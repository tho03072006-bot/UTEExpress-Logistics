package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.*;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService;
import vn.edu.hcmute.uteexpress.service.routing.HubService;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import vn.edu.hcmute.uteexpress.support.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ma trận trạng thái và sự cố; giao dịch đồng thời thật trên H2, không ghi Aiven. */
@SpringBootTest(classes = IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:shipment-incident-matrix;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-incident-matrix-uploads"
})
@ActiveProfiles("isolated-logistics")
@Import(ShipmentIncidentMatrixIntegrationTest.TestClock.class)
class ShipmentIncidentMatrixIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 9, 0);
    static final AtomicInteger SEQUENCE = new AtomicInteger();
    static final Set<String> ALLOWED = Set.of("PENDING_PICKUP>PICKED_UP", "PICKED_UP>IN_TRANSIT",
            "IN_TRANSIT>DELIVERED", "IN_TRANSIT>FAILED", "FAILED>IN_TRANSIT");
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock incidentClock() { return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired OrderComplaintRepository complaints;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired BranchRepository branches;
    @Autowired OrderRouteRepository routes;
    @Autowired OrderService orderService;
    @Autowired DispatchService dispatch;
    @Autowired TrackingService tracking;
    @Autowired DeliveryProofService proofs;
    @Autowired ShipmentQrService qr;
    @Autowired OperationsCaseService cases;
    @Autowired HubService hub;
    AppUser customer, manager, admin, shipper, otherShipper;

    @BeforeEach void seed() {
        RoutingFixtures.seedH2(context);
        customer = account(AppUser.Role.USER); manager = account(AppUser.Role.MANAGER); internal(manager);
        admin = account(AppUser.Role.ADMIN); internal(admin);
        shipper = account(AppUser.Role.SHIPPER); otherShipper = account(AppUser.Role.SHIPPER);
        var branch = branches.findByCode("SGN").orElseThrow();
        for (var user : List.of(manager, shipper, otherShipper)) staffBranches.saveAndFlush(new StaffBranch(user, branch, admin, NOW));
    }
    static Stream<Arguments> transitions() {
        return Arrays.stream(Order.OrderStatus.values()).flatMap(from -> Arrays.stream(Order.OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }
    @ParameterizedTest(name = "{0} → {1}") @MethodSource("transitions")
    void everyShipperTransitionRespectsTheWorkflow(Order.OrderStatus from, Order.OrderStatus to) {
        var order = at(from);
        if (from == Order.OrderStatus.IN_TRANSIT && to == Order.OrderStatus.DELIVERED) saveProof(order);
        Runnable update = () -> tracking.updateStatus(order.getId(), shipper.getUsername(), to,
                to == Order.OrderStatus.FAILED ? DeliveryFailureReason.OTHER : null, "Kiểm thử chuyển trạng thái", null);
        if (ALLOWED.contains(from.name() + ">" + to.name())) {
            assertDoesNotThrow(update::run);
            assertEquals(to, current(order).getStatus());
        } else {
            if (from == Order.OrderStatus.FAILED && to == Order.OrderStatus.RETURNED)
                assertThrows(IllegalArgumentException.class, update::run, "Chỉ Manager hoàn tất hồ sơ hoàn có bằng chứng");
            else assertThrows(IllegalStateException.class, update::run);
            assertEquals(from, current(order).getStatus());
        }
    }
    @ParameterizedTest @EnumSource(DeliveryFailureReason.class)
    void everyDeliveryFailureReasonPreservesUncollectedCod(DeliveryFailureReason reason) {
        var order = at(Order.OrderStatus.IN_TRANSIT);
        tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.FAILED, reason, "Kiểm thử lý do giao thất bại", NOW.plusHours(1));
        assertEquals(Order.OrderStatus.FAILED, current(order).getStatus());
        assertEquals(OrderPayment.PaymentStatus.UNPAID, payments.findByOrder(order).orElseThrow().getStatus());
        assertEquals(1, history.countByOrderAndStatus(order, Order.OrderStatus.FAILED));
        assertFalse(tracking.canRetry(current(order)), "Chưa tới giờ hẹn");
    }
    @ParameterizedTest @EnumSource(DeliveryFailureReason.class)
    void everyPickupFailureReasonKeepsTheOrderCancelable(DeliveryFailureReason reason) {
        var order = at(Order.OrderStatus.PENDING_PICKUP);
        var entry = tracking.reportPickupFailure(order.getId(), shipper.getUsername(), reason, "Kiểm thử lý do lấy thất bại", NOW.plusHours(1));
        assertTrue(entry.isPickupFailure()); assertEquals(reason.getPickupLabel(), entry.getReasonLabel());
        assertEquals(Order.OrderStatus.PENDING_PICKUP, current(order).getStatus());
        assertEquals(1, tracking.pickupFailures(order));
        orderService.cancelOrder(order.getId(), customer.getUsername());
        assertEquals(Order.OrderStatus.CANCELLED, current(order).getStatus());
    }
    @ParameterizedTest @EnumSource(value = Order.OrderStatus.class, names = "PENDING_PICKUP", mode = EnumSource.Mode.EXCLUDE)
    void pickupFailureIsForbiddenAfterThePickupStage(Order.OrderStatus status) {
        var order = at(status);
        assertThrows(IllegalStateException.class, () -> tracking.reportPickupFailure(order.getId(), shipper.getUsername(),
                DeliveryFailureReason.OTHER, "Kiểm thử sai giai đoạn", null));
        assertEquals(0, tracking.pickupFailures(order));
    }
    @ParameterizedTest @EnumSource(value = Order.OrderStatus.class, names = "IN_TRANSIT", mode = EnumSource.Mode.EXCLUDE)
    void proofCannotBeSubmittedOutsideDelivery(Order.OrderStatus status) {
        var order = at(status);
        assertThrows(IllegalStateException.class, () -> saveProof(order));
        assertTrue(proofs.findByOrder(order).isEmpty());
    }
    @ParameterizedTest @EnumSource(value = AppUser.Role.class, names = {"USER", "MANAGER", "ADMIN"})
    void nonShipperRolesCannotConfirmPickupOrReportFailure(AppUser.Role role) {
        var actor = role == AppUser.Role.USER ? customer : role == AppUser.Role.MANAGER ? manager : admin;
        var order = at(Order.OrderStatus.PENDING_PICKUP);
        assertThrows(IllegalStateException.class, () -> tracking.updateStatus(order.getId(), actor.getUsername(), Order.OrderStatus.PICKED_UP));
        assertThrows(IllegalStateException.class, () -> tracking.reportPickupFailure(order.getId(), actor.getUsername(),
                DeliveryFailureReason.OTHER, "Kiểm thử giả vai trò Shipper", null));
        assertEquals(Order.OrderStatus.PENDING_PICKUP, current(order).getStatus());
    }
    @Test void anotherShipperCannotTakeOverByChangingTheOrderId() {
        var order = at(Order.OrderStatus.PENDING_PICKUP);
        assertThrows(IllegalStateException.class, () -> tracking.updateStatus(order.getId(), otherShipper.getUsername(), Order.OrderStatus.PICKED_UP));
        assertThrows(IllegalStateException.class, () -> tracking.reportPickupFailure(order.getId(), otherShipper.getUsername(),
                DeliveryFailureReason.OTHER, "Kiểm thử đơn của Shipper khác", null));
    }
    @Test void duplicatePickupOnlyCreatesOneActualPickup() throws Exception {
        var order = at(Order.OrderStatus.PENDING_PICKUP);
        raceOne(() -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.PICKED_UP),
                () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.PICKED_UP));
        assertEquals(1, history.countByOrderAndStatus(order, Order.OrderStatus.PICKED_UP));
    }
    @Test void duplicateDeliveryFailureCountsOnlyOneAttempt() throws Exception {
        var order = at(Order.OrderStatus.IN_TRANSIT);
        Runnable fail = () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.FAILED,
                DeliveryFailureReason.REFUSED, "Kiểm thử khách từ chối nhận", null);
        raceOne(fail, fail);
        assertEquals(1, history.countByOrderAndStatus(order, Order.OrderStatus.FAILED));
        assertEquals(OrderPayment.PaymentStatus.UNPAID, payments.findByOrder(order).orElseThrow().getStatus());
    }
    @Test void customerCancellationAndPickupCannotBothSucceed() throws Exception {
        var order = at(Order.OrderStatus.PENDING_PICKUP);
        raceOne(() -> orderService.cancelOrder(order.getId(), customer.getUsername()),
                () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.PICKED_UP));
        assertTrue(Set.of(Order.OrderStatus.CANCELLED, Order.OrderStatus.PICKED_UP).contains(current(order).getStatus()));
        assertEquals(1, history.countByOrderAndStatus(order, Order.OrderStatus.CANCELLED) + history.countByOrderAndStatus(order, Order.OrderStatus.PICKED_UP));
    }
    @Test void counterReceiptAndCustomerCancellationCannotBothSucceed() throws Exception {
        var order = create(OrderRoute.PickupMode.DROP_OFF);
        raceOne(() -> orderService.cancelOrder(order.getId(), customer.getUsername()), () -> hub.receiveAtCounter(manager.getUsername(), order.getId()));
        var status = current(order).getStatus();
        assertTrue(Set.of(Order.OrderStatus.CANCELLED, Order.OrderStatus.PICKED_UP).contains(status));
        assertEquals(status == Order.OrderStatus.CANCELLED ? OrderRoute.Stage.PICKUP : OrderRoute.Stage.DEST_HUB,
                routes.findByOrder_Id(order.getId()).orElseThrow().getStage());
    }
    @Test void retryAndReturnRequestsCannotBothBecomeActive() throws Exception {
        var order = at(Order.OrderStatus.FAILED);
        var retry = form(OperationsCase.Kind.RETRY, order.getId(), order.getRevision(), null, NOW.plusHours(1));
        var back = form(OperationsCase.Kind.RETURN, order.getId(), order.getRevision(), null, null);
        raceOne(() -> cases.request(manager.getUsername(), retry, null), () -> cases.request(manager.getUsername(), back, null));
    }
    @Test void duplicateCompensationCompletionDoesNotRecordTwoPayouts() throws Exception {
        var order = at(Order.OrderStatus.IN_TRANSIT);
        var complaint = complaints.saveAndFlush(new OrderComplaint(order, "Kiểm thử tra soát", "Kiểm thử bồi thường", null, NOW));
        long id = cases.request(manager.getUsername(), form(OperationsCase.Kind.COMPENSATION, complaint.getId(), complaint.getRevision(), BigDecimal.TEN, null), image());
        var record = cases.view(admin.getUsername(), id);
        cases.review(admin.getUsername(), id, new OperationsReviewRequest(record.getRevision(), true, "Kiểm thử duyệt có chứng từ"));
        long revision = cases.view(admin.getUsername(), id).getRevision();
        Runnable complete = () -> cases.complete(admin.getUsername(), id, revision, "KIEMTHU-CHI-TRA", image());
        raceOne(complete, complete);
        assertEquals(OperationsCase.Status.COMPLETED, cases.view(admin.getUsername(), id).getStatus());
    }
    private void raceOne(Runnable first, Runnable second) throws Exception {
        var start = new CountDownLatch(1); var executor = Executors.newFixedThreadPool(2);
        try {
            var left = executor.submit(() -> competing(start, first)); var right = executor.submit(() -> competing(start, second)); start.countDown();
            assertEquals(1, left.get(15, TimeUnit.SECONDS) + right.get(15, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private int competing(CountDownLatch start, Runnable action) throws Exception {
        assertTrue(start.await(5, TimeUnit.SECONDS));
        try { action.run(); return 1; }
        catch (IllegalArgumentException | IllegalStateException | StaffConflictException expected) { return 0; }
    }
    private Order at(Order.OrderStatus status) {
        var order = create(OrderRoute.PickupMode.COURIER);
        dispatch.assign(order.getTrackingCode(), shipper.getId(), 0, order.getUpdatedAt(), "Kiểm thử phân công sự cố", manager.getUsername());
        var route = routes.findByOrder_Id(order.getId()).orElseThrow();
        if (status == Order.OrderStatus.IN_TRANSIT || status == Order.OrderStatus.FAILED || status == Order.OrderStatus.DELIVERED || status == Order.OrderStatus.RETURNED) {
            route.startLastMile(); routes.saveAndFlush(route);
        }
        var snapshot = current(order); snapshot.setStatus(status); snapshot = orders.saveAndFlush(snapshot);
        if (status == Order.OrderStatus.FAILED) history.saveAndFlush(new OrderStatusHistory(snapshot, shipper,
                Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.FAILED, DeliveryFailureReason.REFUSED, "Fixture đã tới lịch giao lại", NOW.minusMinutes(1), NOW.minusMinutes(2)));
        return current(order);
    }
    private void saveProof(Order order) {
        byte[] png = qr.generate(order.getTrackingCode());
        proofs.saveProof(order.getId(), shipper.getUsername(), new MockMultipartFile("proofImage", "proof.png", "image/png", png),
                "data:image/png;base64," + Base64.getEncoder().encodeToString(png));
    }
    private MockMultipartFile image() { return new MockMultipartFile("evidence", "receipt.png", "image/png", qr.generate("KIEMTHU-CHUNGTU")); }
    private OperationsCaseRequest form(OperationsCase.Kind kind, Long target, long revision, BigDecimal amount, LocalDateTime next) {
        return new OperationsCaseRequest(kind, target, revision, amount, next, "Kiểm thử hồ sơ sự cố", "KIEMTHU-CHUNGTU", UUID.randomUUID().toString());
    }
    private Order create(OrderRoute.PickupMode mode) {
        var form = new OrderCreateRequest(); RoutingFixtures.fill(form);
        form.setSenderName("Người gửi kiểm thử"); form.setSenderAddress("Số 1 Võ Văn Ngân"); form.setReceiverName("Người nhận kiểm thử");
        form.setReceiverAddress("Số 2 đường kiểm thử"); form.setReceiverPhone("0901234567"); form.setWeightKg(1d);
        form.setServiceType(Order.ServiceType.STANDARD); form.setPaymentMethod(OrderPayment.PaymentMethod.COD); form.setPickupMode(mode);
        return orderService.createOrder(form, customer.getUsername());
    }
    private Order current(Order order) { return orders.findById(order.getId()).orElseThrow(); }
    private AppUser account(AppUser.Role role) {
        String name = "incident" + SEQUENCE.incrementAndGet(); var user = new AppUser(name, "test-only-hash", name + "@test.invalid");
        user.setRole(role); user.setEnabled(true); user.setFullName("Tài khoản kiểm thử"); return users.saveAndFlush(user);
    }
    private void internal(AppUser user) {
        var value = new StaffAccount(); value.setUser(user); value.setActive(true); value.setPasswordChangeRequired(false); value.setIssuedBy("h2-test");
        staff.saveAndFlush(value);
    }
}
