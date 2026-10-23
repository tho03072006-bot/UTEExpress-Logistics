package vn.edu.hcmute.uteexpress.service.manager;

import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.*;
import vn.edu.hcmute.uteexpress.service.tracking.ShipmentQrService;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra khóa/JPQL/MySQL thật. Chỉ tạo dữ liệu QA trong transaction tự rollback, không sửa đơn của nhóm. */
@SpringBootTest @Transactional
class AivenControlsIntegrationTest {
    @Autowired EntityManager em;
    @Autowired OperationsCaseService cases;
    @Autowired OperationsOverviewService overview;
    @Autowired ShipmentQrService qr;
    @Autowired Clock clock;

    private AppUser account(AppUser.Role role) {
        var user = new AppUser("qa_" + UUID.randomUUID().toString().replace("-", ""), "test-only-hash", UUID.randomUUID() + "@example.com");
        user.setEnabled(true); user.setRole(role); em.persist(user);
        if (role == AppUser.Role.ADMIN || role == AppUser.Role.MANAGER) {
            var staff = new StaffAccount(); staff.setUser(user); staff.setActive(true);
            staff.setPasswordChangeRequired(false); staff.setIssuedBy("integration-test"); em.persist(staff);
        }
        em.flush(); return user;
    }
    private MockMultipartFile image() {
        return new MockMultipartFile("evidence", "receipt.png", "image/png", qr.generate("QA-CONTROLS"));
    }
    @Test void returnWorkflowLocksRealRowsAndPreservesHistory() {
        var manager = account(AppUser.Role.MANAGER); var sender = account(AppUser.Role.USER); var shipper = account(AppUser.Role.SHIPPER);
        var now = LocalDateTime.now(clock);
        var order = new Order(); order.setSender(sender); order.setShipper(shipper);
        order.setTrackingCode("QA" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        order.setWeightKg(1.0); order.setShippingFee(new BigDecimal("18000"));
        order.setServiceType(Order.ServiceType.STANDARD); order.setStatus(Order.OrderStatus.FAILED); em.persist(order); em.flush();
        Long id = cases.request(manager.getUsername(), new OperationsCaseRequest(OperationsCase.Kind.RETURN, order.getId(),
                order.getRevision(), null, null, "Kiểm thử hoàn hàng rollback trên MySQL", "", UUID.randomUUID().toString()), null);
        var record = cases.view(manager.getUsername(), id);
        assertEquals(OperationsCase.Status.APPROVED, record.getStatus(), "Hoàn hàng có hiệu lực ngay theo B8, không qua bước duyệt.");
        cases.startReturn(manager.getUsername(), id, record.getRevision(), "Bắt đầu hoàn hàng kiểm thử");
        assertEquals(OperationsCase.Status.IN_PROGRESS, record.getStatus());
        cases.complete(manager.getUsername(), id, record.getRevision(), "Người gửi kiểm thử nhận hoàn", image());
        assertEquals(Order.OrderStatus.RETURNED, order.getStatus());
        assertEquals(OperationsCase.Status.COMPLETED, record.getStatus());
        assertEquals(1L, em.createQuery("select count(h) from OrderStatusHistory h where h.order = :order", Long.class)
                .setParameter("order", order).getSingleResult());
        assertNotNull(overview.alerts(manager.getUsername(), OperationsOverviewService.Alert.RETRY_DUE, 0));
        assertNotNull(overview.performance(manager.getUsername(), 0));
        assertTrue(record.getUpdatedAt().compareTo(now) >= 0);
    }
    @Test void overdueCashDisappearsOnlyAfterIndependentAdminApprovalOnMySql() {
        var manager = account(AppUser.Role.MANAGER); var admin = account(AppUser.Role.ADMIN); var shipper = account(AppUser.Role.SHIPPER);
        var now = LocalDateTime.now(clock);
        var settlement = new CodSettlement(shipper, 1, new BigDecimal("18000"), new BigDecimal("10000"), "Phiếu kiểm thử rollback", now.minusDays(9));
        settlement.review(manager, true, "Đã nhận phần tiền nộp", now.minusDays(8)); em.persist(settlement); em.flush();
        assertTrue(overview.lateCash(manager.getUsername(), 0).stream().anyMatch(s -> s.getId().equals(settlement.getId())));
        Long id = cases.request(manager.getUsername(), new OperationsCaseRequest(OperationsCase.Kind.CASH_SUPPLEMENT,
                settlement.getId(), settlement.getRevision(), new BigDecimal("8000"), null, "Nộp đủ khoản thiếu kiểm thử", "QA-RECEIPT-01", UUID.randomUUID().toString()), image());
        var record = cases.view(admin.getUsername(), id);
        cases.review(admin.getUsername(), id, new OperationsReviewRequest(record.getRevision(), true, "Kiểm tra biên nhận bổ sung"));
        assertEquals(0, cases.outstanding(settlement).signum());
        assertFalse(overview.lateCash(manager.getUsername(), 0).stream().anyMatch(s -> s.getId().equals(settlement.getId())));
        assertEquals(new BigDecimal("10000"), settlement.getDeclaredAmount());
        assertNotNull(overview.lateComplaints(manager.getUsername(), 0));
    }
}
