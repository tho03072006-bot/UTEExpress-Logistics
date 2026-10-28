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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService.Alert;
import vn.edu.hcmute.uteexpress.service.tracking.DispatchService;
import vn.edu.hcmute.uteexpress.service.tracking.TrackingService;
import vn.edu.hcmute.uteexpress.util.PaymentReadiness;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tình huống thực tế quanh việc hủy đơn và hạn lấy hàng (tìm ra khi thử các ca có thể xảy ra):
 * đơn hủy trước khi lấy chỉ được hoàn cước, hủy đơn trả lại lượt mã giảm giá, đơn Hỏa tốc có hạn lấy riêng.
 * H2 in-memory với chi nhánh và biểu giá thật; không ghi Aiven.
 */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:cancellation-pickup-scenarios;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-cancellation-pickup-uploads"
})
@ActiveProfiles("isolated-logistics") @Transactional
@Import(CancellationAndPickupScenarioTest.TestClock.class)
class CancellationAndPickupScenarioTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 9, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock cancellationClock() { return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh")); }
    }
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired PromoCodeRepository promos;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired BranchRepository branches;
    @Autowired OrderService orderService;
    @Autowired OperationsOverviewService overview;
    @Autowired DispatchService dispatch;
    @Autowired TrackingService tracking;
    @Autowired OrderComplaintRepository complaints;
    @Autowired vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService cases;
    @Autowired jakarta.persistence.EntityManager em;
    AppUser customer, managerSgn, shipper;

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        var sgn = branches.findByCode("SGN").orElseThrow();
        customer = account("cancel-customer", AppUser.Role.USER);
        var admin = account("cancel-admin", AppUser.Role.ADMIN); internal(admin);
        managerSgn = account("cancel-manager-sgn", AppUser.Role.MANAGER); internal(managerSgn);
        staffBranches.save(new StaffBranch(managerSgn, sgn, admin, NOW));
        shipper = account("cancel-shipper", AppUser.Role.SHIPPER); staffBranches.save(new StaffBranch(shipper, sgn, admin, NOW));
    }

    @Test void orderCancelledBeforePickupCanOnlyHaveItsPaidFeeRefunded() {
        var order = create(intra(Order.ServiceType.STANDARD, OrderPayment.PaymentMethod.MOMO));
        var payment = markWalletPaid(order);
        orderService.cancelOrder(order.getId(), customer.getUsername());
        assertEquals(0, PaymentReadiness.compensationLimit(payment).compareTo(payment.getAmount()),
                "Hàng chưa từng vào tay hãng: tối đa là hoàn lại cước đã thu, không bồi thường 4 lần");
        var complaint = complaints.findByOrder(order).orElseThrow(() -> new AssertionError("Hủy đơn đã trả ví tự mở yêu cầu hoàn tiền"));
        var form = cases.prepare(managerSgn.getUsername(), vn.edu.hcmute.uteexpress.entity.admin.OperationsCase.Kind.COMPENSATION, complaint.getId());
        var over = new vn.edu.hcmute.uteexpress.dto.manager.OperationsCaseRequest(form.kind(), form.targetId(), form.revision(),
                payment.getAmount().add(BigDecimal.ONE), null, "Hoàn tiền đơn đã hủy", "", java.util.UUID.randomUUID().toString());
        var error = assertThrows(IllegalArgumentException.class, () -> cases.request(managerSgn.getUsername(), over, image()));
        assertTrue(error.getMessage().contains("chỉ được hoàn lại cước đã thu"), error.getMessage());
    }

    @Test void cancelledCodOrderHasNothingToRefundButPickedUpLostOrderKeepsFullCompensation() {
        var cancelled = create(intra(Order.ServiceType.STANDARD, OrderPayment.PaymentMethod.COD));
        orderService.cancelOrder(cancelled.getId(), customer.getUsername());
        assertEquals(0, PaymentReadiness.compensationLimit(payments.findByOrder(cancelled).orElseThrow()).signum());
        var lost = create(intra(Order.ServiceType.STANDARD, OrderPayment.PaymentMethod.COD));
        assign(lost); tracking.updateStatus(lost.getId(), shipper.getUsername(), Order.OrderStatus.PICKED_UP); em.flush();
        var payment = payments.findByOrder(lost).orElseThrow();
        assertEquals(0, PaymentReadiness.compensationLimit(payment).compareTo(payment.getAmount().multiply(BigDecimal.valueOf(3))),
                "Hàng đã lấy, mất trước khi giao, chưa thu cước: vẫn bồi thường tối đa 3 lần cước");
    }

    @Test void cancellingAnOrderGivesItsPromoUsageBack() {
        var promo = new PromoCode(); promo.setCode("CANCELONCE"); promo.setDescription("Mã một lượt để kiểm thử");
        promo.setDiscountType(PromoCode.DiscountType.PERCENT); promo.setDiscountValue(new BigDecimal("10"));
        promo.setMinOrderAmount(BigDecimal.ZERO); promo.setUsageLimit(1); promo.setActive(true);
        promo.setStartAt(NOW.minusDays(1)); promo.setEndAt(NOW.plusDays(10)); promos.saveAndFlush(promo);
        var request = intra(Order.ServiceType.STANDARD, OrderPayment.PaymentMethod.COD); request.setPromoCode("CANCELONCE");
        var first = create(request);
        assertEquals(1, promos.findById(promo.getId()).orElseThrow().getUsedCount());
        orderService.cancelOrder(first.getId(), customer.getUsername());
        em.flush(); em.clear();
        assertEquals(0, promos.findById(promo.getId()).orElseThrow().getUsedCount(), "Đơn hủy trước khi lấy không được tính lượt");
        var again = intra(Order.ServiceType.STANDARD, OrderPayment.PaymentMethod.COD); again.setPromoCode("CANCELONCE");
        var second = create(again);
        assertTrue(payments.findByOrder(second).orElseThrow().hasDiscount(), "Mã một lượt dùng lại được sau khi đơn trước bị hủy");
        assertEquals(1, promos.findById(promo.getId()).orElseThrow().getUsedCount());
    }

    @Test void superExpressOrderIsLatePickupAfterItsPromisedHoursNotTheGenericDay() {
        var late = create(intra(Order.ServiceType.SUPER_EXPRESS, OrderPayment.PaymentMethod.COD));
        var fresh = create(intra(Order.ServiceType.SUPER_EXPRESS, OrderPayment.PaymentMethod.COD));
        var standard = create(intra(Order.ServiceType.STANDARD, OrderPayment.PaymentMethod.COD));
        late.setCreatedAt(NOW.minusHours(5)); fresh.setCreatedAt(NOW.minusHours(3)); standard.setCreatedAt(NOW.minusHours(5)); orders.flush();
        var alerts = overview.alerts(managerSgn.getUsername(), Alert.LATE_PICKUP, 0).getContent();
        assertTrue(alerts.contains(late), "Hỏa tốc hứa giao trong 4 giờ: chờ lấy 5 giờ là trễ");
        assertFalse(alerts.contains(fresh), "Hỏa tốc mới chờ 3 giờ: chưa trễ");
        assertFalse(alerts.contains(standard), "Tiêu chuẩn vẫn theo hạn lấy chung 24 giờ");
        assertEquals(alerts.size(), overview.alertCount(managerSgn.getUsername(), Alert.LATE_PICKUP), "Số đếm ở tổng quan khớp danh sách");
    }

    private void assign(Order order) {
        dispatch.assign(order.getTrackingCode(), shipper.getId(), 0L, order.getUpdatedAt(), "Phân công kiểm thử tình huống", managerSgn.getUsername());
        em.flush();
    }
    private OrderPayment markWalletPaid(Order order) {
        var payment = payments.findByOrder(order).orElseThrow();
        payment.setStatus(OrderPayment.PaymentStatus.PAID); payment.setPaidAt(NOW); payment.setTransactionRef("MOMO-CANCEL-" + order.getId());
        return payments.saveAndFlush(payment);
    }
    private org.springframework.mock.web.MockMultipartFile image() {
        var png = new java.io.ByteArrayOutputStream();
        try { javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(8, 8, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", png); }
        catch (java.io.IOException impossible) { throw new IllegalStateException(impossible); }
        return new org.springframework.mock.web.MockMultipartFile("evidence", "receipt.png", "image/png", png.toByteArray());
    }
    private Order create(OrderCreateRequest request) { return orderService.createOrder(request, customer.getUsername()); }
    private OrderCreateRequest intra(Order.ServiceType service, OrderPayment.PaymentMethod method) {
        var request = new OrderCreateRequest();
        request.setPickupProvinceCode("79"); request.setPickupAreaCode("SGN-THUDUC");
        request.setDeliveryProvinceCode("79"); request.setDeliveryAreaCode("SGN-Q1");
        request.setSenderName("Người gửi thử"); request.setSenderAddress("Số 1 Võ Văn Ngân");
        request.setReceiverName("Người nhận thử"); request.setReceiverPhone("0901234567"); request.setReceiverAddress("Số 2 Lê Lợi");
        request.setWeightKg(1.0); request.setServiceType(service); request.setPaymentMethod(method);
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
