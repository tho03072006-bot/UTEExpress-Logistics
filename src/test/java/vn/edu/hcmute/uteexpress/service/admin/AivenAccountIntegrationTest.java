package vn.edu.hcmute.uteexpress.service.admin;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.AdminUserRepository;
import static org.junit.jupiter.api.Assertions.*;

/** Chỉ sửa/xóa tài khoản QA vừa tạo trong transaction rollback, không đụng tài khoản nhóm. */
@SpringBootTest @Transactional
class AivenAccountIntegrationTest {
    @Autowired EntityManager em;
    @Autowired AdminAccountService service;
    @Autowired AdminUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired vn.edu.hcmute.uteexpress.service.routing.StaffBranchService assignments;
    @ParameterizedTest @EnumSource(AppUser.Role.class)
    void createUpdateResetAndDeleteAllRolesKeepAuditAndUseRealCloud(AppUser.Role role) {
        var actor = admin(); var form = create(role);
        service.createAccount(actor.getUsername(), form);
        var user = users.findByUsername(form.getUsername()).orElseThrow(); var id = user.getId();
        assertTrue(encoder.matches("FirstPassword@2026", user.getPassword())); assertNull(user.getOtpCode());
        assertNotNull(service.findAccounts(actor.getUsername(), role, 0));
        var change = new AdminAccountUpdateRequest(); action(change, user);
        change.getProfile().setFullName("Họ tên kiểm thử mới"); change.getProfile().setEmail(user.getEmail());
        change.getProfile().setPhone("+84 901 234 568"); change.getProfile().setRole(role); change.setActive(true);
        change.getProfile().setAssignment(assignments.selection(actor.getUsername(), id));
        service.updateAccount(actor.getUsername(), id, change);
        assertEquals("0901234568", user.getPhone()); assertTrue(user.getRevision() > change.getRevision());
        var reset = new AdminAccountPasswordRequest(); action(reset, user);
        reset.getCredentials().setPassword("SecondPassword@2026"); reset.getCredentials().setConfirmPassword("SecondPassword@2026");
        service.resetPassword(actor.getUsername(), id, reset);
        assertTrue(encoder.matches("SecondPassword@2026", user.getPassword())); assertEquals(2, user.getAccessVersion());
        var deletion = new AdminAccountDeleteRequest(); action(deletion, user); deletion.setConfirmedUsername(user.getUsername());
        service.deleteAccount(actor.getUsername(), id, deletion); em.flush();
        assertTrue(users.findById(id).isEmpty());
        assertEquals(0L, em.createQuery("select count(s) from StaffAccount s where s.user.id = :id", Long.class).setParameter("id", id).getSingleResult());
        long expectedAudit = role == AppUser.Role.SHIPPER || role == AppUser.Role.MANAGER ? 5L : 4L;
        assertEquals(expectedAudit, em.createQuery("select count(a) from AuditEvent a where a.target = :target", Long.class)
            .setParameter("target", "user:" + id).getSingleResult());
        assertEquals(0L, em.createQuery("select count(s) from StaffBranch s where s.user.id = :id", Long.class).setParameter("id", id).getSingleResult());
        assertEquals(0L, em.createQuery("select count(s) from ShipperZone s where s.shipper.id = :id", Long.class).setParameter("id", id).getSingleResult());
        // Còn Admin hoạt động, không bị ảnh hưởng khi xóa một Admin QA khác.
        assertTrue(users.findById(actor.getId()).orElseThrow().isEnabled());
    }
    @Test void linkedOrderCannotBeDeletedAndOldFormCannotOverwriteNewState() {
        var actor = admin(); var form = create(AppUser.Role.USER); service.createAccount(actor.getUsername(), form);
        var user = users.findByUsername(form.getUsername()).orElseThrow();
        var order = new Order(); order.setSender(user); order.setTrackingCode("QA" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        order.setReceiverPhone("0901234567"); order.setWeightKg(1.0); order.setServiceType(Order.ServiceType.STANDARD);
        order.setShippingFee(BigDecimal.valueOf(18000)); em.persist(order); em.flush();
        var deletion = new AdminAccountDeleteRequest(); action(deletion, user); deletion.setConfirmedUsername(user.getUsername());
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount(actor.getUsername(), user.getId(), deletion));
        assertNotNull(em.find(Order.class, order.getId())); assertNotNull(em.find(AppUser.class, user.getId()));
        var change = new AdminAccountUpdateRequest(); action(change, user); change.setActive(false);
        change.getProfile().setFullName(user.getFullName()); change.getProfile().setEmail(user.getEmail());
        change.getProfile().setPhone(user.getPhone()); change.getProfile().setRole(AppUser.Role.USER);
        service.updateAccount(actor.getUsername(), user.getId(), change);
        assertTrue(user.isLockedByAdmin()); assertFalse(user.isEnabled());
        assertThrows(StaffConflictException.class, () -> service.deleteAccount(actor.getUsername(), user.getId(), deletion));
    }
    @Test void realUnfinishedAssignmentQueryPreventsLockingShipper() {
        var actor = admin(); var form = create(AppUser.Role.SHIPPER); service.createAccount(actor.getUsername(), form);
        var user = users.findByUsername(form.getUsername()).orElseThrow();
        var order = new Order(); order.setSender(actor); order.setShipper(user);
        order.setTrackingCode("QA" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        order.setWeightKg(1.0); order.setServiceType(Order.ServiceType.STANDARD); order.setShippingFee(BigDecimal.valueOf(18000));
        em.persist(order); em.flush();
        var change = new AdminAccountUpdateRequest(); action(change, user); change.setActive(false);
        change.getProfile().setFullName(user.getFullName()); change.getProfile().setEmail(user.getEmail());
        change.getProfile().setPhone(user.getPhone()); change.getProfile().setRole(AppUser.Role.SHIPPER);
        var error = assertThrows(IllegalArgumentException.class, () -> service.updateAccount(actor.getUsername(), user.getId(), change));
        assertTrue(error.getMessage().contains("Shipper còn đơn")); assertTrue(user.isEnabled()); assertFalse(user.isLockedByAdmin());
    }
    private AppUser admin() {
        var form = create(AppUser.Role.ADMIN); var user = new AppUser(form.getUsername(), encoder.encode("AdminPassword@2026"), form.getEmail());
        user.setRole(AppUser.Role.ADMIN); user.setEnabled(true); user.setFullName(form.getFullName()); user.setPhone(form.getPhone()); em.persist(user);
        var staff = new StaffAccount(); staff.setUser(user); staff.setIssuedBy("integration-test"); staff.setPasswordChangeRequired(false);
        em.persist(staff); em.flush(); return user;
    }
    private AdminAccountCreateRequest create(AppUser.Role role) {
        var id = UUID.randomUUID().toString().replace("-", ""); var form = new AdminAccountCreateRequest();
        form.setUsername("qa_" + id); form.setEmail(id + "@example.com"); form.setFullName("Họ tên kiểm thử");
        form.setPhone("0901234567"); form.setRole(role); form.getCredentials().setPassword("FirstPassword@2026");
        form.getCredentials().setConfirmPassword("FirstPassword@2026");
        if (role == AppUser.Role.MANAGER || role == AppUser.Role.SHIPPER) {
            var branch = em.createQuery("select b from Branch b where b.code = 'SGN'", vn.edu.hcmute.uteexpress.entity.routing.Branch.class).getSingleResult();
            form.getAssignment().setBranchId(branch.getId());
            if (role == AppUser.Role.SHIPPER) form.getAssignment().setPrimaryAreaCode("SGN-THUDUC");
        }
        return form;
    }
    private void action(AdminAccountActionRequest form, AppUser target) {
        form.setRevision(target.getRevision()); form.setCurrentPassword("AdminPassword@2026"); form.setReason("Kiểm thử rollback dữ liệu tài khoản");
    }
}
