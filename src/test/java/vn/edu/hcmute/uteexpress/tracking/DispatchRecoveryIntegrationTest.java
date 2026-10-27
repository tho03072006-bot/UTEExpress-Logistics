package vn.edu.hcmute.uteexpress.tracking;

import java.time.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService;
import vn.edu.hcmute.uteexpress.service.routing.*;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import vn.edu.hcmute.uteexpress.support.*;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Giao lại và đổi ca phải giữ đúng tải, chi nhánh và người lấy hàng. Chỉ ghi H2 in-memory. */
@SpringBootTest(classes = IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:dispatch-recovery;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-dispatch-recovery-uploads"
})
@ActiveProfiles("isolated-logistics")
@AutoConfigureMockMvc
@Import(DispatchRecoveryIntegrationTest.TestClock.class)
class DispatchRecoveryIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 9, 0);
    static final String FULL = "Shipper đã đủ số đơn tối đa đang giữ.";
    static final AtomicInteger SEQUENCE = new AtomicInteger();
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock recoveryClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired BranchRepository branches;
    @Autowired OrderRouteRepository routes;
    @Autowired LinehaulTripRepository trips;
    @Autowired OperationsPolicyRepository policies;
    @Autowired OrderService orderService;
    @Autowired DispatchService dispatch;
    @Autowired TrackingService tracking;
    @Autowired ShipperBulkService bulk;
    @Autowired HubService hub;
    @Autowired OperationsOverviewService overview;
    @Autowired MockMvc mvc;
    AppUser customer, admin, managerSgn, managerHan, shipperA, shipperB, shipperHanA, shipperHanB;
    StaffAccount managerSgnAccount;

    @BeforeEach void seed() {
        RoutingFixtures.seedH2(context);
        var policy = policies.findAll().get(0); policy.setMaxActiveOrders(15); policies.saveAndFlush(policy);
        customer = account(AppUser.Role.USER);
        admin = account(AppUser.Role.ADMIN); internal(admin);
        managerSgn = account(AppUser.Role.MANAGER); managerSgnAccount = internal(managerSgn); branch(managerSgn, "SGN");
        managerHan = account(AppUser.Role.MANAGER); internal(managerHan); branch(managerHan, "HAN");
        shipperA = account(AppUser.Role.SHIPPER); branch(shipperA, "SGN");
        shipperB = account(AppUser.Role.SHIPPER); branch(shipperB, "SGN");
        shipperHanA = account(AppUser.Role.SHIPPER); branch(shipperHanA, "HAN");
        shipperHanB = account(AppUser.Role.SHIPPER); branch(shipperHanB, "HAN");
    }

    @Test void retryAtFifteenActiveOrdersIsRejectedWithoutChangingStatusOrHistory() {
        var failed = failedDue(); hold(shipperA, 15);
        long before = history.count();
        var error = assertThrows(IllegalArgumentException.class,
                () -> tracking.updateStatus(failed.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT));
        assertEquals(FULL, error.getMessage());
        assertEquals(15, dispatch.load(shipperA.getId()));
        assertEquals(Order.OrderStatus.FAILED, current(failed).getStatus());
        assertEquals(before, history.count());
    }

    @Test void retryAtFourteenActiveOrdersUsesTheLastSlot() {
        var failed = failedDue(); hold(shipperA, 14);
        tracking.updateStatus(failed.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT);
        assertEquals(15, dispatch.load(shipperA.getId()));
        assertEquals(Order.OrderStatus.IN_TRANSIT, current(failed).getStatus());
    }

    @Test void retryUsesTheCurrentConfiguredCapacity() {
        var failed = failedDue(); hold(shipperA, 2);
        var policy = policies.findAll().get(0); policy.setMaxActiveOrders(2); policies.saveAndFlush(policy);
        assertEquals(FULL, assertThrows(IllegalArgumentException.class,
                () -> tracking.updateStatus(failed.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT)).getMessage());
        assertEquals(2, dispatch.load(shipperA.getId()));
    }

    @Test void startingDeliveryOfAnAlreadyActiveOrderDoesNotRequireAnotherSlot() {
        var picked = create(false, OrderRoute.PickupMode.COURIER); assign(picked, shipperA, managerSgn);
        tracking.updateStatus(picked.getId(), shipperA.getUsername(), Order.OrderStatus.PICKED_UP);
        hold(shipperA, 14);
        tracking.updateStatus(picked.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT);
        assertEquals(15, dispatch.load(shipperA.getId()));
        assertEquals(Order.OrderStatus.IN_TRANSIT, current(picked).getStatus());
    }

    @Test void bulkRetryCannotConsumeTheLastSlotTwice() {
        var first = failedDue(); var second = failedDue(); hold(shipperA, 14);
        var result = bulk.update(shipperA.getUsername(), List.of(first.getId(), second.getId()), Order.OrderStatus.IN_TRANSIT);
        assertEquals(List.of(first.getTrackingCode()), result.updated());
        assertEquals(1, result.failed().size()); assertEquals(FULL, result.failed().get(0).reason());
        assertEquals(Order.OrderStatus.FAILED, current(second).getStatus());
        assertEquals(15, dispatch.load(shipperA.getId()));
    }

    @Test void concurrentRetryAndAssignmentCannotBothConsumeTheLastSlot() throws Exception {
        var failed = failedDue(); hold(shipperA, 14);
        var pending = create(false, OrderRoute.PickupMode.COURIER);
        raceForLastSlot(() -> tracking.updateStatus(failed.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT),
                () -> assign(pending, shipperA, managerSgn));
        assertEquals(15, dispatch.load(shipperA.getId()));
        assertNotEquals(current(failed).getStatus() == Order.OrderStatus.IN_TRANSIT, current(pending).getShipper() != null);
    }

    @Test void twoConcurrentRetriesCannotBothConsumeTheLastSlot() throws Exception {
        var first = failedDue(); var second = failedDue(); hold(shipperA, 14);
        raceForLastSlot(() -> tracking.updateStatus(first.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT),
                () -> tracking.updateStatus(second.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT));
        assertEquals(15, dispatch.load(shipperA.getId()));
        assertNotEquals(current(first).getStatus(), current(second).getStatus());
    }

    @Test void localDropOffCanChangeDeliveryShipperBeforeDeparture() {
        var order = assignedCounterOrder();
        var route = routes.findByOrder_Id(order.getId()).orElseThrow();
        assertEquals(OrderRoute.Stage.LAST_MILE, route.getStage());
        assertTrue(dispatch.reclaimOptions(managerSgn.getUsername(), List.of(current(order))).get(order.getId())
                .stream().anyMatch(c -> c.id().equals(shipperB.getId())));
        reclaim(order, shipperB, managerSgn);
        assertEquals(shipperB.getId(), current(order).getShipper().getId());
        var changed = routes.findByOrder_Id(order.getId()).orElseThrow();
        assertEquals(shipperB.getId(), changed.getDeliveryShipper().getId());
        assertNull(changed.getPickupShipper(), "Khách tự mang tới quầy, không có shipper lấy hàng");
        assertEquals(Order.OrderStatus.PICKED_UP, current(order).getStatus());
    }

    @Test void interprovincialDeliveryCanChangeShipperWithoutChangingPickupHistory() {
        long before = overview.alertCount(managerHan.getUsername(), OperationsOverviewService.Alert.HELD);
        var order = assignedAtDestination();
        assertEquals(before + 1, overview.alertCount(managerHan.getUsername(), OperationsOverviewService.Alert.HELD));
        assertDoesNotThrow(() -> reclaim(order, shipperHanB, managerHan));
        var changed = routes.findByOrder_Id(order.getId()).orElseThrow();
        assertEquals(shipperA.getId(), changed.getPickupShipper().getId());
        assertEquals(shipperHanB.getId(), changed.getDeliveryShipper().getId());
        assertEquals(shipperHanB.getId(), current(order).getShipper().getId());
        assertEquals(OrderRoute.Stage.LAST_MILE, changed.getStage());
    }

    @Test void managerCanSeeAndReassignAnOrderFromTheHeldAlertsPage() throws Exception {
        long before = overview.alertCount(managerSgn.getUsername(), OperationsOverviewService.Alert.HELD);
        var order = assignedCounterOrder();
        assertEquals(before + 1, overview.alertCount(managerSgn.getUsername(), OperationsOverviewService.Alert.HELD));
        var principal = new StaffPrincipal(managerSgnAccount);
        var auth = new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
        mvc.perform(get("/manager/canh-bao").param("alert", "HELD").with(authentication(auth)))
                .andExpect(status().isOk()).andExpect(content().string(containsString(order.getTrackingCode())))
                .andExpect(content().string(containsString("Chuyển Shipper khác")));
        var snapshot = current(order);
        mvc.perform(post("/manager/canh-bao/thu-hoi").with(authentication(auth)).with(csrf())
                .param("trackingCode", order.getTrackingCode()).param("shipperId", shipperB.getId().toString())
                .param("expectedShipperId", shipperA.getId().toString())
                .param("expectedUpdatedAt", snapshot.getUpdatedAt().toString())
                .param("note", "Kiểm thử đổi người giao từ màn hình cảnh báo").param("alert", "HELD"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/manager/canh-bao?alert=HELD"))
                .andExpect(flash().attributeExists("message"));
        assertEquals(shipperB.getId(), current(order).getShipper().getId());
        assertThrows(IllegalStateException.class,
                () -> tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT));
    }

    @Test void lastMileReassignmentStillChecksBranchStalenessAndDeliveryState() {
        var order = assignedCounterOrder(); var snapshot = current(order);
        assertThrows(AccessDeniedException.class, () -> reclaim(order, shipperB, managerHan));
        assertThrows(IllegalArgumentException.class, () -> reclaim(order, shipperHanA, managerSgn));
        assertThrows(IllegalArgumentException.class, () -> dispatch.reclaim(order.getTrackingCode(), shipperB.getId(),
                shipperA.getId(), snapshot.getUpdatedAt().minusMinutes(1), "Trang cũ yêu cầu đổi ca", managerSgn.getUsername()));
        reclaim(order, shipperB, admin);
        tracking.updateStatus(order.getId(), shipperB.getUsername(), Order.OrderStatus.IN_TRANSIT);
        assertThrows(IllegalArgumentException.class, () -> reclaim(order, shipperA, managerSgn));
        assertEquals(shipperB.getId(), current(order).getShipper().getId());
    }

    @Test void fullShipperIsNotOfferedAndCannotReceiveAReclaimedOrder() {
        var order = assignedCounterOrder(); hold(shipperB, 15);
        var options = dispatch.reclaimOptions(managerSgn.getUsername(), List.of(current(order))).get(order.getId());
        assertNotNull(options);
        assertFalse(options.stream().anyMatch(c -> c.id().equals(shipperB.getId())));
        assertEquals(FULL, assertThrows(IllegalArgumentException.class, () -> reclaim(order, shipperB, managerSgn)).getMessage());
        assertEquals(shipperA.getId(), current(order).getShipper().getId());
    }

    private void raceForLastSlot(Runnable first, Runnable second) throws Exception {
        var start = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> competing(start, first));
            var right = executor.submit(() -> competing(start, second)); start.countDown();
            assertEquals(1, left.get(15, TimeUnit.SECONDS) + right.get(15, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private int competing(CountDownLatch start, Runnable action) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try { action.run(); return 1; }
        catch (IllegalArgumentException ex) { assertEquals(FULL, ex.getMessage()); return 0; }
    }
    private Order assignedCounterOrder() {
        var order = create(false, OrderRoute.PickupMode.DROP_OFF);
        hub.receiveAtCounter(managerSgn.getUsername(), order.getId()); assign(order, shipperA, managerSgn); return order;
    }
    private Order assignedAtDestination() {
        var order = create(true, OrderRoute.PickupMode.COURIER); assign(order, shipperA, managerSgn);
        tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.PICKED_UP);
        tracking.handoverToHub(order.getId(), shipperA.getUsername());
        var trip = hub.createTrip(managerSgn.getUsername(), branches.findByCode("SGN").orElseThrow().getId(),
                branches.findByCode("HAN").orElseThrow().getId(), "51C-123.45", "Nguyễn Văn Kiểm", "0901234567", List.of(order.getId()));
        hub.depart(managerSgn.getUsername(), trip.getId(), trip.getRevision(), "RECOVERY-001");
        var departed = trips.findById(trip.getId()).orElseThrow();
        hub.arrive(managerHan.getUsername(), trip.getId(), departed.getRevision(), "RECOVERY-001", List.of(order.getId()), "Nhận đủ kiện kiểm thử");
        assign(order, shipperHanA, managerHan); return order;
    }
    private Order failedDue() {
        var order = create(false, OrderRoute.PickupMode.COURIER); assign(order, shipperA, managerSgn);
        tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.PICKED_UP);
        tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT);
        // Fixture một lượt thất bại đã tới giờ hẹn, không cần chờ thời gian thực hay bỏ kiểm tra lịch.
        var failed = current(order); failed.setStatus(Order.OrderStatus.FAILED); orders.saveAndFlush(failed);
        history.saveAndFlush(new OrderStatusHistory(failed, shipperA, Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.FAILED,
                DeliveryFailureReason.REFUSED, "Kiểm thử khách hẹn giao lại", NOW.minusMinutes(1), NOW.minusMinutes(2)));
        return current(order);
    }
    private void hold(AppUser shipper, int count) {
        for (int i = 0; i < count; i++) assign(create(false, OrderRoute.PickupMode.COURIER), shipper, managerSgn);
    }
    private void assign(Order order, AppUser shipper, AppUser manager) {
        var snapshot = current(order);
        dispatch.assign(order.getTrackingCode(), shipper.getId(), snapshot.getShipper() == null ? 0L : snapshot.getShipper().getId(),
                snapshot.getUpdatedAt(), "Phân công kiểm thử đổi ca", manager.getUsername());
    }
    private void reclaim(Order order, AppUser shipper, AppUser manager) {
        var snapshot = current(order);
        dispatch.reclaim(order.getTrackingCode(), shipper.getId(), snapshot.getShipper().getId(), snapshot.getUpdatedAt(),
                "Kiểm thử đổi shipper trước khi giao", manager.getUsername());
    }
    private Order current(Order order) { return orders.findById(order.getId()).orElseThrow(); }
    private Order create(boolean interprovincial, OrderRoute.PickupMode mode) {
        var form = new OrderCreateRequest(); RoutingFixtures.fill(form);
        if (interprovincial) { form.setDeliveryProvinceCode("01"); form.setDeliveryAreaCode("HAN-CAUGIAY"); }
        form.setSenderName("Người gửi kiểm thử"); form.setSenderAddress("Số 1 Võ Văn Ngân");
        form.setReceiverName("Người nhận kiểm thử"); form.setReceiverPhone("0901234567"); form.setReceiverAddress("Số 2 đường kiểm thử");
        form.setWeightKg(1d); form.setServiceType(Order.ServiceType.STANDARD); form.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        form.setPickupMode(mode); return orderService.createOrder(form, customer.getUsername());
    }
    private AppUser account(AppUser.Role role) {
        String name = "recovery" + SEQUENCE.incrementAndGet();
        var user = new AppUser(name, "test-only-hash", name + "@test.invalid");
        user.setRole(role); user.setEnabled(true); user.setFullName("Tài khoản kiểm thử");
        return users.saveAndFlush(user);
    }
    private StaffAccount internal(AppUser user) {
        var value = new StaffAccount(); value.setUser(user); value.setIssuedBy("h2-test-only");
        value.setActive(true); value.setPasswordChangeRequired(false); return staff.saveAndFlush(value);
    }
    private void branch(AppUser user, String code) {
        staffBranches.saveAndFlush(new StaffBranch(user, branches.findByCode(code).orElseThrow(), admin, NOW));
    }
}
