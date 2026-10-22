package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.dto.tracking.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;
import vn.edu.hcmute.uteexpress.service.*;
import vn.edu.hcmute.uteexpress.service.manager.*;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Kiểm thử chéo bốn vai trò trên H2 in-memory, tuyệt đối không ghi dữ liệu Aiven chung. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:cross-role-sync-test;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "app.upload.dir=target/test-sync-proof-uploads"
})
@AutoConfigureMockMvc @ActiveProfiles("isolated-logistics") @Transactional @RecordApplicationEvents
@Import(CrossRoleSynchronizationIntegrationTest.TestClock.class)
class CrossRoleSynchronizationIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 16, 10, 0);
    @TestConfiguration static class TestClock {
        @Bean @Primary Clock synchronizationClock() {
            return Clock.fixed(NOW.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(), ZoneId.of("Asia/Ho_Chi_Minh"));
        }
    }
    @Autowired MockMvc mvc;
    @Autowired ApplicationEvents events;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired OrderComplaintRepository complaints;
    @Autowired CodSettlementRepository settlements;
    @Autowired OrderPaymentService paymentService;
    @Autowired OrderService orderService;
    @Autowired DispatchService dispatch;
    @Autowired TrackingService tracking;
    @Autowired OperationsCaseService cases;
    @Autowired OperationsProgressService progress;
    @Autowired OperationsOverviewService overview;
    @Autowired ShipperStatisticsService statistics;
    @Autowired ShipmentQrService qr;
    AppUser customer, otherCustomer, shipper, otherShipper, manager, admin;
    StaffAccount managerAccount, adminAccount;
    Order order;
    OrderPayment payment;

    @BeforeEach void seed() {
        customer = account("sync-customer", AppUser.Role.USER); otherCustomer = account("sync-other-customer", AppUser.Role.USER);
        shipper = account("sync-shipper", AppUser.Role.SHIPPER); otherShipper = account("sync-other-shipper", AppUser.Role.SHIPPER);
        manager = account("sync-manager", AppUser.Role.MANAGER); admin = account("sync-admin", AppUser.Role.ADMIN);
        managerAccount = internal(manager); adminAccount = internal(admin);
        order = new Order(); order.setTrackingCode("UTESYNC01"); order.setSender(customer); order.setShipper(shipper);
        order.setSenderName("Người gửi thử nghiệm"); order.setReceiverName("Người nhận thử nghiệm");
        order.setSenderAddress("Địa chỉ lấy hàng thử nghiệm"); order.setReceiverAddress("Địa chỉ giao hàng thử nghiệm");
        order.setReceiverPhone("0901234567"); order.setWeightKg(1.0); order.setShippingFee(new BigDecimal("18000"));
        order.setStatus(Order.OrderStatus.PENDING_PICKUP); order.setCreatedAt(NOW.minusDays(1)); order.setUpdatedAt(NOW.minusHours(1));
        orders.saveAndFlush(order);
        payment = paymentService.createForOrder(order, OrderPayment.PaymentMethod.COD, null, BigDecimal.ZERO); payments.flush();
    }

    @ParameterizedTest @EnumSource(value = OrderPayment.PaymentMethod.class, names = {"MOMO", "VNPAY"})
    void neitherManagerNorAdminCanAssignAnUnpaidWalletOrder(OrderPayment.PaymentMethod method) {
        payment.setMethod(method); payments.flush();
        for (var actor : List.of(manager, admin)) {
            assertThrows(IllegalArgumentException.class, () -> assign(actor));
        }
        assertEquals(shipper.getId(), order.getShipper().getId());
        assertEquals(0, history.count());
        assertEquals(0, events.stream(OrderStatusChangedEvent.class).count());
    }

    @ParameterizedTest @EnumSource(value = Order.OrderStatus.class, names = {"PICKED_UP", "IN_TRANSIT", "DELIVERED"})
    void shipperCannotBypassPrepaymentThroughDirectStatusCalls(Order.OrderStatus next) {
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); payments.flush();
        var previous = next == Order.OrderStatus.PICKED_UP ? Order.OrderStatus.PENDING_PICKUP
                : next == Order.OrderStatus.IN_TRANSIT ? Order.OrderStatus.PICKED_UP : Order.OrderStatus.IN_TRANSIT;
        order.setStatus(previous); orders.flush();
        var error = assertThrows(IllegalArgumentException.class,
                () -> tracking.updateStatus(order.getId(), shipper.getUsername(), next));
        assertTrue(error.getMessage().contains("ví phải trả trước"));
        assertEquals(previous, order.getStatus()); assertEquals(OrderPayment.PaymentStatus.UNPAID, payment.getStatus());
        assertEquals(0, history.count());
    }

    @Test void missingPaymentBlocksDispatchAndPickupInsteadOfTreatingItAsCod() {
        payments.delete(payment); payments.flush();
        assertThrows(IllegalArgumentException.class, () -> assign(manager));
        assertThrows(IllegalArgumentException.class,
                () -> tracking.updateStatus(order.getId(), shipper.getUsername(), Order.OrderStatus.PICKED_UP));
    }

    @ParameterizedTest @EnumSource(value = OrderPayment.PaymentMethod.class, names = {"MOMO"})
    void customerPaymentUnlocksManagerDispatchAndShipperPickup(OrderPayment.PaymentMethod method) {
        payment.setMethod(method); payments.flush();
        paymentService.confirmWalletPayment(order.getId(), customer.getUsername());
        assertEquals(OrderPayment.PaymentStatus.PAID, payment.getStatus());
        assertNotNull(payment.getPaidAt()); assertTrue(payment.getTransactionRef().startsWith("SIM"));
        assertEquals(1, events.stream(OrderStatusChangedEvent.class).count());
        assign(manager);
        tracking.updateStatus(order.getId(), otherShipper.getUsername(), Order.OrderStatus.PICKED_UP);
        assertEquals(Order.OrderStatus.PICKED_UP, order.getStatus());
        assertEquals(1, events.stream(ShipperNotificationEvent.class).count());
        assertEquals(3, events.stream(OrderStatusChangedEvent.class).count());
    }

    @Test void paymentCannotBeRepeatedOrPaidByAnotherCustomer() {
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); payments.flush();
        assertThrows(IllegalStateException.class, () -> paymentService.confirmWalletPayment(order.getId(), otherCustomer.getUsername()));
        paymentService.confirmWalletPayment(order.getId(), customer.getUsername());
        String reference = payment.getTransactionRef();
        assertThrows(IllegalStateException.class, () -> paymentService.confirmWalletPayment(order.getId(), customer.getUsername()));
        assertEquals(reference, payment.getTransactionRef());
        assertEquals(1, events.stream(OrderStatusChangedEvent.class).count());
    }

    @ParameterizedTest @EnumSource(value = Order.OrderStatus.class, names = {"CANCELLED", "RETURNED", "DELIVERED"})
    void closedOrderCannotTakeWalletPayment(Order.OrderStatus closed) {
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); order.setStatus(closed); payments.flush(); orders.flush();
        assertThrows(IllegalStateException.class, () -> paymentService.confirmWalletPayment(order.getId(), customer.getUsername()));
        assertEquals(OrderPayment.PaymentStatus.UNPAID, payment.getStatus());
        assertEquals(0, events.stream(OrderStatusChangedEvent.class).count());
    }

    @Test void managerReturnUpdatesCustomerAndShipperStatisticsWithoutChangingTheHistoryActor() throws Exception {
        order.setStatus(Order.OrderStatus.FAILED); orders.flush();
        long id = cases.request(manager.getUsername(), request(OperationsCase.Kind.RETURN, order.getId(), order.getRevision(), null), null);
        var record = cases.view(manager.getUsername(), id);
        assertEquals(OperationsCase.Status.APPROVED, record.getStatus()); // chuyển hoàn có hiệu lực ngay khi Manager lập
        cases.startReturn(manager.getUsername(), id, record.getRevision(), "Bắt đầu bàn giao cho người gửi");
        cases.complete(manager.getUsername(), id, record.getRevision(), "Biên nhận người gửi TEST-01", image());
        assertEquals(Order.OrderStatus.RETURNED, order.getStatus());
        var mails = events.stream(SenderOrderUpdateEvent.class).toList();
        assertEquals(List.of(SenderOrderUpdateEvent.Kind.RETURN_STARTED, SenderOrderUpdateEvent.Kind.RETURNED),
                mails.stream().map(SenderOrderUpdateEvent::kind).toList());
        assertEquals(customer.getEmail(), mails.get(1).email());
        assertEquals(3, events.stream(OrderStatusChangedEvent.class).count()); // lập (có hiệu lực ngay), bắt đầu hoàn, hoàn tất
        assertEquals(manager.getId(), history.findByOrder(order, org.springframework.data.domain.PageRequest.of(0, 20)).getContent().get(0).getActor().getId());
        var summary = statistics.getStatistics(shipper.getUsername(), ShipperStatisticsPeriod.DAY);
        assertEquals(1, summary.totalOrders()); assertEquals(1, summary.days().get(0).returned());
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/xu-ly").with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Đã hoàn tất")))
                .andExpect(content().string(not(containsString("Biên nhận người gửi TEST-01"))));
    }

    @Test void shortageBalanceStaysVisibleUntilAdminAcceptsTheSupplement() throws Exception {
        var settlement = confirmedSettlement(10000);
        assertMoney("8000", progress.findShipperBalance(shipper.getUsername()).outstanding());
        long id = cases.request(manager.getUsername(), request(OperationsCase.Kind.CASH_SUPPLEMENT, settlement.getId(), settlement.getRevision(), new BigDecimal("8000")), image());
        var record = cases.view(admin.getUsername(), id);
        assertMoney("8000", progress.findSettlementBalance(settlement.getId(), shipper.getUsername()).outstanding());
        assertThrows(AccessDeniedException.class,
                () -> cases.review(manager.getUsername(), id, new OperationsReviewRequest(record.getRevision(), true, "Manager không được duyệt tiền")));
        cases.review(admin.getUsername(), id, new OperationsReviewRequest(record.getRevision(), true, "Đã kiểm tra chứng từ thu bổ sung"));
        assertMoney("0", progress.findShipperBalance(shipper.getUsername()).outstanding());
        assertMoney("0", progress.findSettlementBalance(settlement.getId(), shipper.getUsername()).outstanding());
        assertEquals(new BigDecimal("10000"), settlement.getDeclaredAmount());
        mvc.perform(get("/shipper/doi-soat/" + settlement.getId()).with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Chênh lệch sau khi Manager xác nhận")))
                .andExpect(content().string(containsString("Đã duyệt")));
    }

    @Test void approvedRefundRemainsDueUntilAdminRecordsActualPaymentReceipt() throws Exception {
        var settlement = confirmedSettlement(20000);
        long id = cases.request(manager.getUsername(), request(OperationsCase.Kind.CASH_REFUND, settlement.getId(), settlement.getRevision(), new BigDecimal("2000")), image());
        var record = cases.view(admin.getUsername(), id);
        cases.review(admin.getUsername(), id, new OperationsReviewRequest(record.getRevision(), true, "Duyệt hoàn phần nộp thừa"));
        assertMoney("2000", progress.findShipperBalance(shipper.getUsername()).refundDue());
        assertMoney("2000", progress.findSettlementBalance(settlement.getId(), shipper.getUsername()).refundDue());
        assertThrows(AccessDeniedException.class, () -> cases.complete(manager.getUsername(), id, record.getRevision(), "REFUND-TEST-01", image()));
        cases.complete(admin.getUsername(), id, record.getRevision(), "REFUND-TEST-01", image());
        assertMoney("0", progress.findShipperBalance(shipper.getUsername()).refundDue());
        assertMoney("0", progress.findSettlementBalance(settlement.getId(), shipper.getUsername()).refundDue());
        assertEquals(OperationsCase.Status.COMPLETED, progress.findSettlementProgress(settlement.getId(), shipper.getUsername(), 0).getContent().get(0).status());
        mvc.perform(get("/shipper/doi-soat").with(user(shipper.getUsername()).roles("SHIPPER")))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Không có đơn COD chờ lập phiếu mới")));
    }

    @Test void customerAndShipperCannotReadOtherPeoplesOperationsOrInternalCaseFiles() throws Exception {
        var settlement = confirmedSettlement(10000);
        assertThrows(IllegalArgumentException.class, () -> progress.findSettlementBalance(settlement.getId(), otherShipper.getUsername()));
        assertThrows(AccessDeniedException.class, () -> progress.findSettlementProgress(settlement.getId(), customer.getUsername(), 0));
        assertThrows(AccessDeniedException.class, () -> progress.findOrderProgress(order.getId(), otherCustomer.getUsername(), 0));
        assertThrows(AccessDeniedException.class, () -> progress.findOrderProgress(order.getId(), otherShipper.getUsername(), 0));
        assertThrows(IllegalArgumentException.class, () -> progress.findOrderProgress(order.getId(), customer.getUsername(), -1));
        assertThrows(IllegalArgumentException.class, () -> progress.findSettlementBalance(-1L, shipper.getUsername()));
        mvc.perform(get("/manager/ho-so-van-hanh").with(user(customer.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/admin/tai-khoan").with(authentication(auth(managerAccount)))).andExpect(status().isForbidden());
    }

    @Test void customerCancellationPublishesStatusAndCannotBeFollowedByPayment() {
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); payments.flush();
        orderService.cancelOrder(order.getId(), customer.getUsername());
        assertEquals("CANCELLED", events.stream(OrderStatusChangedEvent.class).toList().get(0).status());
        assertThrows(IllegalStateException.class, () -> paymentService.confirmWalletPayment(order.getId(), customer.getUsername()));
    }

    @Test void customerRealtimeTemplatesAndRoleAndCsrfGuardsArePresent() throws Exception {
        for (String path : List.of("/nguoi-dung/don-hang", "/nguoi-dung/don-hang/" + order.getId(),
                "/nguoi-dung/don-hang/" + order.getId() + "/xu-ly", "/nguoi-dung/don-hang/" + order.getId() + "/hanh-trinh")) {
            mvc.perform(get(path).with(user(customer.getUsername()).roles("USER"))).andExpect(status().isOk())
                    .andExpect(content().string(containsString("data-tracking-code=\"UTESYNC01\"")))
                    .andExpect(content().string(containsString("/js/tracking-realtime.js")))
                    .andExpect(content().string(not(containsString("/js/shipper-notifications.js"))));
        }
        mvc.perform(get("/nguoi-dung/don-hang").with(user(shipper.getUsername()).roles("SHIPPER"))).andExpect(status().isForbidden());
        mvc.perform(get("/shipper/trang-chu").with(user(customer.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        String cancelPath = "/nguoi-dung/don-hang/" + order.getId() + "/huy";
        mvc.perform(post(cancelPath).with(user(customer.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        assertEquals(Order.OrderStatus.PENDING_PICKUP, order.getStatus());
    }

    @Test void progressPaginationDoesNotLeakInternalReasonsAndRejectsInvalidPages() throws Exception {
        order.setStatus(Order.OrderStatus.FAILED); orders.flush();
        // Giao lại vượt giới hạn vẫn chờ Admin duyệt (chuyển hoàn giờ có hiệu lực ngay nên không dùng để tạo nhiều hồ sơ bị từ chối).
        for (int index = 0; index < 3; index++) history.save(new OrderStatusHistory(order, shipper,
                Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.FAILED, DeliveryFailureReason.CUSTOMER_ABSENT,
                "Giao không thành công", null, NOW.minusHours(4 - index)));
        history.flush();
        for (int index = 0; index < 21; index++) {
            long id = cases.request(manager.getUsername(), new OperationsCaseRequest(OperationsCase.Kind.RETRY, order.getId(),
                    order.getRevision(), null, NOW.plusHours(1), "Đề nghị giao vượt giới hạn", "", UUID.randomUUID().toString()), null);
            var record = cases.view(admin.getUsername(), id);
            cases.review(admin.getUsername(), id, new OperationsReviewRequest(record.getRevision(), false, "INTERNAL-ONLY-NOTE-" + index));
        }
        var first = progress.findOrderProgress(order.getId(), customer.getUsername(), 0);
        assertEquals(21, first.getTotalElements()); assertEquals(20, first.getContent().size()); assertTrue(first.hasNext());
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId() + "/xu-ly").param("page", "1")
                .with(user(customer.getUsername()).roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Trang 2/2")))
                .andExpect(content().string(not(containsString("INTERNAL-ONLY-NOTE"))));
        assertThrows(IllegalArgumentException.class, () -> progress.findOrderProgress(order.getId(), customer.getUsername(), 1000000));
    }

    @Test void walletFormsFollowServerRulesAndRequireCsrf() throws Exception {
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); payments.flush();
        String path = "/nguoi-dung/thanh-toan/" + order.getId();
        mvc.perform(post(path + "/xac-nhan").with(user(customer.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        assertEquals(OrderPayment.PaymentStatus.UNPAID, payment.getStatus());
        mvc.perform(post(path + "/xac-nhan").with(user(customer.getUsername()).roles("USER")).with(csrf()))
                .andExpect(status().isNotFound());
        assertEquals(OrderPayment.PaymentStatus.UNPAID, payment.getStatus());
        order.setStatus(Order.OrderStatus.DELIVERED); payment.setStatus(OrderPayment.PaymentStatus.UNPAID); orders.flush(); payments.flush();
        mvc.perform(get(path).with(user(customer.getUsername()).roles("USER"))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Xác nhận đã thanh toán"))));
    }

    @Test void websocketHandshakeRejectsCrossOriginPages() throws Exception {
        mvc.perform(get("/ws-tracking/info").header("Origin", "https://untrusted.invalid")
                .with(user(customer.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/ws-tracking/info").header("Origin", "http://localhost")
                .with(user(customer.getUsername()).roles("USER"))).andExpect(status().isOk());
    }

    @Test void managerCannotRejectExtraAttemptToBypassAdminReview() {
        order.setStatus(Order.OrderStatus.FAILED); orders.flush();
        for (int index = 0; index < 3; index++) history.save(new OrderStatusHistory(order, shipper,
                Order.OrderStatus.IN_TRANSIT, Order.OrderStatus.FAILED, DeliveryFailureReason.CUSTOMER_ABSENT,
                "Giao không thành công", null, NOW.minusHours(4 - index)));
        history.flush();
        long id = cases.request(manager.getUsername(), new OperationsCaseRequest(OperationsCase.Kind.RETRY, order.getId(),
                order.getRevision(), null, NOW.plusHours(1), "Đề nghị giao vượt giới hạn", "", UUID.randomUUID().toString()), null);
        var record = cases.view(manager.getUsername(), id);
        assertThrows(AccessDeniedException.class, () -> cases.review(manager.getUsername(), id,
                new OperationsReviewRequest(record.getRevision(), false, "Không được tự xử lý ngoại lệ")));
        assertEquals(OperationsCase.Status.REQUESTED, record.getStatus());
    }

    @Test void legacyUnpaidOrdersAppearForManagerAndDisappearOnlyAfterCustomerPayment() throws Exception {
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); order.setStatus(Order.OrderStatus.IN_TRANSIT); orders.flush(); payments.flush();
        assertEquals(List.of(order.getId()), overview.alerts(manager.getUsername(), OperationsOverviewService.Alert.PAYMENT_ISSUE, 0)
                .map(Order::getId).getContent());
        mvc.perform(get("/manager/canh-bao").param("alert", "PAYMENT_ISSUE").with(authentication(auth(managerAccount))))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Không tự sửa SQL thành PAID")))
                .andExpect(content().string(containsString("UTESYNC01")));
        paymentService.confirmWalletPayment(order.getId(), customer.getUsername());
        assertTrue(overview.alerts(manager.getUsername(), OperationsOverviewService.Alert.PAYMENT_ISSUE, 0).isEmpty());
    }

    private AppUser account(String username, AppUser.Role role) {
        var value = new AppUser(username, "test-only-hash", username + "@test.invalid");
        value.setEnabled(true); value.setRole(role); value.setFullName("Tài khoản kiểm thử");
        return users.saveAndFlush(value);
    }
    private StaffAccount internal(AppUser user) {
        var value = new StaffAccount(); value.setUser(user); value.setIssuedBy("test-only"); value.setActive(true); value.setPasswordChangeRequired(false);
        return staff.saveAndFlush(value);
    }
    private UsernamePasswordAuthenticationToken auth(StaffAccount account) {
        var principal = new StaffPrincipal(account);
        return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }
    private void assign(AppUser actor) {
        dispatch.assign(order.getTrackingCode(), otherShipper.getId(), shipper.getId(), order.getUpdatedAt(), "Phân công lại theo tuyến", actor.getUsername());
    }
    private CodSettlement confirmedSettlement(int amount) {
        var value = settlements.saveAndFlush(new CodSettlement(shipper, 1, new BigDecimal("18000"), BigDecimal.valueOf(amount), "Phiếu thử nghiệm", NOW));
        value.review(manager, true, "Xác nhận số tiền thực nhận", NOW); return settlements.saveAndFlush(value);
    }
    private OperationsCaseRequest request(OperationsCase.Kind kind, Long target, long revision, BigDecimal amount) {
        return new OperationsCaseRequest(kind, target, revision, amount, null, "Lý do kiểm thử hồ sơ", "RECEIPT-TEST-01", UUID.randomUUID().toString());
    }
    private MockMultipartFile image() { return new MockMultipartFile("evidence", "receipt.png", "image/png", qr.generate("UTESYNC01")); }
    private void assertMoney(String expected, BigDecimal actual) { assertEquals(0, new BigDecimal(expected).compareTo(actual)); }
}
