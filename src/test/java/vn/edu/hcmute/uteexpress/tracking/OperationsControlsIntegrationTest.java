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
    @Test void emptyBranchCatalogueDoesNotTellAdminToAskAnotherAdminForAssignment() throws Exception {
        mvc.perform(get("/manager/canh-bao").with(authentication(auth(adminAccount)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Tài khoản chưa được gán chi nhánh"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa có chi nhánh trong danh mục")));
        mvc.perform(get("/manager/canh-bao").with(authentication(auth(managerAccount)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tài khoản chưa được gán chi nhánh")));
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
    @Test void adminCannotCreateRetryBeyondPolicyButStillCreatesReturn() {
        for(int i=0;i<3;i++) history.save(new OrderStatusHistory(order,shipper,Order.OrderStatus.IN_TRANSIT,Order.OrderStatus.FAILED,
                DeliveryFailureReason.CUSTOMER_ABSENT,"Khách không có mặt",null,NOW.minusHours(4-i)));
        history.flush();
        assertThrows(AccessDeniedException.class,()->cases.request(admin.getUsername(),request(OperationsCase.Kind.RETRY,order.getId(),order.getRevision(),null,NOW.plusHours(1)),null));
        Long id=cases.request(admin.getUsername(),request(OperationsCase.Kind.RETURN,order.getId(),order.getRevision(),null,null),null);
        assertEquals(OperationsCase.Status.APPROVED,cases.view(admin.getUsername(),id).getStatus());
    }
    @Test void supplementMustReadConfirmedStateAgainAfterLockingPreloadedSettlement() {
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("10000"),"Phiếu cần kiểm tra",NOW));
        settlement.review(manager,true,"Đã nhận tiền",NOW); settlements.flush();
        em.createNativeQuery("update cod_settlement set status='SUBMITTED', revision=revision+1 where id=:id")
                .setParameter("id",settlement.getId()).executeUpdate();
        assertThrows(StaffConflictException.class,()->cases.request(manager.getUsername(),request(OperationsCase.Kind.CASH_SUPPLEMENT,
                settlement.getId(),settlement.getRevision(),new BigDecimal("1000"),null),image()));
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
    @Test void adminCannotCreateFinancialCaseSoCreatorAndApproverDiffer() {
        // Bảng phân quyền: phiếu tiền do Manager lập, Admin duyệt; Admin không lập nên không thể tự duyệt phiếu của mình.
        var settlement=settlements.saveAndFlush(new CodSettlement(shipper,1,new BigDecimal("18000"),new BigDecimal("10000"),"Thiếu cước",NOW));
        settlement.review(manager,true,"Ghi nhận khoản thiếu",NOW); settlements.flush();
        assertThrows(AccessDeniedException.class,()->cases.prepare(admin.getUsername(),OperationsCase.Kind.CASH_SUPPLEMENT,settlement.getId()));
        assertThrows(AccessDeniedException.class,()->cases.request(admin.getUsername(),request(OperationsCase.Kind.CASH_SUPPLEMENT,settlement.getId(),settlement.getRevision(),new BigDecimal("1000"),null),image()));
        assertEquals(0,caseRepository.count());
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
    @Test void customerAndShipperSeeLatestComplaintMessageAndCanReadOlderPages() throws Exception {
        complaintService.create(order.getId(), customer.getUsername(), "Kiểm tra hội thoại dài", "Nội dung mở hồ sơ", null);
        var complaint = complaints.findByOrder(order).orElseThrow();
        for (int i = 1; i <= 51; i++) complaintService.customerMessage(complaint.getId(), customer.getUsername(), "Tin kiểm thử " + i);
        for (var viewer : List.of(customer, shipper)) {
            String prefix = viewer.getRole() == AppUser.Role.USER ? "/nguoi-dung/khieu-nai/" : "/shipper/khieu-nai/";
            mvc.perform(get(prefix + complaint.getId()).with(user(viewer.getUsername()).roles(viewer.getRole().name())))
                    .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Tin kiểm thử 51")))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(prefix + complaint.getId() + "/lich-su?page=0")));
            mvc.perform(get(prefix + complaint.getId() + "/lich-su").param("page", "0")
                    .with(user(viewer.getUsername()).roles(viewer.getRole().name())))
                    .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Tin kiểm thử 1")));
        }
    }
    @Test void complaintHistoryPagesRetainOwnershipAndValidatePage() throws Exception {
        complaintService.create(order.getId(), customer.getUsername(), "Kiểm tra quyền lịch sử", "Nội dung mở hồ sơ", null);
        var complaint = complaints.findByOrder(order).orElseThrow();
        var stranger = account("stranger-control", AppUser.Role.USER);
        mvc.perform(get("/nguoi-dung/khieu-nai/" + complaint.getId() + "/lich-su").param("page", "0")
                .with(user(stranger.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/nguoi-dung/khieu-nai/" + complaint.getId() + "/lich-su").param("page", "-1")
                .with(user(customer.getUsername()).roles("USER"))).andExpect(status().isBadRequest());
    }
    @Test void complaintUiOnlyOffersHandlingToOwnerOrAdmin() throws Exception {
        complaintService.create(order.getId(), customer.getUsername(), "Hồ sơ có người phụ trách", "Nội dung mở hồ sơ", null);
        var complaint = complaints.findByOrder(order).orElseThrow();
        complaintService.claim(complaint.getId(), manager.getUsername(), complaint.getRevision());
        var colleague = internal(account("colleague-control", AppUser.Role.MANAGER));
        String path = "/manager/khieu-nai/" + complaint.getId();
        mvc.perform(get(path).with(authentication(auth(colleague)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(path + "/phan-hoi"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(path + "/nhan-xu-ly"))));
        mvc.perform(get(path).with(authentication(auth(managerAccount)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(path + "/phan-hoi")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(path + "/nhan-xu-ly"))));
        mvc.perform(get(path).with(authentication(auth(adminAccount)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(path + "/phan-hoi")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(path + "/nhan-xu-ly")));
    }
    @Test void claimingSameComplaintTwiceDoesNotCreateRepeatedActivity() {
        complaintService.create(order.getId(), customer.getUsername(), "Không nhận xử lý lặp", "Nội dung mở hồ sơ", null);
        var complaint = complaints.findByOrder(order).orElseThrow();
        complaintService.claim(complaint.getId(), manager.getUsername(), complaint.getRevision());
        assertThrows(IllegalArgumentException.class, () -> complaintService.claim(complaint.getId(), manager.getUsername(), complaint.getRevision()));
        assertEquals(2, complaintService.timeline(complaint.getId(), manager.getUsername(), 0).getTotalElements());
    }
    @Test void inconsistentPaidSnapshotDoesNotOfferOrAllowCompensation() throws Exception {
        var payment = paid(); payment.setAmount(new BigDecimal("20000")); payments.flush();
        var complaint = complaints.saveAndFlush(new OrderComplaint(order, "Cước cần tra soát", "Số tiền không khớp cước đã chốt", null, NOW));
        mvc.perform(get("/manager/khieu-nai/" + complaint.getId()).with(authentication(auth(managerAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Đề xuất bồi thường"))));
        assertThrows(IllegalArgumentException.class, () -> cases.prepare(manager.getUsername(), OperationsCase.Kind.COMPENSATION, complaint.getId()));
    }
    @Test void missingWalletReceiptCannotCreateCompensationEvenIfPaidFlagIsSet() {
        var payment = paid(); payment.setMethod(OrderPayment.PaymentMethod.MOMO); payment.setTransactionRef(null); payments.flush();
        var complaint = complaints.saveAndFlush(new OrderComplaint(order, "Thiếu biên nhận thanh toán", "Cần tra soát giao dịch trước khi chi trả", null, NOW));
        assertThrows(IllegalArgumentException.class, () -> cases.request(manager.getUsername(),
                request(OperationsCase.Kind.COMPENSATION, complaint.getId(), complaint.getRevision(), BigDecimal.TEN, null), image()));
        assertEquals(0, caseRepository.count());
    }
    @Test void compensationApprovalRechecksPaymentReceipt() throws Exception {
        var payment = paid();
        var complaint = complaints.saveAndFlush(new OrderComplaint(order, "Kiểm tra lại chứng từ", "Thanh toán thay đổi sau khi lập phiếu", null, NOW));
        Long id = cases.request(manager.getUsername(), request(OperationsCase.Kind.COMPENSATION, complaint.getId(), complaint.getRevision(), BigDecimal.TEN, null), image());
        var record = cases.view(admin.getUsername(), id);
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); payment.setTransactionRef(null); payments.flush();
        mvc.perform(get("/manager/ho-so-van-hanh/" + id).with(authentication(auth(adminAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"approve\" value=\"true\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"approve\" value=\"false\"")));
        assertThrows(IllegalArgumentException.class, () -> cases.review(admin.getUsername(), id,
                new OperationsReviewRequest(record.getRevision(), true, "Không duyệt khi thiếu biên nhận")));
        assertEquals(OperationsCase.Status.REQUESTED, record.getStatus());
    }
    @Test void approvalUiDoesNotOfferExpiredRetryButStillAllowsRejection() throws Exception {
        for (int i = 0; i < 3; i++) history.save(new OrderStatusHistory(order, shipper, Order.OrderStatus.IN_TRANSIT,
                Order.OrderStatus.FAILED, DeliveryFailureReason.CUSTOMER_ABSENT, "Khách không có mặt", null, NOW.minusHours(4 - i)));
        history.flush();
        Long id = cases.request(manager.getUsername(), request(OperationsCase.Kind.RETRY, order.getId(), order.getRevision(), null, NOW.plusHours(1)), null);
        clock.at(NOW.plusHours(2));
        mvc.perform(get("/manager/ho-so-van-hanh/" + id).with(authentication(auth(adminAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"approve\" value=\"true\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"approve\" value=\"false\"")));
    }
    @Test void legacyFinancialCaseWithoutAmountDoesNotCrashOrOfferApproval() throws Exception {
        var settlement = settlements.saveAndFlush(new CodSettlement(shipper, 1, new BigDecimal("18000"), new BigDecimal("10000"), "Thiếu cước kiểm thử", NOW));
        settlement.review(manager, true, "Đã xác nhận khoản thiếu", NOW); settlements.flush();
        Long id = cases.request(manager.getUsername(), request(OperationsCase.Kind.CASH_SUPPLEMENT, settlement.getId(), settlement.getRevision(), BigDecimal.TEN, null), image());
        var record = cases.view(admin.getUsername(), id);
        em.createNativeQuery("update operations_case set amount=null where id=:id").setParameter("id", id).executeUpdate();
        em.refresh(record);
        mvc.perform(get("/manager/ho-so-van-hanh/" + id).with(authentication(auth(adminAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"approve\" value=\"true\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"approve\" value=\"false\"")));
        mvc.perform(post("/manager/ho-so-van-hanh/" + id + "/duyet").with(authentication(auth(adminAccount))).with(csrf())
                .param("revision", Long.toString(record.getRevision())).param("approve", "true").param("reason", "Kiểm tra phiếu thiếu số tiền"))
                .andExpect(redirectedUrl("/manager/ho-so-van-hanh/" + id)).andExpect(flash().attributeExists("error"));
        assertEquals(OperationsCase.Status.REQUESTED, record.getStatus());
    }
    @Test void compensationCompletionRechecksReceiptAndDoesNotOfferInvalidPaymentAction() throws Exception {
        var payment = paid();
        var complaint = complaints.saveAndFlush(new OrderComplaint(order, "Kiểm tra lại trước khi chi", "Thanh toán thay đổi sau lúc duyệt", null, NOW));
        Long id = cases.request(manager.getUsername(), request(OperationsCase.Kind.COMPENSATION, complaint.getId(), complaint.getRevision(), BigDecimal.TEN, null), image());
        var record = cases.view(admin.getUsername(), id);
        cases.review(admin.getUsername(), id, new OperationsReviewRequest(record.getRevision(), true, "Duyệt cước đã thanh toán hợp lệ"));
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); payment.setTransactionRef(null); payments.flush();
        assertFalse(cases.canComplete(admin.getUsername(), id));
        mvc.perform(get("/manager/ho-so-van-hanh/" + id).with(authentication(auth(adminAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("/" + id + "/hoan-tat"))));
        assertThrows(IllegalArgumentException.class, () -> cases.complete(admin.getUsername(), id, record.getRevision(), "RECEIPT-OUT-01", image()));
        assertEquals(OperationsCase.Status.APPROVED, record.getStatus());
    }
    @Test void duplicateClaimPostReturnsToComplaintWithValidationMessage() throws Exception {
        complaintService.create(order.getId(), customer.getUsername(), "Kiểm tra nhận lặp từ form", "Nội dung mở hồ sơ", null);
        var complaint = complaints.findByOrder(order).orElseThrow();
        complaintService.claim(complaint.getId(), manager.getUsername(), complaint.getRevision());
        mvc.perform(post("/manager/khieu-nai/" + complaint.getId() + "/nhan-xu-ly").with(authentication(auth(managerAccount))).with(csrf())
                .param("revision", Long.toString(complaint.getRevision())))
                .andExpect(redirectedUrl("/manager/khieu-nai/" + complaint.getId())).andExpect(flash().attributeExists("error"));
    }
    @Test void compensationUiDoesNotOfferDuplicatePendingOrApprovedCase() throws Exception {
        paid(); var complaint = complaints.saveAndFlush(new OrderComplaint(order, "Không lập phiếu trùng", "Hồ sơ đã có đề xuất đang xử lý", null, NOW));
        Long id = cases.request(manager.getUsername(), request(OperationsCase.Kind.COMPENSATION, complaint.getId(), complaint.getRevision(), BigDecimal.TEN, null), image());
        String path = "/manager/khieu-nai/" + complaint.getId();
        mvc.perform(get(path).with(authentication(auth(managerAccount)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Đề xuất bồi thường"))));
        var record = cases.view(admin.getUsername(), id);
        cases.review(admin.getUsername(), id, new OperationsReviewRequest(record.getRevision(), false, "Từ chối đề xuất để kiểm tra lại"));
        mvc.perform(get(path).with(authentication(auth(managerAccount)))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Đề xuất bồi thường")));
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
        var form=new OperationsPolicyRequest(p.getRevision(),24,100,40,4,4,24,168, 15, 3000,"Điều chỉnh chính sách thử");
        policies.update(admin.getUsername(),form); assertEquals(4,policies.current().getMaxDeliveryAttempts());
        assertThrows(StaffConflictException.class,()->policies.update(admin.getUsername(),form));
    }
    @Test void inconsistentPaymentSnapshotIsVisibleInManagerAlerts() {
        var payment=paid();payment.setDiscountAmount(new BigDecimal("1000"));payments.saveAndFlush(payment);
        assertTrue(overview.alerts(manager.getUsername(),OperationsOverviewService.Alert.PAYMENT_ISSUE,0).getContent().contains(order));
    }
    @Test void paidMomoWithoutReceiptIsVisibleInManagerAlerts() {
        var payment=paid();payment.setMethod(OrderPayment.PaymentMethod.MOMO);payment.setTransactionRef(null);payments.saveAndFlush(payment);
        assertTrue(overview.alerts(manager.getUsername(),OperationsOverviewService.Alert.PAYMENT_ISSUE,0).getContent().contains(order));
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
    @Test void compensationForPaidFeeIsCappedAtFourTimesTheFee() {
        // Nghị định 47/2011/NĐ-CP, Điều 25: hàng không khai giá bồi thường tối đa 4 lần cước, đã gồm hoàn cước 18.000 đ.
        paid(); var complaint=complaints.saveAndFlush(new OrderComplaint(order,"Mất hàng khi vận chuyển","Nội dung kiểm thử khiếu nại",null,NOW));
        var error=assertThrows(IllegalArgumentException.class,()->cases.request(manager.getUsername(),request(OperationsCase.Kind.COMPENSATION,
                complaint.getId(),complaint.getRevision(),new BigDecimal("72001"),null),image()));
        assertTrue(error.getMessage().contains("Nghị định 47/2011") && error.getMessage().contains("72.000 đ"), error.getMessage());
        assertEquals(0, caseRepository.count());
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.COMPENSATION,complaint.getId(),complaint.getRevision(),new BigDecimal("72000"),null),image());
        assertEquals(new BigDecimal("72000"), cases.view(admin.getUsername(),id).getAmount());
    }
    @Test void unpaidCodOrderLostBeforeDeliveryIsCompensatedUpToThreeTimesTheFee() throws Exception {
        // Đơn trả cước khi giao chưa thu cước: không thu cước nữa nên bồi thường tối đa 3 lần, người gửi được bù như đơn đã trả.
        var unpaid=new OrderPayment(); unpaid.setOrder(order); unpaid.setAmount(new BigDecimal("18000"));
        unpaid.setMethod(OrderPayment.PaymentMethod.COD); unpaid.setStatus(OrderPayment.PaymentStatus.UNPAID); payments.saveAndFlush(unpaid);
        var complaint=complaints.saveAndFlush(new OrderComplaint(order,"Mất hàng trước khi giao","Kiện thất lạc trên chuyến trung chuyển",null,NOW));
        assertEquals(OperationsCase.Kind.COMPENSATION, cases.prepare(manager.getUsername(),OperationsCase.Kind.COMPENSATION,complaint.getId()).kind());
        mvc.perform(get("/manager/khieu-nai/"+complaint.getId()).with(authentication(auth(managerAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Đề xuất bồi thường")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("54.000 đ")));
        assertThrows(IllegalArgumentException.class,()->cases.request(manager.getUsername(),request(OperationsCase.Kind.COMPENSATION,
                complaint.getId(),complaint.getRevision(),new BigDecimal("54001"),null),image()));
        Long id=cases.request(manager.getUsername(),request(OperationsCase.Kind.COMPENSATION,complaint.getId(),complaint.getRevision(),new BigDecimal("54000"),null),image());
        var record=cases.view(admin.getUsername(),id);
        cases.review(admin.getUsername(),id,new OperationsReviewRequest(record.getRevision(),true,"Đủ hồ sơ thất lạc"));
        assertEquals(OperationsCase.Status.APPROVED, cases.view(admin.getUsername(),id).getStatus());
        assertEquals(OrderPayment.PaymentStatus.UNPAID, payments.findByOrder(order).orElseThrow().getStatus(), "Duyệt bồi thường không tự ghi là đã thu cước");
    }
    @Test void orderWithoutPaymentHasNoCompensationOption() throws Exception {
        var complaint=complaints.saveAndFlush(new OrderComplaint(order,"Yêu cầu bồi thường","Đơn chưa có thanh toán",null,NOW));
        var error=assertThrows(IllegalArgumentException.class,()->cases.prepare(manager.getUsername(),OperationsCase.Kind.COMPENSATION,complaint.getId()));
        assertTrue(error.getMessage().contains("chưa có thanh toán hợp lệ"));
        mvc.perform(get("/manager/khieu-nai/"+complaint.getId()).with(authentication(auth(managerAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Đề xuất bồi thường"))));
        paid();
        assertEquals(OperationsCase.Kind.COMPENSATION, cases.prepare(manager.getUsername(),OperationsCase.Kind.COMPENSATION,complaint.getId()).kind());
        // B8: Manager có nút đề xuất; Admin (người duyệt chi tiền) chỉ thấy ghi chú, không có nút.
        mvc.perform(get("/manager/khieu-nai/"+complaint.getId()).with(authentication(auth(managerAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Đề xuất bồi thường")));
        mvc.perform(get("/manager/khieu-nai/"+complaint.getId()).with(authentication(auth(adminAccount))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Đề xuất bồi thường"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Phiếu tiền do Manager lập, Admin duyệt.")));
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
                .param("complaintResponseHours","24").param("complaintResolveHours","168").param("maxActiveOrders","15").param("dropOffDiscount","5000").param("reason","Đổi chính sách qua form"))
                .andExpect(redirectedUrl("/admin/chinh-sach-van-hanh"));
        assertEquals(4,policies.current().getMaxDeliveryAttempts());
        assertEquals(0,new BigDecimal("5000").compareTo(policies.current().getDropOffDiscount()));
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
