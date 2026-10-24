package vn.edu.hcmute.uteexpress.service.manager;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.dto.admin.ShippingTariffUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import static org.junit.jupiter.api.Assertions.*;

/** Chạy trên Aiven thật; mọi tài khoản/đơn/khuyến mãi tạo trong test đều rollback. */
@SpringBootTest @Transactional
class AivenOperationsIntegrationTest {
    @Autowired EntityManager em;
    @Autowired ManagerDispatchService dispatch;
    @Autowired ManagerPromotionService promotions;
    @Autowired ManagerReportService reports;
    @Autowired ShippingTariffService tariffs;
    @Autowired AuditService audit;
    @Autowired vn.edu.hcmute.uteexpress.service.impl.OrderCreationWriter orders;
    @Autowired vn.edu.hcmute.uteexpress.service.OrderPaymentService payments;
    @Autowired Clock clock;
    private String operator(AppUser.Role role) {
        var user = new AppUser("qa_" + UUID.randomUUID().toString().replace("-", ""), "test-hash", UUID.randomUUID() + "@example.com");
        user.setRole(role); user.setEnabled(true); em.persist(user);
        var staff = new StaffAccount(); staff.setUser(user); staff.setIssuedBy("integration-test");
        staff.setPasswordChangeRequired(false); em.persist(staff); em.flush(); return user.getUsername();
    }
    @Test void realQueriesAssignmentTariffAndAuditUseSharedSchema() {
        var admin = operator(AppUser.Role.ADMIN);
        var sender = new AppUser("qa_" + UUID.randomUUID().toString().replace("-", ""), "hash", UUID.randomUUID() + "@example.com");
        sender.setEnabled(true); em.persist(sender);
        var shipper = new AppUser("qa_" + UUID.randomUUID().toString().replace("-", ""), "hash", UUID.randomUUID() + "@example.com");
        shipper.setEnabled(true); shipper.setRole(AppUser.Role.SHIPPER); em.persist(shipper);
        var order = new Order(); order.setSender(sender); order.setTrackingCode("QA" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        order.setSenderName("Kiểm thử"); order.setSenderAddress("Địa chỉ kiểm thử");
        order.setReceiverName("Kiểm thử"); order.setReceiverAddress("Địa chỉ kiểm thử"); order.setReceiverPhone("0901234567");
        order.setWeightKg(1.0); order.setServiceType(Order.ServiceType.STANDARD); order.setShippingFee(BigDecimal.valueOf(18000));
        em.persist(order); em.flush();
        var area = em.createQuery("select a from ProvinceArea a where a.areaCode = :code", ProvinceArea.class)
                .setParameter("code", "SGN-THUDUC").getSingleResult();
        var operator = em.createQuery("select u from AppUser u where u.username = :name", AppUser.class)
                .setParameter("name", admin).getSingleResult();
        em.persist(new StaffBranch(shipper, area.getBranch(), operator, LocalDateTime.now(clock)));
        em.persist(new ShipperZone(shipper, area, true));
        var route = new OrderRoute(order, area, area); em.persist(route);
        var payment = payments.createForOrder(order, OrderPayment.PaymentMethod.COD, null, BigDecimal.ZERO);
        em.flush();
        assertEquals(OrderPayment.PaymentStatus.UNPAID, payment.getStatus());
        assertEquals(0, payment.getAmount().compareTo(order.getShippingFee()));
        var form = new OrderAssignmentRequest(); form.setShipperId(shipper.getId()); form.setRevision(order.getRevision());
        form.setReason("Phân công kiểm thử");
        dispatch.assignOrder(admin, order.getId(), form);
        em.flush();
        assertEquals(shipper.getId(), order.getShipper().getId()); assertTrue(order.getRevision() > form.getRevision());
        assertEquals(shipper.getId(), route.getPickupShipper().getId());
        assertEquals(shipper.getId(), route.getDeliveryShipper().getId());
        assertEquals(1L, em.createQuery("select count(h) from OrderStatusHistory h where h.order = :order", Long.class)
                .setParameter("order", order).getSingleResult());
        assertEquals(1L, em.createQuery("select count(n) from ShipperNotification n where n.order = :order and n.recipient = :shipper", Long.class)
                .setParameter("order", order).setParameter("shipper", shipper).getSingleResult());
        assertThrows(StaffConflictException.class, () -> dispatch.assignOrder(admin, order.getId(), form));
        assertNotNull(dispatch.findShippers(admin, 0)); assertNotNull(dispatch.findPendingOrders(admin, 0));
        var report = reports.report(admin, LocalDate.now(clock), LocalDate.now(clock));
        assertTrue(report.totalOrders() >= 1); assertNotNull(report.paidShippingFees());
        assertTrue(audit.findEvents(admin, 0).getContent().stream().anyMatch(e -> e.target().equals("order:" + order.getId())));
        assertEquals(3, tariffs.findTariffs(admin).size());
        var tariff = tariffs.findTariffs(admin).stream().filter(t -> t.getServiceType() == Order.ServiceType.STANDARD).findFirst().orElseThrow();
        var change = new ShippingTariffUpdateRequest(); change.setRevision(tariff.getRevision());
        change.setBaseFee(tariff.getBaseFee().add(BigDecimal.ONE)); change.setFeePerKg(tariff.getFeePerKg());
        change.setReason("Kiểm thử rollback cước");
        var before = tariffs.quote(Order.ServiceType.STANDARD, 1);
        tariffs.updateTariff(admin, Order.ServiceType.STANDARD, change);
        assertEquals(before.add(BigDecimal.ONE), tariffs.quote(Order.ServiceType.STANDARD, 1));
        assertEquals(BigDecimal.valueOf(18000), order.getShippingFee());
    }
    @Test void promotionUsageAndCustomerCreationShareOneTransaction() {
        var manager = operator(AppUser.Role.MANAGER);
        var form = new PromotionRequest(); form.setCode("QA" + UUID.randomUUID().toString().replace("-", "").substring(0, 20));
        form.setDescription("Khuyến mãi kiểm thử rollback"); form.setDiscountType(PromoCode.DiscountType.PERCENT);
        form.setDiscountValue(BigDecimal.TEN); form.setMaxDiscountAmount(BigDecimal.valueOf(30000));
        form.setMinOrderAmount(BigDecimal.ZERO); form.setUsageLimit(1);
        form.setStartAt(LocalDateTime.now(clock).minusDays(1)); form.setEndAt(LocalDateTime.now(clock).plusDays(1));
        promotions.savePromotion(manager, null, form);
        var sender = new AppUser("qa_" + UUID.randomUUID().toString().replace("-", ""), "hash", UUID.randomUUID() + "@example.com");
        sender.setEnabled(true); em.persist(sender); em.flush();
        var request = new OrderCreateRequest();
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.fill(request);
        request.setSenderName("Người gửi kiểm thử"); request.setSenderAddress("Địa chỉ kiểm thử");
        request.setReceiverName("Người nhận kiểm thử"); request.setReceiverAddress("Địa chỉ kiểm thử");
        request.setReceiverPhone("+84 901 234 567"); request.setWeightKg(1.0);
        request.setServiceType(Order.ServiceType.STANDARD); request.setPromoCode(form.getCode());
        var order = orders.createRouted(request, sender.getUsername()); em.flush();
        assertNotNull(order.getId());
        var payment = em.createQuery("select p from OrderPayment p where p.order = :order", OrderPayment.class)
            .setParameter("order", order).getSingleResult();
        assertTrue(payment.getAmount().compareTo(order.getShippingFee()) < 0);
        var promo = em.createQuery("select p from PromoCode p where p.code = :code", PromoCode.class)
            .setParameter("code", form.getCode()).getSingleResult();
        assertEquals(1, promo.getUsedCount()); assertFalse(promo.hasRemainingUsage());
        assertThrows(IllegalStateException.class, () -> orders.createRouted(request, sender.getUsername()));
    }
}

