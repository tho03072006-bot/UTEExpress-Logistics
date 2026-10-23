package vn.edu.hcmute.uteexpress.tracking;

import java.time.*;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.manager.OperationsCaseRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsCase;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService;
import vn.edu.hcmute.uteexpress.service.routing.*;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bước 17 (B9): đơn liên tỉnh SGN -> HAN giao thất bại ở HAN, Manager HAN lập hoàn hàng, hàng đi chuyến ngược HAN -> SGN,
 * kho SGN ghi nhận trả người gửi -> RETURNED. Mỗi bước chỉ đúng kho mới thao tác được. H2, không ghi Aiven.
 */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:interprovincial-return-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-interprovincial-return-uploads"
})
@ActiveProfiles("isolated-logistics") @Transactional
@Import(InterprovincialReturnIntegrationTest.TestClock.class)
class InterprovincialReturnIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock returnClock() { return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired BranchRepository branches;
    @Autowired OrderRouteRepository routes;
    @Autowired LinehaulTripRepository trips;
    @Autowired jakarta.persistence.EntityManager em;
    @Autowired OrderRepository orders;
    @Autowired OrderService orderService;
    @Autowired OperationsCaseService cases;
    @Autowired HubService hub;
    @Autowired OrderRoutingService routing;
    @Autowired ProvinceAreaRepository areas;
    @Autowired ShipperZoneRepository zones;
    @Autowired vn.edu.hcmute.uteexpress.service.tracking.DispatchService dispatch;
    @Autowired vn.edu.hcmute.uteexpress.service.tracking.TrackingService tracking;
    @Autowired vn.edu.hcmute.uteexpress.service.tracking.ShipmentQrService qr;
    AppUser customer, admin, managerSgn, managerHan, shipperHan;
    Branch sgn, han;
    MockMvc mvc;

