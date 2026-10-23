package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsPeriod;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperWorkView;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** SQL thật trên H2 riêng, không tạo hoặc sửa dữ liệu demo trên Aiven. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:logistics-test;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload.dir=target/test-proof-uploads"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("isolated-logistics")
@Import(LogisticsIntegrationTest.TestClock.class)
@Transactional
class LogisticsIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 11, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired DeliveryProofRepository proofs;
    @Autowired OrderComplaintRepository complaints;
    @Autowired ShipperNotificationRepository notifications;
    @Autowired StaffAccountRepository staff;
    @Autowired TrackingService tracking;
    @Autowired OrderComplaintService complaintService;
    @Autowired DispatchService dispatch;
    @Autowired ShipmentQrService qr;
    @Autowired OrderJourneyService journey;
    @Autowired OrderService orderService;
    @Autowired ShipperWorkspaceService workspace;
    @Autowired ShipperStatisticsService statistics;
    @Autowired OrderPaymentRepository payments;
    @Autowired DeliveryProofService proofService;
    @Autowired vn.edu.hcmute.uteexpress.service.media.CloudMediaStorageService cloud;
    @Autowired vn.edu.hcmute.uteexpress.service.ServiceReviewService reviewService;
    @Autowired ServiceReviewRepository reviews;
    @Autowired PasswordEncoder passwordEncoder;
    AppUser customer, anotherCustomer, shipper, anotherShipper;
    Order order;
    Authentication managerAuth;

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        customer = userAccount("customer-test", AppUser.Role.USER);
        anotherCustomer = userAccount("another-customer", AppUser.Role.USER);
        shipper = userAccount("shipper-test", AppUser.Role.SHIPPER);
        anotherShipper = userAccount("another-shipper", AppUser.Role.SHIPPER);
        var manager = userAccount("manager-test", AppUser.Role.MANAGER);
        var account = new StaffAccount(); account.setUser(manager); account.setActive(true); account.setPasswordChangeRequired(false); account.setIssuedBy("test-only");
        staff.saveAndFlush(account);
        var principal = new StaffPrincipal(account);
        managerAuth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
        order = order("UTE-TEST-01", shipper);
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.assignStaff(context, manager, manager, shipper, anotherShipper);
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.route(context, order);
        payment(order, OrderPayment.PaymentMethod.COD, OrderPayment.PaymentStatus.UNPAID, 18000, null);
    }

    @Test void dashboardPaginatesOnlyAssignedOrdersAndPreservesFilters() throws Exception {
        for (int i = 0; i < 10; i++) order("UTE-MINE-" + i, shipper);
        order("UTE-OTHER", anotherShipper);
        mvc.perform(get("/shipper/trang-chu").with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("11 đơn phù hợp")))
            .andExpect(content().string(not(containsString("UTE-OTHER"))))
            .andExpect(content().string(containsString("Trang sau")));
        var page = tracking.searchAssignedOrders(shipper.getUsername(), " Mai ", Order.OrderStatus.PENDING_PICKUP,
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9), 1);
        assertEquals(11, page.getTotalElements()); assertEquals(1, page.getContent().size());
        assertEquals(10, page.getSize());
        assertTrue(page.getContent().stream().allMatch(o -> o.getShipper().getId().equals(shipper.getId())));
    }

    @Test void shipperKeepsWorkNavigationOnPublicPagesWhileGuestsSeePublicLayout() throws Exception {
        for (String page : List.of("/", "/tra-cuu", "/uoc-tinh-cuoc")) {
            mvc.perform(get(page).with(user(shipper.getUsername()).roles("SHIPPER")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("class=\"ute-shell ute-shell-with-sidebar\"")))
                    .andExpect(content().string(containsString("class=\"ute-brand\" href=\"/shipper/trang-chu\"")))
                    .andExpect(content().string(containsString("href=\"/shipper/trang-chu\">Trang chủ</a>")))
                    .andExpect(content().string(containsString("href=\"/shipper/thong-ke\"")))
                    .andExpect(content().string(containsString("href=\"/shipper/quet-ma\"")))
                    .andExpect(content().string(containsString("id=\"uteSidebarMobile\"")));
            mvc.perform(get(page))
                    .andExpect(status().isOk())
                    .andExpect(content().string(not(containsString("class=\"ute-shell ute-shell-with-sidebar\""))))
                    .andExpect(content().string(containsString("class=\"ute-brand\" href=\"/\"")))
                    .andExpect(content().string(not(containsString("href=\"/shipper/trang-chu\""))));
        }
    }

    @Test void expiredNotificationPollReturns401AndLoginOpensShipperDashboard() throws Exception {
        var session = new MockHttpSession();
        mvc.perform(get("/shipper/thong-bao/chua-doc").session(session).accept(MediaType.ALL))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
        assertNull(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
        enableLogin(shipper);
        mvc.perform(post("/dang-nhap").with(csrf()).session(session).param("username", shipper.getUsername())
                .param("password", "LoginTestOnly@2026"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/shipper/trang-chu"));
    }

    @Test void backgroundRequestsCannotReplaceTheRequestedOrderAfterLogin() throws Exception {
        var session = new MockHttpSession();
        String detail = "/shipper/don/" + order.getId();
        mvc.perform(get(detail).session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        var saved = session.getAttribute("SPRING_SECURITY_SAVED_REQUEST");
        assertNotNull(saved);
        for (String background : List.of("/shipper/thong-bao/chua-doc", "/ws-tracking/info")) {
            mvc.perform(get(background).session(session).accept(MediaType.ALL))
                    .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
            assertSame(saved, session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
        }
        enableLogin(shipper);
        mvc.perform(post("/dang-nhap").with(csrf()).session(session).param("username", shipper.getUsername())
                .param("password", "LoginTestOnly@2026"))
                .andExpect(redirectedUrlPattern("**" + detail + "?continue"));
    }

    @Test void oldSavedNotificationApiIsDiscardedEvenWhenItRequestedHtml() throws Exception {
        var session = new MockHttpSession();
        var oldRequest = new MockHttpServletRequest("GET", "/shipper/thong-bao/chua-doc");
        oldRequest.setSession(session);
        oldRequest.addHeader("Accept", "text/html");
        new HttpSessionRequestCache().saveRequest(oldRequest, new MockHttpServletResponse());
        assertNotNull(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
        enableLogin(shipper);
        mvc.perform(post("/dang-nhap").with(csrf()).session(session).param("username", shipper.getUsername())
                .param("password", "LoginTestOnly@2026"))
                .andExpect(redirectedUrl("/shipper/trang-chu"));
        assertNull(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
    }

    @Test void jsonAndAjaxRequestsDoNotBecomeLoginDestinations() throws Exception {
        var session = new MockHttpSession();
        String detail = "/shipper/don/" + order.getId();
        mvc.perform(get(detail).session(session).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(detail).session(session).accept(MediaType.TEXT_HTML)
                .header("X-Requested-With", "XMLHttpRequest"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(detail).session(session).accept(MediaType.TEXT_HTML).header("Sec-Fetch-Dest", "empty"))
                .andExpect(status().isUnauthorized());
        assertNull(session.getAttribute("SPRING_SECURITY_SAVED_REQUEST"));
    }

    @Test void directLoginStillOpensCustomerDashboard() throws Exception {
        enableLogin(customer);
        mvc.perform(post("/dang-nhap").with(csrf()).param("username", customer.getUsername()).param("password", "LoginTestOnly@2026"))
                .andExpect(redirectedUrl("/nguoi-dung/trang-chu"));
    }

    @Test void customerHtmlPageStillOpensAfterLogin() throws Exception {
        var session = new MockHttpSession();
        String detail = "/nguoi-dung/don-hang/" + order.getId();
        mvc.perform(get(detail).session(session).accept(MediaType.TEXT_HTML))
                .andExpect(status().is3xxRedirection());
        enableLogin(customer);
        mvc.perform(post("/dang-nhap").with(csrf()).session(session).param("username", customer.getUsername())
                .param("password", "LoginTestOnly@2026"))
                .andExpect(redirectedUrlPattern("**" + detail + "?continue"));
    }

    private void enableLogin(AppUser account) {
        account.setPassword(passwordEncoder.encode("LoginTestOnly@2026"));
        users.saveAndFlush(account);
    }

    @Test void creatingAndCancellingAnOrderRecordsItsRealJourney() {
        var request = new OrderCreateRequest();
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.fill(request);
        request.setSenderName("Người gửi thử"); request.setSenderAddress("Địa chỉ gửi thử");
        request.setReceiverName("Người nhận thử"); request.setReceiverPhone("0901234567");
        request.setReceiverAddress("Địa chỉ nhận thử"); request.setWeightKg(1.0);
        request.setServiceType(Order.ServiceType.STANDARD); request.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        var created = orderService.createOrder(request, customer.getUsername());
        assertEquals(NOW, created.getCreatedAt());
        var initial = journey.history(created.getId(), customer.getUsername(), 0);
        assertEquals(1, initial.getTotalElements());
        assertEquals(Order.OrderStatus.PENDING_PICKUP, initial.getContent().get(0).getStatus());
        orderService.cancelOrder(created.getId(), customer.getUsername());
        var cancelled = journey.history(created.getId(), customer.getUsername(), 0);
        assertEquals(2, cancelled.getTotalElements());
        assertEquals(Order.OrderStatus.CANCELLED, cancelled.getContent().get(0).getStatus());
        assertEquals(customer.getId(), cancelled.getContent().get(0).getActor().getId());
    }

    @Test void searchTreatsSqlWildcardsAsLiteralAndIncludesEndOfDay() {
        order.setReceiverName("Mai %_!"); order.setCreatedAt(LocalDateTime.of(2026, 10, 9, 23, 59, 59)); orders.saveAndFlush(order);
        order("UTE-UNRELATED", shipper).setReceiverName("Someone else"); orders.flush();
        var result = tracking.searchAssignedOrders(shipper.getUsername(), "%_!", null,
                LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9), 0);
        assertEquals(1, result.getTotalElements()); assertEquals(order.getId(), result.getContent().get(0).getId());
        assertThrows(IllegalArgumentException.class, () -> tracking.searchAssignedOrders(shipper.getUsername(), "", null,
                LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 9), 0));
    }

    @Test void detailRendersQrHistoryAndCsrfToken() throws Exception {
        mvc.perform(get("/shipper/don/" + order.getId()).with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Lịch sử hành trình")))
            .andExpect(content().string(containsString("QR vận đơn")))
            .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test void csrfAndRolePreventStatusChanges() throws Exception {
        String path = "/shipper/don/" + order.getId() + "/trang-thai";
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).param("status", "PICKED_UP"))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).servletPath(path).with(user(customer.getUsername()).roles("USER")).with(csrf()).param("status", "PICKED_UP"))
            .andExpect(status().isForbidden());
        assertEquals(0, history.count()); assertEquals(Order.OrderStatus.PENDING_PICKUP, order.getStatus());
    }

    @Test void statusChangePersistsHistoryWithActor() throws Exception {
        String path = "/shipper/don/" + order.getId() + "/trang-thai";
        mvc.perform(post(path).servletPath(path).with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf())
                .param("status", "PICKED_UP")).andExpect(status().is3xxRedirection());
        var entry = journey.history(order.getId(), customer.getUsername(), 0).getContent().get(0);
        assertEquals(Order.OrderStatus.PENDING_PICKUP, entry.getPreviousStatus());
        assertEquals(Order.OrderStatus.PICKED_UP, entry.getStatus());
        assertEquals(shipper.getId(), entry.getActor().getId()); assertEquals(NOW, entry.getOccurredAt());
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/hanh-trinh").with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Đã lấy hàng")));
    }

    @Test void failureRequiresReasonAndValidRetryScheduleBeforeChangingState() {
        order.setStatus(Order.OrderStatus.IN_TRANSIT); orders.flush();
        assertThrows(IllegalArgumentException.class, () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.FAILED));
        assertEquals(Order.OrderStatus.IN_TRANSIT, order.getStatus()); assertEquals(0, history.count());
        assertThrows(IllegalArgumentException.class, () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.FAILED,
                DeliveryFailureReason.OTHER, "", null));
        assertThrows(IllegalArgumentException.class, () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.FAILED,
                DeliveryFailureReason.CUSTOMER_ABSENT, "", NOW.minusMinutes(1)));
    }

    @Test void retryNeedsNewProofAndRecordsFailureSchedule() {
        order.setStatus(Order.OrderStatus.IN_TRANSIT); orders.flush();
        var proof = new DeliveryProof(); proof.setOrder(order); proof.setShipper(shipper);
        proof.setProofImagePath("cld1:test-cloud:PROOF:0123456789abcdef0123456789abcdef:png");
        proof.setSignaturePath("cld1:test-cloud:SIGNATURE:0123456789abcdef0123456789abcdef:png"); proof.setUpdatedAt(NOW.minusHours(1));
        proofs.saveAndFlush(proof);
        tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.FAILED,
                DeliveryFailureReason.CUSTOMER_ABSENT, "Khách hẹn buổi chiều", NOW.plusHours(5));
        var failure = history.findAll().get(0);
        assertEquals(DeliveryFailureReason.CUSTOMER_ABSENT, failure.getFailureReason());
        assertEquals(NOW.plusHours(5), failure.getNextAttemptAt());
        assertThrows(IllegalStateException.class, () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.IN_TRANSIT));
        history.saveAndFlush(new OrderStatusHistory(order, shipper, Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.FAILED,
                DeliveryFailureReason.CUSTOMER_ABSENT, "Cập nhật lịch đã đến hẹn", NOW.minusSeconds(1), NOW.plusSeconds(1)));
        tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.IN_TRANSIT);
        assertFalse(tracking.hasCurrentProof(order));
        assertThrows(IllegalStateException.class, () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.DELIVERED));
        proof.setUpdatedAt(NOW.plusSeconds(1)); proofs.saveAndFlush(proof);
        tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.DELIVERED);
        assertEquals(Order.OrderStatus.DELIVERED, order.getStatus()); assertEquals(4, history.count());
    }

    @Test void retryLimitAndForeignAssignmentsCannotBeBypassed() {
        order.setStatus(Order.OrderStatus.FAILED); orders.flush();
        for (int i = 0; i < 3; i++) history.save(new OrderStatusHistory(order, shipper, Order.OrderStatus.IN_TRANSIT,
                Order.OrderStatus.FAILED, DeliveryFailureReason.CUSTOMER_ABSENT, "Khách không có mặt", null, NOW.minusHours(3-i)));
        history.flush();
        assertFalse(tracking.canRetry(order));
        assertThrows(IllegalStateException.class, () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.IN_TRANSIT));
        assertThrows(IllegalStateException.class, () -> tracking.updateStatus(order.getId(), anotherShipper.getUsername(), Order.OrderStatus.RETURNED));
        assertEquals(Order.OrderStatus.FAILED, order.getStatus());
    }

    @Test void qrRoundTripOpensOnlyAssignedOrder() throws Exception {
        mvc.perform(get("/shipper/quet-ma").with(user(shipper.getUsername()).roles("SHIPPER"))).andExpect(status().isOk());
        byte[] bytes = qr.generate(order.getTrackingCode());
        var image = new MockMultipartFile("qrImage", "qr.png", "image/png", bytes);
        assertEquals(order.getTrackingCode(), qr.decode(image));
        mvc.perform(multipart("/shipper/quet-ma").file(image).with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf()))
            .andExpect(redirectedUrl("/shipper/don/" + order.getId()));
        mvc.perform(multipart("/shipper/quet-ma").file(image).with(user(anotherShipper.getUsername()).roles("SHIPPER")).with(csrf()))
            .andExpect(redirectedUrl("/shipper/quet-ma")).andExpect(flash().attributeExists("error"));
        assertThrows(IllegalArgumentException.class, () -> qr.decode(new MockMultipartFile("qrImage", "fake.png", "image/png", "fake".getBytes())));
    }

    @Test void foreignUserCannotReadQrOrHistory() throws Exception {
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/qr.png").with(user(anotherCustomer.getUsername()).roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/hanh-trinh").with(user(anotherCustomer.getUsername()).roles("USER")))
            .andExpect(status().isForbidden());
    }

    @Test void dispatchCreatesNotificationAndRejectsStaleAssignment() throws Exception {
        order.setShipper(null); orders.saveAndFlush(order);
        LocalDateTime expected = order.getUpdatedAt();
        mvc.perform(get("/manager/phan-cong").with(authentication(managerAuth))).andExpect(status().isOk())
            .andExpect(content().string(containsString("Phân công giao hàng")));
        dispatch.assign(order.getTrackingCode(), shipper.getId(), 0, expected, "Phân công theo khu vực", "manager-test");
        assertEquals(shipper.getId(), order.getShipper().getId());
        assertEquals(1, notifications.countByRecipientAndReadAtIsNull(shipper));
        assertEquals(1, history.count());
        assertThrows(IllegalArgumentException.class, () -> dispatch.assign(order.getTrackingCode(), anotherShipper.getId(), 0,
                expected, "Phân công theo khu vực", "manager-test"));
        assertEquals(1, notifications.count());
        mvc.perform(get("/shipper/thong-bao").with(user(shipper.getUsername()).roles("SHIPPER"))).andExpect(status().isOk())
            .andExpect(content().string(containsString("1 thông báo chưa đọc")));
        var id = notifications.findAll().get(0).getId();
        mvc.perform(post("/shipper/thong-bao/" + id + "/da-doc").with(user(anotherShipper.getUsername()).roles("SHIPPER")).with(csrf()))
            .andExpect(status().isBadRequest());
        assertEquals(1, notifications.countByRecipientAndReadAtIsNull(shipper));
        mvc.perform(post("/shipper/thong-bao/" + id + "/da-doc").with(user(shipper.getUsername()).roles("SHIPPER")).with(csrf()))
            .andExpect(status().is3xxRedirection());
        assertEquals(0, notifications.countByRecipientAndReadAtIsNull(shipper));
    }

    @Test void nonIssuedManagerCannotOpenNewInternalFunctions() throws Exception {
        for (String path : List.of("/manager/phan-cong", "/manager/khieu-nai"))
            mvc.perform(get(path).with(user("fake-manager").roles("MANAGER"))).andExpect(status().isForbidden());
    }

    @Test void complaintImageIsPrivateAndAllRolesSeeCorrectActions() throws Exception {
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId()).with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Khiếu nại</a>")));
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/khieu-nai").with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Gửi khiếu nại")));
        var image = new MockMultipartFile("image", "evidence.png", "image/png", qr.generate(order.getTrackingCode()));
        complaintService.create(order.getId(), customer.getUsername(), "Giao hàng chậm", "Đề nghị kiểm tra tiến độ giao hàng", image);
        var complaint = complaints.findByOrder(order).orElseThrow();
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/khieu-nai").with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Giao hàng chậm")))
            .andExpect(content().string(not(containsString("Trả lời khách hàng"))))
            .andExpect(content().string(containsString("Nhắn thêm cho UTEExpress")));
        mvc.perform(get("/nguoi-dung/khieu-nai/" + complaint.getId() + "/anh").with(user(anotherCustomer.getUsername()).roles("USER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/shipper/khieu-nai/" + complaint.getId() + "/anh").with(user(anotherShipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/nguoi-dung/khieu-nai/" + complaint.getId() + "/anh").with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/manager/khieu-nai").with(authentication(managerAuth))).andExpect(status().isOk());
        mvc.perform(get("/manager/khieu-nai/" + complaint.getId()).with(authentication(managerAuth)))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Trả lời khách hàng")));
        mvc.perform(get("/shipper/khieu-nai/" + complaint.getId()).with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Bổ sung thông tin giao hàng")));
    }

    @Test void complaintRevisionAndClosedStatusProtectReplies() {
        complaintService.create(order.getId(), customer.getUsername(), "Giao hàng chậm", "Đề nghị kiểm tra tiến độ giao hàng", null);
        var complaint = complaints.findByOrder(order).orElseThrow(); long original = complaint.getRevision();
        complaintService.supplement(complaint.getId(), shipper.getUsername(), original, "Đã liên hệ người nhận và hẹn lại.");
        assertThrows(IllegalArgumentException.class, () -> complaintService.respond(complaint.getId(), "manager-test", original,
                OrderComplaint.Status.RESOLVED, "Đã xử lý theo đề nghị của người gửi."));
        complaintService.respond(complaint.getId(), "manager-test", complaint.getRevision(), OrderComplaint.Status.RESOLVED,
                "Đã xử lý theo đề nghị của người gửi.");
        assertTrue(complaint.getStatus().isClosed());
        assertThrows(IllegalArgumentException.class, () -> complaintService.supplement(complaint.getId(), shipper.getUsername(),
                complaint.getRevision(), "Không được ghi đè hồ sơ đã kết thúc."));
        assertThrows(IllegalArgumentException.class, () -> complaintService.create(order.getId(), customer.getUsername(),
                "Hồ sơ trùng", "Không được tạo hai hồ sơ trên cùng vận đơn", null));
    }

    @Test void customerCanKeepMessagingAndReopenResolvedComplaint() {
        complaintService.create(order.getId(), customer.getUsername(), "X", "Y", null);
        var complaint = complaints.findByOrder(order).orElseThrow();
        complaintService.respond(complaint.getId(), "manager-test", complaint.getRevision(), OrderComplaint.Status.RESOLVED, "Ok");
        assertTrue(complaint.getStatus().isClosed());
        complaintService.customerMessage(complaint.getId(), customer.getUsername(), "Vẫn chưa nhận được hàng");
        complaintService.customerMessage(complaint.getId(), customer.getUsername(), "?");
        assertEquals(OrderComplaint.Status.IN_PROGRESS, complaint.getStatus());
        var thread = complaintService.timeline(complaint.getId(), customer.getUsername(), 0).getContent();
        assertEquals(2, thread.stream().filter(entry -> entry.getAction().equals("CUSTOMER_MESSAGE")).count());
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> complaintService.customerMessage(complaint.getId(), anotherCustomer.getUsername(), "Không phải đơn của tôi"));
        assertThrows(IllegalArgumentException.class, () -> complaintService.customerMessage(complaint.getId(), customer.getUsername(), "   "));
    }

    @Test void workViewsUseLatestRetryAndExcludeForeignOrCompletedOrders() throws Exception {
        order.setStatus(Order.OrderStatus.FAILED);
        failure(order, NOW.minusHours(2), NOW.minusMinutes(1));
        var later = order("UTE-LATER", shipper); later.setStatus(Order.OrderStatus.FAILED);
        failure(later, NOW.minusHours(2), NOW.minusMinutes(1));
        failure(later, NOW.minusHours(1), NOW.plusHours(2));
        var tomorrow = order("UTE-TOMORROW", shipper); tomorrow.setStatus(Order.OrderStatus.FAILED);
        failure(tomorrow, NOW.minusHours(1), NOW.toLocalDate().plusDays(1).atStartOfDay());
        var other = order("UTE-FOREIGN", anotherShipper); other.setStatus(Order.OrderStatus.FAILED);
        failure(other, NOW.minusHours(1), NOW.minusMinutes(1));
        var done = order("UTE-DONE", shipper); done.setStatus(Order.OrderStatus.DELIVERED);
        failure(done, NOW.minusHours(2), NOW.minusMinutes(1));
        var active = order("UTE-ACTIVE", shipper); active.setStatus(Order.OrderStatus.IN_TRANSIT);
        var exhausted = order("UTE-EXHAUSTED", shipper); exhausted.setStatus(Order.OrderStatus.FAILED);
        for (int i = 3; i > 0; i--) failure(exhausted, NOW.minusHours(i), NOW.minusMinutes(1));
        orders.flush(); history.flush();
        var due = workspace.work(shipper.getUsername(), ShipperWorkView.RETRY_DUE, "", null, null, null, 0);
        assertEquals(List.of(order.getId()), due.map(Order::getId).getContent());
        assertEquals(1L, workspace.counts(shipper.getUsername()).get("retryDue"));
        var today = workspace.work(shipper.getUsername(), ShipperWorkView.TODAY, "", null, null, null, 0);
        assertEquals(3, today.getTotalElements());
        assertEquals(order.getId(), today.getContent().get(0).getId());
        assertEquals(active.getId(), today.getContent().get(1).getId());
        assertEquals(1, workspace.work(shipper.getUsername(), ShipperWorkView.TODAY, "ACTIVE", null, null, null, 999).getTotalElements());
        mvc.perform(get("/shipper/trang-chu").param("work", "RETRY_DUE").with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Đã đến hẹn giao lại")))
            .andExpect(content().string(not(containsString("UTE-FOREIGN"))));
    }

    @Test void moneyUsesDiscountedPaymentAndNeverCollectsPrepaidOrOnlineCash() throws Exception {
        payment(order, OrderPayment.PaymentMethod.COD, OrderPayment.PaymentStatus.UNPAID, 12000, null);
        var prepaid = order("UTE-PREPAID", shipper);
        payment(prepaid, OrderPayment.PaymentMethod.MOMO, OrderPayment.PaymentStatus.PAID, 12000, NOW);
        var onlineUnpaid = order("UTE-ONLINE", shipper);
        payment(onlineUnpaid, OrderPayment.PaymentMethod.VNPAY, OrderPayment.PaymentStatus.UNPAID, 12000, null);
        var missing = order("UTE-MISSING", shipper);
        var views = workspace.money(shipper.getUsername(), List.of(order, prepaid, onlineUnpaid, missing));
        assertEquals(new BigDecimal("12000"), views.get(order.getId()).due());
        assertEquals(new BigDecimal("6000"), views.get(order.getId()).discount());
        assertEquals(BigDecimal.ZERO, views.get(prepaid.getId()).due());
        assertEquals(BigDecimal.ZERO, views.get(onlineUnpaid.getId()).due());
        assertFalse(views.get(missing.getId()).available());
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> workspace.money(anotherShipper.getUsername(), List.of(order)));
        mvc.perform(get("/shipper/don/" + order.getId()).with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("12.000 đ")))
            .andExpect(content().string(containsString("6.000 đ")))
            .andExpect(content().string(containsString("data-copy-text")));
    }

    @Test void statisticsUseActualEventsAndPaidDateInsteadOfMutableOrderTimestamp() throws Exception {
        var previousDay = NOW.minusDays(1);
        order.setStatus(Order.OrderStatus.DELIVERED); order.setUpdatedAt(NOW); orders.flush();
        history.saveAndFlush(new OrderStatusHistory(order, shipper, Order.OrderStatus.IN_TRANSIT,
                Order.OrderStatus.DELIVERED, null, "Giao hôm qua", null, previousDay));
        payment(order, OrderPayment.PaymentMethod.COD, OrderPayment.PaymentStatus.PAID, 12000, previousDay);
        var day = statistics.getStatistics(shipper.getUsername(), ShipperStatisticsPeriod.DAY);
        assertEquals(0, day.totalOrders()); assertEquals(BigDecimal.ZERO, day.collectedCod());
        var yesterday = previousDay.toLocalDate();
        var result = statistics.getStatistics(shipper.getUsername(), ShipperStatisticsPeriod.DAY, yesterday, yesterday);
        assertEquals(1, result.deliveredOrders()); assertEquals(0, result.legacyOrders());
        assertEquals(1, result.days().get(0).delivered());
        assertEquals(0, result.days().get(0).collectedCod().compareTo(new BigDecimal("12000")));
        // Biểu đồ theo ngày chỉ hiện khi kỳ có từ 2 ngày trở lên.
        mvc.perform(get("/shipper/thong-ke").param("from", yesterday.minusDays(1).toString()).param("to", yesterday.toString())
                .with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("12.000 đ")))
            .andExpect(content().string(containsString("shipper-chart-day")))
            .andExpect(content().string(containsString("sx-donut")))
            .andExpect(content().string(containsString("Cước COD thu theo ngày")));
        // Kỳ một ngày: biểu đồ theo ngày chỉ có một điểm nên được ẩn, vẫn giữ biểu đồ tròn.
        mvc.perform(get("/shipper/thong-ke").param("from", yesterday.toString()).param("to", yesterday.toString())
                .with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("sx-donut")))
            .andExpect(content().string(not(containsString("data-line-chart"))));
    }

    @Test void retryStatisticsCountEventsButSummaryCountsEachOrderOnceAndPreservesActor() {
        order.setStatus(Order.OrderStatus.DELIVERED); order.setShipper(anotherShipper); orders.flush();
        failure(order, NOW.minusHours(3), NOW.minusHours(2));
        history.save(new OrderStatusHistory(order, shipper, Order.OrderStatus.FAILED, Order.OrderStatus.IN_TRANSIT, null, "Giao lại", null, NOW.minusHours(2)));
        failure(order, NOW.minusHours(1), null);
        history.saveAndFlush(new OrderStatusHistory(order, shipper, Order.OrderStatus.FAILED, Order.OrderStatus.RETURNED, null, "Hoàn trả", null, NOW));
        var result = statistics.getStatistics(shipper.getUsername(), ShipperStatisticsPeriod.DAY);
        assertEquals(1, result.totalOrders()); assertEquals(1, result.failedOrReturnedOrders());
        assertEquals(2, result.days().get(0).failed()); assertEquals(1, result.days().get(0).returned());
        assertEquals(0, statistics.getStatistics(anotherShipper.getUsername(), ShipperStatisticsPeriod.DAY).totalOrders());
    }

    @Test void csvHasVietnameseHeadersAndNoCustomerDataAndChecksRangeAndRole() throws Exception {
        mvc.perform(get("/shipper/thong-ke/xuat.csv").param("from", "2026-10-10").param("to", "2026-10-11")
                .with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(status().isOk()).andExpect(header().string("Content-Disposition", containsString("attachment")))
            .andExpect(content().string(containsString("Ngày,Đã lấy hàng,Giao thành công")))
            .andExpect(content().string(containsString("2026-10-11,0,0,0,0,0")))
            .andExpect(content().string(not(containsString(order.getReceiverPhone()))));
        mvc.perform(get("/shipper/thong-ke/xuat.csv").with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isForbidden());
        assertThrows(IllegalArgumentException.class, () -> statistics.getStatistics(shipper.getUsername(), null, NOW.toLocalDate(), null));
        assertThrows(IllegalArgumentException.class, () -> statistics.getStatistics(shipper.getUsername(), null, NOW.toLocalDate(), NOW.toLocalDate().plusDays(92)));
        // Khoảng ngày ngược: giữ trang thống kê, báo lỗi tại chỗ và giữ lại ngày đã nhập để sửa.
        mvc.perform(get("/shipper/thong-ke").param("from", "2026-10-11").param("to", "2026-10-10")
                .with(user(shipper.getUsername()).roles("SHIPPER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Từ ngày không được sau đến ngày.")))
                .andExpect(content().string(containsString("value=\"2026-10-10\"")))
                .andExpect(content().string(containsString("is-invalid")));
    }

    @Test void cameraCodeSubmissionValidatesCodeCsrfAndAssignment() throws Exception {
        mvc.perform(post("/shipper/quet-ma/ma").param("code", order.getTrackingCode()).with(csrf())
                .with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(redirectedUrl("/shipper/don/" + order.getId()));
        mvc.perform(post("/shipper/quet-ma/ma").param("code", order.getTrackingCode()).with(csrf())
                .with(user(anotherShipper.getUsername()).roles("SHIPPER")))
            .andExpect(redirectedUrl("/shipper/quet-ma")).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/shipper/quet-ma/ma").param("code", "https://fake.invalid/UTE-TEST-01").with(csrf())
                .with(user(shipper.getUsername()).roles("SHIPPER")))
            .andExpect(redirectedUrl("/shipper/quet-ma")).andExpect(flash().attributeExists("error"));
        mvc.perform(post("/shipper/quet-ma/ma").param("code", order.getTrackingCode())
                .with(user(shipper.getUsername()).roles("SHIPPER"))).andExpect(status().isForbidden());
    }

    @Test void cloudProofIsPrivateAndLocalPathsNeverReadFromDisk() throws Exception {
        String filename = "legacy.png";
        byte[] image = qr.generate(order.getTrackingCode());
            var proof = new DeliveryProof(); proof.setOrder(order); proof.setShipper(shipper); proof.setUpdatedAt(NOW);
            proof.setProofImagePath(cloud.storeBytes(vn.edu.hcmute.uteexpress.service.media.MediaPurpose.PROOF,image,"proof.png"));
            proof.setSignaturePath(cloud.storeBytes(vn.edu.hcmute.uteexpress.service.media.MediaPurpose.SIGNATURE,image,"signature.png"));
            proofs.saveAndFlush(proof);
            String shipperPath = "/shipper/don/" + order.getId() + "/bang-chung/anh";
            mvc.perform(get(shipperPath).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk()).andExpect(content().bytes(image)).andExpect(header().string("Cache-Control", "no-store"));
            mvc.perform(get(shipperPath).with(user(anotherShipper.getUsername()).roles("SHIPPER"))).andExpect(status().isForbidden());
            mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/bang-chung/chu-ky").with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().contentType("image/png"));
            mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/bang-chung/anh").with(user(anotherCustomer.getUsername()).roles("USER")))
                .andExpect(status().isForbidden());
            mvc.perform(get("/manager/don-hang/" + order.getId() + "/bang-chung/anh").with(authentication(managerAuth))).andExpect(status().isOk());
            for (String path : List.of("/uploads/giao-hang/" + filename, "/private-proof/" + filename))
                mvc.perform(get(path).with(user(shipper.getUsername()).roles("SHIPPER"))).andExpect(status().isForbidden());
            proof.setProofImagePath("/uploads/giao-hang/../../config/aiven-local.properties"); proofs.flush();
            mvc.perform(get(shipperPath).with(user(shipper.getUsername()).roles("SHIPPER"))).andExpect(status().isNotFound());
            mvc.perform(get("/shipper/don/"+order.getId()).with(user(shipper.getUsername()).roles("SHIPPER")))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("chưa có trên kho cloud chung")))
                    .andExpect(content().string(not(containsString("/bang-chung/anh"))));
            proof.setProofImagePath("cld1:test-cloud:PROOF:0123456789abcdef0123456789abcdef:png"); proofs.flush();
            mvc.perform(get(shipperPath).with(user(shipper.getUsername()).roles("SHIPPER"))).andExpect(status().isNotFound());
    }

    @Test void publicReviewMediaCannotExposePrivatePurposeOrArbitraryPaths() throws Exception {
        order.setStatus(Order.OrderStatus.DELIVERED); orders.saveAndFlush(order);
        var form=new vn.edu.hcmute.uteexpress.dto.ServiceReviewRequest(); form.setRating(5);
        form.setContent("Giao hàng đúng hẹn, nhân viên hỗ trợ nhiệt tình và đóng gói cẩn thận.");
        byte[] image=qr.generate(order.getTrackingCode());
        form.setMediaFile(new MockMultipartFile("mediaFile","review.png","image/png",image));
        var review=reviewService.createReview(order.getId(),form,customer.getUsername()); reviews.flush();
        String path="/tep-danh-gia/"+review.getId();
        assertTrue(review.getMediaPath().startsWith("cld1:test-cloud:REVIEW:"));
        mvc.perform(get(path)).andExpect(status().isOk()).andExpect(content().bytes(image))
                .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get(path).header("Range","bytes=0-7")).andExpect(status().isPartialContent())
                .andExpect(content().bytes(java.util.Arrays.copyOf(image,8)));
        review.setMediaPath(cloud.storeBytes(vn.edu.hcmute.uteexpress.service.media.MediaPurpose.PROOF,image,"private.png")); reviews.flush();
        mvc.perform(get(path)).andExpect(status().isServiceUnavailable());
        review.setMediaPath("https://evil.invalid/private.png"); reviews.flush();
        mvc.perform(get(path)).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/tep-danh-gia/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/tep-danh-gia/9223372036854775807")).andExpect(status().isNotFound());
    }

    private void failure(Order value, LocalDateTime at, LocalDateTime next) {
        history.saveAndFlush(new OrderStatusHistory(value, shipper, Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.FAILED,
                DeliveryFailureReason.CUSTOMER_ABSENT, "Hẹn giao lại", next, at));
    }
    private void payment(Order value, OrderPayment.PaymentMethod method, OrderPayment.PaymentStatus status, int amount, LocalDateTime paidAt) {
        var payment = payments.findByOrder(value).orElseGet(OrderPayment::new); payment.setOrder(value); payment.setMethod(method); payment.setStatus(status);
        payment.setAmount(BigDecimal.valueOf(amount)); payment.setDiscountAmount(new BigDecimal("6000")); payment.setPaidAt(paidAt);
        payments.saveAndFlush(payment);
    }

    private AppUser userAccount(String username, AppUser.Role role) {
        var user = new AppUser(username, "test-hash", username + "@test.invalid");
        user.setRole(role); user.setEnabled(true); user.setFullName(username);
        return users.saveAndFlush(user);
    }
    private Order order(String code, AppUser assigned) {
        var result = new Order(); result.setTrackingCode(code); result.setSender(customer); result.setShipper(assigned);
        result.setSenderName("Người gửi thử nghiệm"); result.setReceiverName("Nguyễn Mai"); result.setReceiverPhone("0901234567");
        result.setSenderAddress("Địa chỉ lấy hàng"); result.setReceiverAddress("Địa chỉ giao hàng"); result.setWeightKg(1.0);
        result.setShippingFee(new BigDecimal("18000")); result.setCreatedAt(NOW.minusDays(2)); result.setUpdatedAt(NOW.minusDays(2));
        return orders.saveAndFlush(result);
    }
}
