package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService.Alert;
import vn.edu.hcmute.uteexpress.service.manager.StaffOrderCancellationService;
import vn.edu.hcmute.uteexpress.service.tracking.DispatchService;
import vn.edu.hcmute.uteexpress.service.tracking.TrackingService;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tình huống thực tế cần nhân sự can thiệp (tìm ra khi thử các ca có thể xảy ra): hủy đơn đã trả ví phải mở yêu cầu
 * hoàn tiền, Manager thu hồi đơn khi Shipper nghỉ ngang, Manager/Admin hủy đơn treo, doanh thu không tính đơn hủy và
 * không gấp đôi đơn liên tỉnh. H2 in-memory với chi nhánh và biểu giá thật; không ghi Aiven.
 */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:staff-intervention-scenarios;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-staff-intervention-uploads"
})
@ActiveProfiles("isolated-logistics") @Transactional
@Import(StaffInterventionScenarioTest.TestClock.class)
class StaffInterventionScenarioTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock interventionClock() { return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
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
    @Autowired OperationsOverviewService overview;
    @Autowired DispatchService dispatch;
    @Autowired TrackingService tracking;
    @Autowired StaffOrderCancellationService cancellation;
    @Autowired vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService cases;
    @Autowired vn.edu.hcmute.uteexpress.repository.manager.ManagerDispatchRepository reports;
    @Autowired jakarta.persistence.EntityManager em;
    AppUser customer, admin, managerSgn, managerHan, shipperA, shipperB, shipperHan;
    Branch sgn, han;

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        sgn = branches.findByCode("SGN").orElseThrow(); han = branches.findByCode("HAN").orElseThrow();
        customer = account("staff-customer", AppUser.Role.USER);
        admin = account("staff-admin", AppUser.Role.ADMIN); internal(admin);
        managerSgn = account("staff-manager-sgn", AppUser.Role.MANAGER); internal(managerSgn); staffBranches.save(new StaffBranch(managerSgn, sgn, admin, NOW));
        managerHan = account("staff-manager-han", AppUser.Role.MANAGER); internal(managerHan); staffBranches.save(new StaffBranch(managerHan, han, admin, NOW));
        shipperA = account("staff-shipper-a", AppUser.Role.SHIPPER); staffBranches.save(new StaffBranch(shipperA, sgn, admin, NOW));
        shipperB = account("staff-shipper-b", AppUser.Role.SHIPPER); staffBranches.save(new StaffBranch(shipperB, sgn, admin, NOW));
        shipperHan = account("staff-shipper-han", AppUser.Role.SHIPPER); staffBranches.save(new StaffBranch(shipperHan, han, admin, NOW));
    }

    // ===== Hủy đơn đã trả ví: mở yêu cầu hoàn tiền, doanh thu không tính =====

    @Test void cancellingWalletPaidOrderOpensRefundRequestAndLeavesRevenue() {
        var order = create(intra(OrderPayment.PaymentMethod.MOMO));
        var payment = markWalletPaid(order);
        assertEquals(0, payment.getAmount().compareTo(revenue(sgn)), "Trước khi hủy: cước đã trả là doanh thu");
        orderService.cancelOrder(order.getId(), customer.getUsername());
        var refund = complaints.findByOrder(order).orElseThrow(() -> new AssertionError("Hủy đơn đã trả ví phải mở yêu cầu hoàn tiền"));
        assertEquals("Hoàn tiền đơn đã hủy", refund.getSubject());
        assertEquals(OrderComplaint.Status.OPEN, refund.getStatus());
        assertEquals(0, revenue(sgn).signum(), "Đơn đã hủy không còn là doanh thu");
        // Manager lập phiếu hoàn từ hồ sơ này, tối đa đúng số đã trả (đơn chưa lấy hàng).
        var form = cases.prepare(managerSgn.getUsername(), vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Kind.COMPENSATION, refund.getId());
        assertNotNull(form);
    }

    @Test void cancellingCodOrderOpensNoRefundRequest() {
        var order = create(intra(OrderPayment.PaymentMethod.COD));
        orderService.cancelOrder(order.getId(), customer.getUsername());
        assertTrue(complaints.findByOrder(order).isEmpty(), "Chưa thu đồng nào thì không có gì để hoàn");
    }

    @Test void cancellingWalletPaidOrderReopensAnExistingComplaintInsteadOfFailing() {
        var order = create(intra(OrderPayment.PaymentMethod.MOMO)); markWalletPaid(order);
        var earlier = complaints.saveAndFlush(new OrderComplaint(order, "Hỏi giờ lấy hàng", "Bao giờ Shipper tới?", null, NOW.minusHours(2)));
        org.springframework.test.util.ReflectionTestUtils.setField(earlier, "status", OrderComplaint.Status.RESOLVED); complaints.saveAndFlush(earlier);
        orderService.cancelOrder(order.getId(), customer.getUsername());
        assertFalse(complaints.findByOrder(order).orElseThrow().getStatus().isClosed(), "Hồ sơ cũ mở lại để xử lý hoàn tiền");
    }

    // ===== Doanh thu liên tỉnh =====

    @Test void interprovincialRevenueBelongsToTheOriginBranchOnly() {
        var request = intra(OrderPayment.PaymentMethod.MOMO);
        request.setDeliveryProvinceCode("01"); request.setDeliveryAreaCode("HAN-CAUGIAY");
        var order = create(request); var paid = markWalletPaid(order).getAmount();
        assertEquals(0, paid.compareTo(revenue(sgn)), "Chi nhánh gửi (nhận đơn) ghi doanh thu");
        assertEquals(0, revenue(han).signum(), "Chi nhánh nhận không ghi thêm lần nữa");
        assertEquals(0, paid.compareTo(reports.sumPaidFees(NOW.minusDays(1), NOW.plusDays(1), null)), "Admin xem tất cả: đúng một lần");
    }

    // ===== Thu hồi đơn khi Shipper nghỉ ngang =====

    @Test void failedOrderIsReclaimedFromOffShiftShipperAndHandedToAnother() {
        var order = failedBy(shipperA);
        assertTrue(overview.alerts(managerSgn.getUsername(), Alert.HELD, 0).getContent().contains(order), "Hiện trong nhóm Shipper đang giữ");
        var options = dispatch.reclaimOptions(managerSgn.getUsername(), java.util.List.of(order)).get(order.getId());
        assertEquals(java.util.List.of(shipperB.getId()), options.stream().map(c -> c.id()).toList(), "Chỉ gợi ý Shipper khác cùng chi nhánh");
        dispatch.reclaim(order.getTrackingCode(), shipperB.getId(), shipperA.getId(), order.getUpdatedAt(), "Shipper A nghỉ ca chiều", managerSgn.getUsername());
        em.flush();
        var reclaimed = orders.findById(order.getId()).orElseThrow();
        assertEquals(shipperB.getId(), reclaimed.getShipper().getId());
        var route = routes.findByOrder_Id(order.getId()).orElseThrow();
        assertEquals(shipperA.getId(), route.getPickupShipper().getId(), "Người đã lấy hàng giữ nguyên");
        assertEquals(shipperB.getId(), route.getDeliveryShipper().getId());
        assertTrue(history.findAll().stream().anyMatch(h -> h.getOrder().getId().equals(order.getId()) && h.getNote() != null
                && h.getNote().startsWith("Thu hồi từ")), "Có lịch sử thu hồi");
        assertTrue(tracking.findAssignedOrder(order.getId(), shipperA.getUsername()).isEmpty(), "Shipper A không còn mở được đơn");
    }

    @Test void pickedUpOrderIsReclaimedAndTheNewShipperDeliversIt() {
        var order = create(intra(OrderPayment.PaymentMethod.COD));
        assign(order, shipperA); tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.PICKED_UP); em.flush();
        var held = orders.findById(order.getId()).orElseThrow();
        dispatch.reclaim(held.getTrackingCode(), shipperB.getId(), shipperA.getId(), held.getUpdatedAt(), "Shipper A ốm, hàng đã nộp bưu cục", managerSgn.getUsername());
        em.flush();
        assertThrows(IllegalStateException.class, () -> tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT));
        tracking.updateStatus(order.getId(), shipperB.getUsername(), Order.OrderStatus.IN_TRANSIT);
        assertEquals(Order.OrderStatus.IN_TRANSIT, orders.findById(order.getId()).orElseThrow().getStatus());
    }

    @Test void reclaimIsRefusedWhileOutForDeliveryOrAcrossBranchesOrFromStalePage() {
        var order = create(intra(OrderPayment.PaymentMethod.COD));
        assign(order, shipperA);
        tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.PICKED_UP);
        tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.IN_TRANSIT); em.flush();
        var onTheRoad = orders.findById(order.getId()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> dispatch.reclaim(onTheRoad.getTrackingCode(), shipperB.getId(), shipperA.getId(),
                onTheRoad.getUpdatedAt(), "Thu hồi khi đang giao", managerSgn.getUsername()), "Đang trên đường giao: chờ báo kết quả");
        var failed = failedBy(shipperA);
        assertThrows(AccessDeniedException.class, () -> dispatch.reclaim(failed.getTrackingCode(), shipperB.getId(), shipperA.getId(),
                failed.getUpdatedAt(), "Kho khác thu hồi", managerHan.getUsername()));
        assertThrows(IllegalArgumentException.class, () -> dispatch.reclaim(failed.getTrackingCode(), shipperHan.getId(), shipperA.getId(),
                failed.getUpdatedAt(), "Giao cho Shipper chi nhánh khác", managerSgn.getUsername()));
        assertThrows(IllegalArgumentException.class, () -> dispatch.reclaim(failed.getTrackingCode(), shipperB.getId(), shipperA.getId(),
                failed.getUpdatedAt().minusMinutes(5), "Trang đã cũ", managerSgn.getUsername()));
        assertThrows(IllegalArgumentException.class, () -> dispatch.reclaim(failed.getTrackingCode(), shipperA.getId(), shipperA.getId(),
                failed.getUpdatedAt(), "Giao lại cho chính người đang giữ", managerSgn.getUsername()));
    }

    // ===== Manager/Admin hủy đơn treo =====

    @Test void managerCancelsOrderStuckAfterThreePickupFailures() {
        var order = create(intra(OrderPayment.PaymentMethod.COD));
        assign(order, shipperA);
        for (int i = 3; i >= 1; i--) history.save(new OrderStatusHistory(order, shipperA, Order.OrderStatus.PENDING_PICKUP,
                Order.OrderStatus.PENDING_PICKUP, DeliveryFailureReason.OTHER, "Không liên lạc được người gửi", null, NOW.minusHours(i)));
        history.flush();
        assertFalse(tracking.canReportPickupFailure(order), "Đã đủ 3 lần, Shipper không báo thêm được");
        var stuck = orders.findById(order.getId()).orElseThrow();
        assertTrue(cancellation.cancelable(managerSgn.getUsername(), java.util.List.of(stuck)).contains(stuck.getId()));
        assertFalse(cancellation.cancelable(managerHan.getUsername(), java.util.List.of(stuck)).contains(stuck.getId()), "Chỉ chi nhánh gửi");
        cancellation.cancel(managerSgn.getUsername(), stuck.getId(), stuck.getRevision(), "Lấy hàng thất bại 3 lần, không liên lạc được người gửi");
        assertEquals(Order.OrderStatus.CANCELLED, orders.findById(order.getId()).orElseThrow().getStatus());
        assertTrue(history.findAll().stream().anyMatch(h -> h.getOrder().getId().equals(order.getId())
                && h.getStatus() == Order.OrderStatus.CANCELLED && h.getNote().startsWith("Chi nhánh hủy vận đơn")));
    }

    @Test void adminCancelsUnpaidWalletOrderAndPaidOneGetsRefundRequest() {
        var unpaid = create(intra(OrderPayment.PaymentMethod.MOMO));
        cancellation.cancel(admin.getUsername(), unpaid.getId(), unpaid.getRevision(), "Đơn ví không thanh toán sau 3 ngày");
        assertEquals(Order.OrderStatus.CANCELLED, orders.findById(unpaid.getId()).orElseThrow().getStatus());
        assertTrue(complaints.findByOrder(unpaid).isEmpty());
        var paid = create(intra(OrderPayment.PaymentMethod.MOMO)); markWalletPaid(paid);
        cancellation.cancel(managerSgn.getUsername(), paid.getId(), orders.findById(paid.getId()).orElseThrow().getRevision(),
                "Khách không mang hàng tới quầy sau 5 ngày");
        assertTrue(complaints.findByOrder(paid).isPresent(), "Đã trả ví thì mở yêu cầu hoàn tiền");
    }

    @Test void staffCannotCancelPickedUpOrForeignOrStaleOrdersOrWithoutReason() {
        var order = create(intra(OrderPayment.PaymentMethod.COD));
        assertThrows(IllegalArgumentException.class, () -> cancellation.cancel(managerSgn.getUsername(), order.getId(), order.getRevision(), "Ngắn"));
        assertThrows(AccessDeniedException.class, () -> cancellation.cancel(managerHan.getUsername(), order.getId(), order.getRevision(),
                "Kho khác hủy đơn của kho gửi"));
        assertThrows(StaffConflictException.class, () -> cancellation.cancel(managerSgn.getUsername(), order.getId(), order.getRevision() + 9,
                "Bấm hủy từ trang đã cũ"));
        assign(order, shipperA); tracking.updateStatus(order.getId(), shipperA.getUsername(), Order.OrderStatus.PICKED_UP); em.flush();
        var picked = orders.findById(order.getId()).orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> cancellation.cancel(managerSgn.getUsername(), picked.getId(), picked.getRevision(),
                "Hàng đã lấy thì không hủy được nữa"));
    }

    // ===== Hỗ trợ =====

    private BigDecimal revenue(Branch branch) { return reports.sumPaidFees(NOW.minusDays(1), NOW.plusDays(1), branch.getId()); }
    private Order failedBy(AppUser shipper) {
        var order = create(intra(OrderPayment.PaymentMethod.COD));
        assign(order, shipper);
        tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.PICKED_UP);
        tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.IN_TRANSIT);
        tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.FAILED, DeliveryFailureReason.REFUSED, "Khách hẹn hôm khác", null);
        em.flush();
        return orders.findById(order.getId()).orElseThrow();
    }
    private void assign(Order order, AppUser shipper) {
        dispatch.assign(order.getTrackingCode(), shipper.getId(), order.getShipper() == null ? 0L : order.getShipper().getId(),
                order.getUpdatedAt(), "Phân công kiểm thử tình huống", managerSgn.getUsername());
        em.flush();
    }
    private OrderPayment markWalletPaid(Order order) {
        var payment = payments.findByOrder(order).orElseThrow();
        payment.setStatus(OrderPayment.PaymentStatus.PAID); payment.setPaidAt(NOW); payment.setTransactionRef("MOMO-STAFF-" + order.getId());
        return payments.saveAndFlush(payment);
    }
    private Order create(OrderCreateRequest request) { return orderService.createOrder(request, customer.getUsername()); }
    private OrderCreateRequest intra(OrderPayment.PaymentMethod method) {
        var request = new OrderCreateRequest();
        request.setPickupProvinceCode("79"); request.setPickupAreaCode("SGN-THUDUC");
        request.setDeliveryProvinceCode("79"); request.setDeliveryAreaCode("SGN-Q1");
        request.setSenderName("Người gửi thử"); request.setSenderAddress("Số 1 Võ Văn Ngân");
        request.setReceiverName("Người nhận thử"); request.setReceiverPhone("0901234567"); request.setReceiverAddress("Số 2 Lê Lợi");
        request.setWeightKg(1.0); request.setServiceType(Order.ServiceType.STANDARD); request.setPaymentMethod(method);
        return request;
    }
    private AppUser account(String username, AppUser.Role role) {
        var value = new AppUser(username, "test-only-hash", username + "@test.invalid");
        value.setEnabled(true); value.setRole(role); value.setFullName("Tài khoản kiểm thử"); value.setPhone("0912345678");
        return users.saveAndFlush(value);
    }
    private StaffAccount internal(AppUser user) {
        var value = new StaffAccount(); value.setUser(user); value.setIssuedBy("test-only"); value.setActive(true); value.setPasswordChangeRequired(false);
        return staff.saveAndFlush(value);
    }
}