    @BeforeEach void seed() {
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context).apply(springSecurity()).build();
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        sgn = branches.findByCode("SGN").orElseThrow(); han = branches.findByCode("HAN").orElseThrow();
        customer = account("return-customer", AppUser.Role.USER);
        admin = account("return-admin", AppUser.Role.ADMIN); internal(admin);
        managerSgn = account("return-manager-sgn", AppUser.Role.MANAGER); internal(managerSgn); staffBranches.save(new StaffBranch(managerSgn, sgn, admin, NOW));
        managerHan = account("return-manager-han", AppUser.Role.MANAGER); internal(managerHan); staffBranches.save(new StaffBranch(managerHan, han, admin, NOW));
        shipperHan = account("return-shipper-han", AppUser.Role.SHIPPER); staffBranches.save(new StaffBranch(shipperHan, han, admin, NOW));
    }

    @Test void failedInterprovincialOrderTravelsBackOnReverseTripAndOriginHubCompletesReturn() throws Exception {
        var order = failedAfterForwardTrip();
        Long caseId = cases.request(managerHan.getUsername(), new OperationsCaseRequest(OperationsCase.Kind.RETURN, order.getId(),
                order.getRevision(), null, null, "Người nhận từ chối, hoàn về người gửi", "", UUID.randomUUID().toString()), null);
        assertEquals(OperationsCase.Status.APPROVED, cases.view(managerHan.getUsername(), caseId).getStatus(), "Hoàn hàng lập là có hiệu lực");
        assertReturnPage(managerSgn, caseId, false, false);
        assertReturnPage(managerHan, caseId, true, false);
        assertReturnPage(admin, caseId, true, false);

        // Bắt đầu hoàn chỉ do kho chi nhánh nhận (HAN).
        var record = cases.view(managerHan.getUsername(), caseId);
        mvc.perform(post("/manager/ho-so-van-hanh/" + caseId + "/bat-dau-hoan")
                .with(authentication(auth(managerSgn))).with(csrf())
                .param("revision", Long.toString(record.getRevision())).param("reason", "Kiểm thử bắt đầu hoàn sai kho"))
                .andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> cases.startReturn(managerSgn.getUsername(), caseId, record.getRevision(), "Kho SGN thử bắt đầu hoàn"));
        cases.startReturn(managerHan.getUsername(), caseId, record.getRevision(), "Hàng về kho Hà Nội chờ chuyến về");
        var route = routes.findByOrder_Id(order.getId()).orElseThrow();
        assertTrue(route.isReturnLeg()); assertEquals(OrderRoute.Stage.DEST_HUB, route.getStage());
        assertNull(orders.findById(order.getId()).orElseThrow().getShipper(), "Shipper giao hết giữ đơn khi hàng về kho");
        assertEquals("Hàng hoàn tại kho " + han.getName() + ", chờ chuyến về " + sgn.getName(), routing.summary(order).orElseThrow().location());
        assertReturnPage(managerSgn, caseId, false, false);
        assertReturnPage(managerHan, caseId, false, false);
        assertReturnPage(admin, caseId, false, false);

        // Chưa về kho gửi thì không được hoàn tất.
        var started = cases.view(managerSgn.getUsername(), caseId);
        assertThrows(StaffConflictException.class, () -> cases.complete(managerSgn.getUsername(), caseId, started.getRevision(), "Trần Minh Thọ", image()));

        // Kho HAN thấy hàng hoàn trong danh sách chờ xếp chuyến về SGN; kho SGN không thấy.
        var hanHub = hub.overview(managerHan.getUsername(), null);
        assertEquals(List.of(order.getId()), hanHub.waitingFor(sgn.getId()).stream().map(r -> r.getOrder().getId()).toList());
        assertEquals(0, hanHub.waitingDelivery(), "Hàng hoàn không tính là đơn chờ phân công giao");
        assertTrue(hub.overview(managerSgn.getUsername(), null).waiting().stream().noneMatch(r -> r.getOrder().getId().equals(order.getId())));
        assertThrows(IllegalArgumentException.class, () -> hub.createTrip(managerSgn.getUsername(), null, han.getId(), "51C-111.11",
                "Tài Xế Sai Kho", "0912345670", List.of(order.getId())), "Kho SGN không xếp được hàng đang nằm ở kho HAN");

        var trip = hub.createTrip(managerHan.getUsername(), null, sgn.getId(), "29C-222.22", "Lê Văn Tài Xế", "0912345671", List.of(order.getId()));
        em.flush(); // như trang web: form đọc phiên bản chuyến sau khi đã ghi
        hub.depart(managerHan.getUsername(), trip.getId(), trip.getRevision(), "NP-HOAN-01");
        assertEquals(OrderRoute.Stage.LINEHAUL, routes.findByOrder_Id(order.getId()).orElseThrow().getStage());
        assertReturnPage(managerSgn, caseId, false, false);
        em.flush();
        long departed = trips.findById(trip.getId()).orElseThrow().getRevision();
        assertThrows(AccessDeniedException.class, () -> hub.arrive(managerHan.getUsername(), trip.getId(), departed, "NP-HOAN-01",
                List.of(order.getId()), ""), "Kho HAN không tự nhận chuyến về SGN");
        hub.arrive(managerSgn.getUsername(), trip.getId(), departed, "NP-HOAN-01", List.of(order.getId()), "");
        route = routes.findByOrder_Id(order.getId()).orElseThrow();
        assertEquals(OrderRoute.Stage.ORIGIN_HUB, route.getStage());
        assertEquals(List.of(order.getId()), hub.overview(managerSgn.getUsername(), null).returned().stream().map(r -> r.getOrder().getId()).toList());
        assertTrue(hub.overview(managerSgn.getUsername(), null).waiting().isEmpty(), "Hàng hoàn đã về không quay lại danh sách xếp chuyến");
        assertEquals("Hàng hoàn đã về kho " + sgn.getName() + ", chờ trả người gửi", routing.summary(order).orElseThrow().location());
        assertReturnPage(managerSgn, caseId, false, true);
        assertReturnPage(managerHan, caseId, false, false);
        assertReturnPage(admin, caseId, false, true);

        // Hoàn tất (trả người gửi) chỉ do kho chi nhánh gửi (SGN).
        var arrived = cases.view(managerSgn.getUsername(), caseId);
        mvc.perform(multipart("/manager/ho-so-van-hanh/" + caseId + "/hoan-tat").file(image())
                .with(authentication(auth(managerHan))).with(csrf())
                .param("revision", Long.toString(arrived.getRevision())).param("reference", "Kiểm thử hoàn tất sai kho"))
                .andExpect(status().isForbidden());
        assertThrows(AccessDeniedException.class, () -> cases.complete(managerHan.getUsername(), caseId, arrived.getRevision(), "Trần Minh Thọ", image()));
        cases.complete(managerSgn.getUsername(), caseId, arrived.getRevision(), "Trần Minh Thọ nhận lại hàng", image());
        assertEquals(Order.OrderStatus.RETURNED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertReturnPage(managerSgn, caseId, false, false);
        assertReturnPage(admin, caseId, false, false);
        var labels = orderService.buildTimeline(orders.findById(order.getId()).orElseThrow()).stream().map(s -> s.getLabel()).toList();
        assertTrue(labels.containsAll(List.of("Giao thất bại", "Hàng hoàn về kho " + han.getName(), "Hàng hoàn đang trung chuyển",
                "Hàng hoàn về kho " + sgn.getName(), "Đã hoàn trả")), labels.toString());
        // Đơn đã hoàn trả đi qua mọi mốc: không mốc nào được hiện như chưa xảy ra.
        assertAllStepsDone(orders.findById(order.getId()).orElseThrow());
    }

    @Test void failedInterprovincialOrderShowsEveryForwardMilestoneAsDone() {
        var order = failedAtDestination();
        var steps = orderService.buildTimeline(orders.findById(order.getId()).orElseThrow());
        assertEquals(List.of("Chờ lấy hàng", "Đã lấy hàng", "Tại kho gửi", "Đang trung chuyển", "Tới kho nhận", "Đang giao", "Giao thất bại"),
                steps.stream().map(s -> s.getLabel()).toList());
        assertAllStepsDone(orders.findById(order.getId()).orElseThrow());
        assertTrue(steps.get(steps.size() - 1).isCurrent());
    }

    private void assertAllStepsDone(Order order) {
        var steps = orderService.buildTimeline(order);
        var pending = steps.stream().filter(s -> !s.isDone()).map(s -> s.getLabel()).toList();
        assertTrue(pending.isEmpty(), "Mốc hiện như chưa xảy ra: " + pending);
    }

    @Test void intraProvinceReturnKeepsTheOldFlow() {
        var order = orderService.createOrder(request("SGN-THUDUC", "79", "SGN-Q1"), customer.getUsername());
        var shipperSgn = account("return-shipper-sgn", AppUser.Role.SHIPPER); staffBranches.save(new StaffBranch(shipperSgn, sgn, admin, NOW));
        var route = routes.findByOrder_Id(order.getId()).orElseThrow(); route.assignShipper(shipperSgn); route.startLastMile();
        order.setShipper(shipperSgn); order.setStatus(Order.OrderStatus.FAILED); orders.saveAndFlush(order);
        Long caseId = cases.request(managerSgn.getUsername(), new OperationsCaseRequest(OperationsCase.Kind.RETURN, order.getId(),
                order.getRevision(), null, null, "Người nhận từ chối, hoàn về người gửi", "", UUID.randomUUID().toString()), null);
        var record = cases.view(managerSgn.getUsername(), caseId);
        cases.startReturn(managerSgn.getUsername(), caseId, record.getRevision(), "Bắt đầu hoàn nội tỉnh");
        assertFalse(routes.findByOrder_Id(order.getId()).orElseThrow().isReturnLeg());
        var started = cases.view(managerSgn.getUsername(), caseId);
        cases.complete(managerSgn.getUsername(), caseId, started.getRevision(), "Trần Minh Thọ nhận lại hàng", image());
        assertEquals(Order.OrderStatus.RETURNED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertAllStepsDone(orders.findById(order.getId()).orElseThrow());
    }

    @ParameterizedTest
    @EnumSource(value = OperationsCase.Kind.class, names = {"RETRY", "RETURN"})
    void originHubCannotOpenDeliveryCaseForFailedInterprovincialOrder(OperationsCase.Kind kind) {
        var order = failedAtDestination();
        assertThrows(AccessDeniedException.class, () -> cases.prepare(managerSgn.getUsername(), kind, order.getId()));
        var request = new OperationsCaseRequest(kind, order.getId(), order.getRevision(), null,
                kind == OperationsCase.Kind.RETRY ? NOW.plusHours(1) : null,
                "Kiểm thử lập hồ sơ sai chi nhánh giao", "", UUID.randomUUID().toString());
        assertThrows(AccessDeniedException.class, () -> cases.request(managerSgn.getUsername(), request, null));
        assertDoesNotThrow(() -> cases.prepare(managerHan.getUsername(), kind, order.getId()));
        Long caseId = cases.request(managerHan.getUsername(), request, null);
        assertEquals(OperationsCase.Status.APPROVED, cases.view(managerHan.getUsername(), caseId).getStatus());
    }

    private UsernamePasswordAuthenticationToken auth(AppUser actor) {
        var principal = new StaffPrincipal(staff.findByUserUsername(actor.getUsername()).orElseThrow());
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }

    private void assertReturnPage(AppUser actor, Long caseId, boolean canStart, boolean canComplete) throws Exception {
        var result = mvc.perform(get("/manager/ho-so-van-hanh/" + caseId).with(authentication(auth(actor))))
                .andExpect(status().isOk()).andExpect(model().attribute("canStartReturn", canStart))
                .andExpect(model().attribute("canComplete", canComplete)).andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(canStart, html.contains("/manager/ho-so-van-hanh/" + caseId + "/bat-dau-hoan"));
        assertEquals(canComplete, html.contains("/manager/ho-so-van-hanh/" + caseId + "/hoan-tat"));
    }

    /** Đơn SGN -> HAN đã qua chuyến, đang ở chặng giao cuối do shipper HAN giữ và vừa giao thất bại. */
    private Order failedAtDestination() {
        var order = orderService.createOrder(request("SGN-THUDUC", "01", "HAN-CAUGIAY"), customer.getUsername());
        var route = routes.findByOrder_Id(order.getId()).orElseThrow();
        route.receiveAtOriginHub(); route.departLinehaul(); route.arriveFromLinehaul(); route.assignShipper(shipperHan);
        order.setShipper(shipperHan); order.setStatus(Order.OrderStatus.FAILED); orders.saveAndFlush(order); routes.saveAndFlush(route);
        assertEquals(OrderRoute.Stage.LAST_MILE, route.getStage());
        return order;
    }
    /** Đi toàn bộ chiều đi qua service thực, không đặt trực tiếp trạng thái để bỏ qua quy tắc phân công/kho. */
    private Order failedAfterForwardTrip() {
        var order = orderService.createOrder(request("SGN-THUDUC", "01", "HAN-CAUGIAY"), customer.getUsername());
        var shipperSgn = account("return-shipper-sgn", AppUser.Role.SHIPPER);
        staffBranches.save(new StaffBranch(shipperSgn, sgn, admin, NOW));
        zones.save(new ShipperZone(shipperSgn, areas.findByAreaCode("SGN-THUDUC").orElseThrow(), true));
        zones.save(new ShipperZone(shipperHan, areas.findByAreaCode("HAN-CAUGIAY").orElseThrow(), true));
        dispatch.assign(order.getTrackingCode(), shipperSgn.getId(), 0L, order.getUpdatedAt(),
                "Kiểm thử phân công lấy hàng tại SGN", managerSgn.getUsername());
        tracking.updateStatus(order.getId(), shipperSgn.getUsername(), Order.OrderStatus.PICKED_UP);
        assertThrows(IllegalStateException.class,
                () -> tracking.updateStatus(order.getId(), shipperSgn.getUsername(), Order.OrderStatus.IN_TRANSIT),
                "Shipper SGN không được tự đi giao liên tỉnh");
        tracking.handoverToHub(order.getId(), shipperSgn.getUsername());
        assertEquals(0, dispatch.load(shipperSgn.getId()), "Bàn giao kho xong thì giải phóng tải cho shipper lấy");
        assertTrue(tracking.findAssignedOrder(order.getId(), shipperSgn.getUsername()).isEmpty());
        var trip = hub.createTrip(managerSgn.getUsername(), null, han.getId(), "51C-123.45",
                "Tài xế kiểm thử chiều đi", "0901234567", List.of(order.getId()));
        em.flush();
        hub.depart(managerSgn.getUsername(), trip.getId(), trip.getRevision(), "NP-KIEM-THU-DI");
        em.flush();
        hub.arrive(managerHan.getUsername(), trip.getId(), trip.getRevision(), "NP-KIEM-THU-DI", List.of(order.getId()), "");
        assertThrows(IllegalArgumentException.class, () -> dispatch.assign(order.getTrackingCode(), shipperSgn.getId(), 0L,
                order.getUpdatedAt(), "Kiểm thử gán shipper SGN tại HAN", managerHan.getUsername()));
        dispatch.assign(order.getTrackingCode(), shipperHan.getId(), 0L, order.getUpdatedAt(),
                "Kiểm thử phân công giao hàng tại HAN", managerHan.getUsername());
        tracking.updateStatus(order.getId(), shipperHan.getUsername(), Order.OrderStatus.IN_TRANSIT);
        tracking.updateStatus(order.getId(), shipperHan.getUsername(), Order.OrderStatus.FAILED,
                vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason.REFUSED,
                "Kiểm thử khách từ chối nhận, chuẩn bị hoàn về SGN", null);
        em.flush();
        var route = routes.findByOrder_Id(order.getId()).orElseThrow();
        assertEquals(shipperSgn.getId(), route.getPickupShipper().getId());
        assertEquals(shipperHan.getId(), route.getDeliveryShipper().getId());
        assertEquals(Order.OrderStatus.FAILED, order.getStatus());
        return order;
    }
    private OrderCreateRequest request(String pickupArea, String deliveryProvince, String deliveryArea) {
        var request = new OrderCreateRequest();
        request.setPickupProvinceCode("79"); request.setPickupAreaCode(pickupArea);
        request.setDeliveryProvinceCode(deliveryProvince); request.setDeliveryAreaCode(deliveryArea);
        request.setSenderName("Trần Minh Thọ"); request.setSenderAddress("Số 1 Võ Văn Ngân");
        request.setReceiverName("Người nhận kiểm thử"); request.setReceiverPhone("0901234567"); request.setReceiverAddress("Số 2 Cầu Giấy");
        request.setWeightKg(1.0); request.setServiceType(Order.ServiceType.STANDARD); request.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        return request;
    }
    private MockMultipartFile image() { return new MockMultipartFile("evidence", "receipt.png", "image/png", qr.generate("UTERETURN01")); }
    private AppUser account(String username, AppUser.Role role) {
        var value = new AppUser(username, "test-only-hash", username + "@test.invalid");
        value.setEnabled(true); value.setRole(role); value.setFullName("Tài khoản kiểm thử");
        return users.saveAndFlush(value);
    }
    private StaffAccount internal(AppUser user) {
        var value = new StaffAccount(); value.setUser(user); value.setIssuedBy("test-only"); value.setActive(true); value.setPasswordChangeRequired(false);
        return staff.saveAndFlush(value);
    }
}
