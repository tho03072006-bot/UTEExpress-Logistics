package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.security.admin.StaffPrincipal;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.service.manager.*;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:controls-test;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.mail.order-updates.enabled=false", "app.upload.dir=target/test-controls-uploads"
})
@AutoConfigureMockMvc @ActiveProfiles("isolated-logistics") @Transactional
@Import(OperationsControlsIntegrationTest.TestClock.class)
class OperationsControlsIntegrationTest {
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 15, 10, 0);
    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static class MutableClock extends Clock {
        private Instant value = NOW.atZone(ZONE).toInstant();
        public ZoneId getZone() { return ZONE; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(value, zone); }
        public Instant instant() { return value; }
        void at(LocalDateTime time) { value = time.atZone(ZONE).toInstant(); }
    }
    @TestConfiguration static class TestClock { @Bean @Primary MutableClock controlsClock() { return new MutableClock(); } }
    @Autowired MutableClock clock;
    @Autowired EntityManager em;
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired OrderComplaintRepository complaints;
    @Autowired CodSettlementRepository settlements;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired OperationsCaseRepository caseRepository;
    @Autowired OperationsPolicyService policies;
    @Autowired OperationsCaseService cases;
    @Autowired OperationsOverviewService overview;
    @Autowired TariffVersionService schedules;
    @Autowired ShippingTariffService pricing;
    @Autowired OrderComplaintService complaintService;
    @Autowired ShipmentQrService qr;
    @Autowired TrackingService tracking;
    AppUser customer, shipper, manager, admin;
    StaffAccount managerAccount, adminAccount;
    Order order;
    @BeforeEach void seed() {
        clock.at(NOW); customer = account("customer-control", AppUser.Role.USER); shipper = account("shipper-control", AppUser.Role.SHIPPER);
        manager = account("manager-control", AppUser.Role.MANAGER); admin = account("admin-control", AppUser.Role.ADMIN);
        managerAccount = internal(manager); adminAccount = internal(admin);
        order = new Order(); order.setSender(customer); order.setShipper(shipper); order.setTrackingCode("UTECONTROL01");
        order.setSenderName("Người gửi"); order.setReceiverName("Người nhận"); order.setReceiverAddress("Địa chỉ giao");
        order.setServiceType(Order.ServiceType.STANDARD); order.setWeightKg(1.0); order.setShippingFee(new BigDecimal("18000"));
        order.setStatus(Order.OrderStatus.FAILED); order.setCreatedAt(NOW.minusDays(6)); order.setUpdatedAt(NOW.minusHours(2)); orders.saveAndFlush(order);
    }
    AppUser account(String username, AppUser.Role role) {
        var user = new AppUser(username, "test-only-hash", username + "@example.com"); user.setRole(role); user.setEnabled(true);
        user.setFullName("Tài khoản kiểm thử"); return users.saveAndFlush(user);
    }
    StaffAccount internal(AppUser user) {
        var record = new StaffAccount(); record.setUser(user); record.setActive(true); record.setPasswordChangeRequired(false);
        record.setIssuedBy("test-only"); return staff.saveAndFlush(record);
    }
    UsernamePasswordAuthenticationToken auth(StaffAccount account) {
        var principal = new StaffPrincipal(account); return new UsernamePasswordAuthenticationToken(principal, principal.getPassword(), principal.getAuthorities());
    }
    MockMultipartFile image() { return new MockMultipartFile("evidence", "receipt.png", "image/png", qr.generate("UTECONTROL01")); }
    OperationsCaseRequest request(OperationsCase.Kind kind, long target, long revision, BigDecimal amount, LocalDateTime next) {
        return new OperationsCaseRequest(kind, target, revision, amount, next, "Lý do kiểm thử hồ sơ", "RECEIPT-TEST-01", UUID.randomUUID().toString());
    }
    Long newReturn() { return cases.request(manager.getUsername(), request(OperationsCase.Kind.RETURN, order.getId(), order.getRevision(), null, null), null); }
    OrderPayment paid() {
        var payment = new OrderPayment(); payment.setOrder(order); payment.setAmount(new BigDecimal("18000"));
        payment.setMethod(OrderPayment.PaymentMethod.COD); payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.setPaidAt(NOW.minusHours(1)); return payments.saveAndFlush(payment);
    }
    @Test void allNewPagesRenderAndManagerCannotEditPolicy() throws Exception {
        for (String path : List.of("/manager/canh-bao", "/manager/hieu-suat-shipper", "/manager/ho-so-van-hanh", "/manager/lich-cuoc"))
            mvc.perform(get(path).with(authentication(auth(managerAccount)))).andExpect(status().isOk());
        mvc.perform(get("/admin/chinh-sach-van-hanh").with(authentication(auth(adminAccount)))).andExpect(status().isOk());
        mvc.perform(get("/admin/chinh-sach-van-hanh").with(authentication(auth(managerAccount)))).andExpect(status().isForbidden());
        mvc.perform(get("/manager/ho-so-van-hanh/tao").param("kind","RETURN").param("targetId",order.getId().toString())
                .with(authentication(auth(managerAccount)))).andExpect(status().isOk());
        mvc.perform(get("/manager/ho-so-van-hanh").with(user(customer.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/manager/ho-so-van-hanh").with(authentication(auth(managerAccount)))).andExpect(status().isForbidden());
    }
    @Test void returnTakesEffectOnCreateThenNeedsStartAndEvidenceAndCannotCompleteTwice() throws Exception {
        Long id = newReturn(); var record = cases.view(manager.getUsername(), id);
        mvc.perform(get("/manager/ho-so-van-hanh/"+id).with(authentication(auth(managerAccount)))).andExpect(status().isOk());
        assertEquals(OperationsCase.Status.APPROVED, record.getStatus()); // Manager lập là có hiệu lực, không cần duyệt
        record = cases.view(manager.getUsername(),id); cases.startReturn(manager.getUsername(),id,record.getRevision(),"Bắt đầu bàn giao hoàn");
        record = cases.view(manager.getUsername(),id); cases.complete(manager.getUsername(),id,record.getRevision(),"Nguyễn Văn Người Gửi",image());
        assertEquals(Order.OrderStatus.RETURNED, order.getStatus()); assertTrue(record.isHasEvidence());
        assertEquals(OperationsCase.Status.COMPLETED,record.getStatus());
        long completedRevision = record.getRevision();
        assertThrows(StaffConflictException.class,()->cases.complete(manager.getUsername(),id,completedRevision,"Người gửi nhận lại",image()));
    }
    @Test void pendingReturnBlocksRetryAndShipperCannotMarkReturnedDirectly() {
        newReturn(); assertFalse(cases.canRetry(order));
        assertThrows(IllegalArgumentException.class,()->cases.beforeStatusChange(order,shipper.getUsername(),Order.OrderStatus.RETURNED));
    }
    @Test void duplicateReturnAndStaleTargetAreRejected() {
        long original = order.getRevision(); newReturn();
        assertThrows(StaffConflictException.class,()->cases.request(manager.getUsername(),request(OperationsCase.Kind.RETURN,order.getId(),original,null,null),null));
    }
    @Test void retryUsesScheduleAndOneExtraApprovalOnly() {
        for (int i=0;i<3;i++) history.save(new OrderStatusHistory(order,shipper,Order.OrderStatus.IN_TRANSIT,Order.OrderStatus.FAILED,
                DeliveryFailureReason.CUSTOMER_ABSENT,"Khách không có mặt",null,NOW.minusHours(4-i)));
        history.flush(); assertFalse(cases.canRetry(order));
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.RETRY,order.getId(),order.getRevision(),null,NOW.plusHours(1)),null);
        var record=cases.view(admin.getUsername(),id);
        cases.review(admin.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Duyệt ngoại lệ thêm một lần"));
        assertFalse(cases.canRetry(order)); clock.at(NOW.plusHours(2)); assertTrue(cases.canRetry(order));
        var retryPayment = new OrderPayment(); retryPayment.setOrder(order); retryPayment.setAmount(new BigDecimal("18000"));
        retryPayment.setMethod(OrderPayment.PaymentMethod.COD); retryPayment.setStatus(OrderPayment.PaymentStatus.UNPAID); payments.saveAndFlush(retryPayment);
        tracking.updateStatus(order.getId(),shipper.getUsername(),Order.OrderStatus.IN_TRANSIT);
        assertEquals(OperationsCase.Status.COMPLETED,record.getStatus());
    }
    @Test void managerCannotApproveRetryBeyondPolicy() {
        for(int i=0;i<3;i++) history.save(new OrderStatusHistory(order,shipper,Order.OrderStatus.IN_TRANSIT,Order.OrderStatus.FAILED,
                DeliveryFailureReason.CUSTOMER_ABSENT,"Khách không có mặt",null,NOW.minusHours(4-i)));
        history.flush(); Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.RETRY,order.getId(),order.getRevision(),null,NOW.plusHours(1)),null);
        var record=cases.view(manager.getUsername(),id);
        assertThrows(AccessDeniedException.class,()->cases.review(manager.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Thử vượt quyền Manager")));
    }
    @Test void financialSupplementNeedsAdminAndReducesOutstandingExactly() {
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("10000"),"Nộp thiếu có ghi nhận",NOW));
        settlement.review(manager,true,"Chờ nộp bổ sung",NOW); settlements.flush();
        assertEquals(new BigDecimal("8000"),cases.outstanding(settlement));
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.CASH_SUPPLEMENT,settlement.getId(),settlement.getRevision(),new BigDecimal("8000"),null),image());
        var record=cases.view(admin.getUsername(),id); cases.review(admin.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Đã kiểm tra biên nhận"));
        assertEquals(0,cases.outstanding(settlement).signum());
        assertEquals(new BigDecimal("10000"),settlement.getDeclaredAmount());
    }
    @Test void creatorCannotApproveOwnFinancialCase() {
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("10000"),"Thiếu cước",NOW));
        settlement.review(manager,true,"Ghi nhận khoản thiếu",NOW); settlements.flush();
        Long id=cases.request(admin.getUsername(),request(OperationsCase.Kind.CASH_SUPPLEMENT,settlement.getId(),settlement.getRevision(),new BigDecimal("1000"),null),image());
        var record=cases.view(admin.getUsername(),id);
        assertThrows(IllegalArgumentException.class,()->cases.review(admin.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Tự duyệt phiếu nộp tiền")));
    }
    @Test void compensationApprovalDoesNotPretendMoneyHasBeenPaid() {
        paid(); var complaint=complaints.saveAndFlush(new OrderComplaint(order,"Yêu cầu bồi hoàn cước","Nội dung kiểm thử khiếu nại",null,NOW));
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.COMPENSATION,complaint.getId(),complaint.getRevision(),new BigDecimal("10000"),null),image());
        var record=cases.view(admin.getUsername(),id); cases.review(admin.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Đủ chứng từ bồi hoàn"));
        assertEquals(OperationsCase.Status.APPROVED,record.getStatus());
        cases.complete(admin.getUsername(),id,record.getRevision(),"EXPENSE-OUTSIDE-01",image()); assertEquals(OperationsCase.Status.COMPLETED,record.getStatus());
        assertEquals(OrderPayment.PaymentStatus.PAID,payments.findByOrder(order).orElseThrow().getStatus());
    }
    @Test void complaintKeepsHistoryAndOwnerCanClaim() {
        complaintService.create(order.getId(),customer.getUsername(),"Đề nghị kiểm tra đơn","Nội dung mô tả khiếu nại",null);
        var complaint=complaints.findByOrder(order).orElseThrow();
        complaintService.claim(complaint.getId(),manager.getUsername(),complaint.getRevision());
        complaintService.respond(complaint.getId(),manager.getUsername(),complaint.getRevision(),OrderComplaint.Status.IN_PROGRESS,"Đang kiểm tra với Shipper");
        assertEquals(3,complaintService.timeline(complaint.getId(),manager.getUsername(),0).getTotalElements());
        assertEquals(manager.getId(),complaint.getHandledBy().getId());
    }
    @Test void approvedCompensationPreventsContradictoryComplaintRejection() {
        paid(); var complaint=complaints.saveAndFlush(new OrderComplaint(order,"Đề nghị hoàn cước","Kiểm thử chống trạng thái mâu thuẫn",null,NOW));
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.COMPENSATION,complaint.getId(),complaint.getRevision(),BigDecimal.TEN,null),image());
        var record=cases.view(admin.getUsername(),id);
        cases.review(admin.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Duyệt hoàn cước có chứng từ"));
        assertThrows(IllegalArgumentException.class,()->complaintService.respond(complaint.getId(),manager.getUsername(),complaint.getRevision(),
                OrderComplaint.Status.REJECTED,"Thử từ chối sau khi đã duyệt tiền"));
    }
    @Test void scheduledPriceAppliesOnlyInItsWindowAndDoesNotRewriteOrder() {
        var old=pricing.quote(Order.ServiceType.STANDARD,1);
        schedules.schedule(admin.getUsername(),new TariffScheduleRequest(Order.ServiceType.STANDARD,new BigDecimal("30000"),new BigDecimal("5000"),NOW.plusHours(1),NOW.plusHours(3),"Thử lịch giá theo thời gian"));
        assertEquals(old,pricing.quote(Order.ServiceType.STANDARD,1)); clock.at(NOW.plusHours(2));
        assertEquals(new BigDecimal("35000"),pricing.quote(Order.ServiceType.STANDARD,1));
        clock.at(NOW.plusHours(3)); assertEquals(old,pricing.quote(Order.ServiceType.STANDARD,1));
        assertEquals(new BigDecimal("18000"),order.getShippingFee());
    }
    @Test void overlappingPriceAndManagerScheduleAreRejected() {
        var form=new TariffScheduleRequest(Order.ServiceType.STANDARD,new BigDecimal("20000"),new BigDecimal("4000"),NOW.plusHours(1),NOW.plusHours(3),"Lập lịch cước kiểm thử");
        schedules.schedule(admin.getUsername(),form);
        assertThrows(StaffConflictException.class,()->schedules.schedule(admin.getUsername(),form));
        assertThrows(AccessDeniedException.class,()->schedules.schedule(manager.getUsername(),form));
    }
    @Test void priceScreenShowsLifecycleAndNeverOffersCancellationAfterStart() throws Exception {
        schedules.schedule(admin.getUsername(),new TariffScheduleRequest(Order.ServiceType.STANDARD,new BigDecimal("20000"),new BigDecimal("4000"),
                NOW.plusHours(1),NOW.plusHours(3),"Kiểm tra hiển thị vòng đời lịch cước"));
        mvc.perform(get("/admin/lich-cuoc").with(authentication(auth(adminAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Sắp áp dụng")));
        clock.at(NOW.plusHours(2));
        mvc.perform(get("/admin/lich-cuoc").with(authentication(auth(adminAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Đang áp dụng")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(">Hủy</button>"))));
        clock.at(NOW.plusHours(3));
        mvc.perform(get("/manager/lich-cuoc").with(authentication(auth(managerAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Đã kết thúc")));
    }
    @Test void failedOrderWithoutAppointmentNeedsManagerCaseInsteadOfUnscheduledRetry() {
        assertFalse(cases.canRetry(order));
        assertFalse(overview.alerts(manager.getUsername(),OperationsOverviewService.Alert.RETRY_DUE,0).stream().anyMatch(o -> o.getId().equals(order.getId())));
    }
    @Test void policyServerValidationAndStaleForm() {
        var p=policies.current();
        var form=new OperationsPolicyRequest(p.getRevision(),24,100,40,4,4,24,168,"Điều chỉnh chính sách thử");
        policies.update(admin.getUsername(),form); assertEquals(4,policies.current().getMaxDeliveryAttempts());
        assertThrows(StaffConflictException.class,()->policies.update(admin.getUsername(),form));
    }
    @Test void alertsExcludeDeliveredAndCannotBeClearedByNoteUpdate() {
        assertTrue(overview.alerts(manager.getUsername(),OperationsOverviewService.Alert.LATE_DELIVERY,0).getContent().contains(order));
        order.setUpdatedAt(NOW);orders.flush();
        assertTrue(overview.alerts(manager.getUsername(),OperationsOverviewService.Alert.LATE_DELIVERY,0).getContent().contains(order));
        order.setStatus(Order.OrderStatus.DELIVERED);orders.flush();
        assertFalse(overview.alerts(manager.getUsername(),OperationsOverviewService.Alert.LATE_DELIVERY,0).getContent().contains(order));
    }
    @Test void overpaidFeesNeedApprovalAndReceiptBeforeBeingRecordedAsReturned() throws Exception {
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("20000"),"Nộp thừa cần xử lý",NOW));
        settlement.review(manager,true,"Xác nhận số thực nhận",NOW); settlements.flush();
        assertEquals(new BigDecimal("2000"),cases.refundable(settlement));
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.CASH_REFUND,settlement.getId(),settlement.getRevision(),new BigDecimal("2000"),null),image());
        var record=cases.view(admin.getUsername(),id);
        cases.review(admin.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Duyệt hoàn phần tiền thừa"));
        assertEquals(OperationsCase.Status.APPROVED,record.getStatus());
        mvc.perform(get("/manager/ho-so-van-hanh/"+id).with(authentication(auth(adminAccount)))).andExpect(status().isOk());
        cases.complete(admin.getUsername(),id,record.getRevision(),"REFUND-RECEIPT-01",image());
        assertEquals(OperationsCase.Status.COMPLETED,record.getStatus());
        assertEquals(new BigDecimal("20000"),settlement.getDeclaredAmount());
    }
    @Test void managerCannotApproveFinancialCaseEvenByPostingDirectly() throws Exception {
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("10000"),"Nộp thiếu có ghi nhận",NOW));
        settlement.review(manager,true,"Đã nhận phần tiền nộp",NOW); settlements.flush();
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.CASH_SUPPLEMENT,settlement.getId(),settlement.getRevision(),BigDecimal.ONE,null),image());
        var record=cases.view(manager.getUsername(),id);
        mvc.perform(post("/manager/ho-so-van-hanh/"+id+"/duyet").with(authentication(auth(managerAccount))).with(csrf())
                .param("revision",Long.toString(record.getRevision())).param("approve","true").param("reason","Thử gửi thẳng request"))
                .andExpect(status().isForbidden());
    }
    @Test void concurrentFormTokenCannotCreateTwoFinancialRequests() {
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("10000"),"Nộp thiếu có ghi nhận",NOW));
        settlement.review(manager,true,"Đã nhận phần tiền nộp",NOW); settlements.flush();
        var form=request(OperationsCase.Kind.CASH_SUPPLEMENT,settlement.getId(),settlement.getRevision(),new BigDecimal("1000"),null);
        cases.request(manager.getUsername(),form,image());
        assertEquals(1,caseRepository.count());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->cases.request(manager.getUsername(),form,image()));
    }
    @Test void missingMoneyEvidenceAndOverbookingRejectBeforePersistence() {
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("10000"),"Nộp thiếu có ghi nhận",NOW));
        settlement.review(manager,true,"Đã nhận phần tiền nộp",NOW); settlements.flush();
        assertThrows(IllegalArgumentException.class,()->cases.request(manager.getUsername(),request(OperationsCase.Kind.CASH_SUPPLEMENT,
                settlement.getId(),settlement.getRevision(),new BigDecimal("8001"),null),image()));
        assertThrows(IllegalArgumentException.class,()->cases.request(manager.getUsername(),request(OperationsCase.Kind.CASH_SUPPLEMENT,
                settlement.getId(),settlement.getRevision(),BigDecimal.ONE,null),null));
    }
    @Test void refundIsLimitedToPaidShippingFeeNotMerchandiseValue() {
        paid(); var complaint=complaints.saveAndFlush(new OrderComplaint(order,"Yêu cầu bồi hoàn cước","Nội dung kiểm thử khiếu nại",null,NOW));
        assertThrows(IllegalArgumentException.class,()->cases.request(manager.getUsername(),request(OperationsCase.Kind.COMPENSATION,
                complaint.getId(),complaint.getRevision(),new BigDecimal("18001"),null),image()));
    }
    @Test void unpaidOrderHasNoCompensationOption() throws Exception {
        var complaint=complaints.saveAndFlush(new OrderComplaint(order,"Yêu cầu bồi hoàn cước","Đơn chưa thanh toán",null,NOW));
        var error=assertThrows(IllegalArgumentException.class,()->cases.prepare(manager.getUsername(),OperationsCase.Kind.COMPENSATION,complaint.getId()));
        assertTrue(error.getMessage().contains("chưa thanh toán"));
        mvc.perform(get("/manager/khieu-nai/"+complaint.getId()).with(authentication(auth(managerAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Đề xuất bồi hoàn cước"))));
        paid();
        assertEquals(OperationsCase.Kind.COMPENSATION, cases.prepare(manager.getUsername(),OperationsCase.Kind.COMPENSATION,complaint.getId()).kind());
    }
    @Test void invalidUploadAndIncompleteReturnDoNotFinishOrder() {
        Long id=newReturn(); var record=cases.view(manager.getUsername(),id);
        assertThrows(IllegalArgumentException.class,()->cases.complete(manager.getUsername(),id,record.getRevision(),"Tên người gửi nhận lại",null));
        assertThrows(IllegalArgumentException.class,()->cases.complete(manager.getUsername(),id,record.getRevision(),"Tên người gửi nhận lại",
                new MockMultipartFile("evidence","fake.png","image/png","not-an-image".getBytes())));
        assertEquals(OperationsCase.Status.APPROVED, record.getStatus()); assertEquals(Order.OrderStatus.FAILED, order.getStatus());
    }
    @Test void overdueCashRemainsVisibleUntilSupplementIsActuallyApproved() {
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("10000"),"Nộp thiếu có ghi nhận",NOW.minusDays(8)));
        settlement.review(manager,true,"Đã nhận phần tiền nộp",NOW.minusDays(8)); settlements.flush();
        assertTrue(overview.lateCash(manager.getUsername(),0).getContent().contains(settlement));
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.CASH_SUPPLEMENT,settlement.getId(),settlement.getRevision(),new BigDecimal("8000"),null),image());
        assertTrue(overview.lateCash(manager.getUsername(),0).getContent().contains(settlement));
        var record=cases.view(admin.getUsername(),id); cases.review(admin.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Đã kiểm tra chứng từ nộp đủ"));
        assertFalse(overview.lateCash(manager.getUsername(),0).getContent().contains(settlement));
    }
    @Test void approvedRetryScheduleIsSharedByShipperQueueAndManagerAlerts() {
        history.saveAndFlush(new OrderStatusHistory(order,shipper,Order.OrderStatus.IN_TRANSIT,Order.OrderStatus.FAILED,
                DeliveryFailureReason.CUSTOMER_ABSENT,"Khách không có mặt",NOW.minusHours(1),NOW.minusHours(2)));
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.RETRY,order.getId(),order.getRevision(),null,NOW.plusHours(1)),null);
        assertEquals(OperationsCase.Status.APPROVED, cases.view(manager.getUsername(),id).getStatus()); // trong giới hạn: có hiệu lực ngay
        assertEquals(0,orders.countDueRetries(shipper,NOW,policies.current().getMaxDeliveryAttempts()));
        assertTrue(overview.alerts(manager.getUsername(),OperationsOverviewService.Alert.RETRY_DUE,0).isEmpty());
        clock.at(NOW.plusHours(2));
        assertEquals(1,orders.countDueRetries(shipper,NOW.plusHours(2),policies.current().getMaxDeliveryAttempts()));
        assertFalse(overview.alerts(manager.getUsername(),OperationsOverviewService.Alert.RETRY_DUE,0).isEmpty());
    }
    @Test void adminPolicyAndTariffFormsBindRecordsAndValidateServerSide() throws Exception {
        var policy=policies.current();
        mvc.perform(post("/admin/chinh-sach-van-hanh").with(authentication(auth(adminAccount))).with(csrf())
                .param("revision",Long.toString(policy.getRevision())).param("pickupHours","24").param("standardHours","120")
                .param("expressHours","48").param("superExpressHours","4").param("maxDeliveryAttempts","4")
                .param("complaintResponseHours","24").param("complaintResolveHours","168").param("reason","Đổi chính sách qua form"))
                .andExpect(redirectedUrl("/admin/chinh-sach-van-hanh"));
        assertEquals(4,policies.current().getMaxDeliveryAttempts());
        mvc.perform(post("/admin/lich-cuoc").with(authentication(auth(adminAccount))).with(csrf())
                .param("serviceType","STANDARD").param("baseFee","30000").param("feePerKg","5000")
                .param("startsAt",NOW.plusHours(1).toString()).param("endsAt",NOW.plusHours(3).toString()).param("reason","Lập lịch cước qua form"))
                .andExpect(redirectedUrl("/admin/lich-cuoc"));
        assertEquals(1,schedules.list(admin.getUsername(),0).getTotalElements());
    }
    @Test void invalidFormDoesNotCrashOnRedirectOrCreateCase() throws Exception {
        var result=mvc.perform(post("/manager/ho-so-van-hanh").with(authentication(auth(managerAccount))).with(csrf())
                .param("kind","UNKNOWN").param("targetId","abc").param("amount","NaN"))
                .andExpect(redirectedUrl("/manager/ho-so-van-hanh")).andExpect(flash().attributeExists("error")).andReturn();
        mvc.perform(get("/manager/ho-so-van-hanh").with(authentication(auth(managerAccount))).flashAttrs(result.getFlashMap())).andExpect(status().isOk());
        assertEquals(0,caseRepository.count());
    }
    @Test void priceCannotBeCancelledAfterStartingAndBoundaryWindowsMayTouch() {
        var form=new TariffScheduleRequest(Order.ServiceType.STANDARD,new BigDecimal("20000"),new BigDecimal("4000"),NOW.plusHours(1),NOW.plusHours(3),"Lập lịch cước thứ nhất");
        schedules.schedule(admin.getUsername(),form);
        schedules.schedule(admin.getUsername(),new TariffScheduleRequest(Order.ServiceType.STANDARD,new BigDecimal("21000"),new BigDecimal("4100"),NOW.plusHours(3),NOW.plusHours(4),"Lập lịch cước liền kề"));
        var version=schedules.list(admin.getUsername(),0).getContent().stream().filter(v->v.getStartsAt().equals(NOW.plusHours(1))).findFirst().orElseThrow();
        clock.at(NOW.plusHours(2));
        assertThrows(StaffConflictException.class,()->schedules.cancel(admin.getUsername(),version.getId(),version.getRevision(),"Không sửa lịch sử đã áp dụng"));
    }
}
