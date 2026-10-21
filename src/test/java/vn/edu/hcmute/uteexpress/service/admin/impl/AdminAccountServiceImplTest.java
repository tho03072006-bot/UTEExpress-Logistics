package vn.edu.hcmute.uteexpress.service.admin.impl;

import jakarta.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.repository.EmailChangeRequestRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceImplTest {
    @Mock AdminUserRepository users;
    @Mock StaffAccountRepository staff;
    @Mock EmailChangeRequestRepository emailChanges;
    @Mock AuditService audit;
    private ValidatorFactory factory;
    private AdminAccountServiceImpl service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private StaffAccount admin;
    private AppUser target;
    @BeforeEach void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new AdminAccountServiceImpl(users, staff, emailChanges,
            new StaffAccessPolicy(staff, factory.getValidator()), encoder, audit,
            new vn.edu.hcmute.uteexpress.security.admin.StaffLoginAttemptService(java.time.Clock.systemUTC()));
        admin = staffAccount(user(1L, "admin01", AppUser.Role.ADMIN));
        target = user(3L, "userdemo", AppUser.Role.USER);
    }
    @AfterEach void closeValidator() { factory.close(); }
    @ParameterizedTest @EnumSource(AppUser.Role.class)
    void adminCreatesEveryRoleWithEncodedPasswordWithoutOtp(AppUser.Role role) {
        when(staff.lockAccounts()).thenReturn(List.of(admin));
        when(users.save(any())).thenAnswer(inv -> { AppUser u = inv.getArgument(0); u.setId(3L); return u; });
        var form = create(role); service.createAccount("admin01", form);
        var saved = ArgumentCaptor.forClass(AppUser.class); verify(users).save(saved.capture());
        assertEquals(role, saved.getValue().getRole()); assertTrue(saved.getValue().isEnabled());
        assertNull(saved.getValue().getOtpCode()); assertNull(saved.getValue().getOtpExpiry());
        assertTrue(encoder.matches("NewPassword@2026", saved.getValue().getPassword()));
        assertEquals("0901234567", saved.getValue().getPhone());
        if (role == AppUser.Role.ADMIN || role == AppUser.Role.MANAGER) {
            var issued = ArgumentCaptor.forClass(StaffAccount.class); verify(staff).save(issued.capture());
            assertTrue(issued.getValue().isPasswordChangeRequired()); assertEquals("admin01", issued.getValue().getIssuedBy());
        } else { verify(staff, never()).save(any()); }
        verify(audit).record(eq("admin01"), eq("ACCOUNT_CREATE"), eq("user:3"), anyString());
    }
    @ParameterizedTest @EnumSource(value = AppUser.Role.class, names = {"USER", "SHIPPER", "MANAGER"})
    void otherRolesCannotCallAnyCrudServiceDirectly(AppUser.Role role) {
        var actor = staffAccount(user(2L, "notadmin", role));
        when(staff.lockAccounts()).thenReturn(List.of(actor));
        when(staff.findByUserUsername("notadmin")).thenReturn(Optional.of(actor));
        assertThrows(AccessDeniedException.class, () -> service.findAccounts("notadmin", null, 0));
        assertThrows(AccessDeniedException.class, () -> service.findAccount("notadmin", 3L));
        assertThrows(AccessDeniedException.class, () -> service.createAccount("notadmin", create(AppUser.Role.USER)));
        assertThrows(AccessDeniedException.class, () -> service.updateAccount("notadmin", 3L, update()));
        assertThrows(AccessDeniedException.class, () -> service.deleteAccount("notadmin", 3L, deletion()));
        assertThrows(AccessDeniedException.class, () -> service.resetPassword("notadmin", 3L, reset()));
        verifyNoInteractions(users, emailChanges, audit);
    }
    @Test void revokedOrUnchangedInitialPasswordAdminCannotWrite() {
        when(staff.lockAccounts()).thenReturn(List.of(admin));
        admin.getUser().setLockedByAdmin(true);
        assertThrows(AccessDeniedException.class, () -> service.createAccount("admin01", create(AppUser.Role.USER)));
        admin.getUser().setLockedByAdmin(false); admin.setPasswordChangeRequired(true);
        assertThrows(AccessDeniedException.class, () -> service.createAccount("admin01", create(AppUser.Role.USER)));
        verifyNoInteractions(users);
    }
    @Test void duplicateUsernameAndEmailCannotCreateOrPromoteExistingCustomer() {
        when(staff.lockAccounts()).thenReturn(List.of(admin));
        when(users.existsByUsernameIgnoreCase("userdemo")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createAccount("admin01", create(AppUser.Role.ADMIN)));
        when(users.existsByUsernameIgnoreCase("userdemo")).thenReturn(false);
        when(users.existsByEmailIgnoreCase("user@example.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createAccount("admin01", create(AppUser.Role.USER)));
        verify(users, never()).save(any());
    }
    @Test void invalidAndMissingFormsNeverWrite() {
        when(staff.lockAccounts()).thenReturn(List.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.createAccount("admin01", null));
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 3L, new AdminAccountUpdateRequest()));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", 3L, new AdminAccountDeleteRequest()));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("admin01", 3L, new AdminAccountPasswordRequest()));
        verifyNoInteractions(users, audit);
    }
    @Test void profileUpdateAndLockRevokesSessionsAndOldRecovery() {
        lockTarget(); target.setOtpCode("123456"); var form = update(); form.setActive(false);
        service.updateAccount("admin01", 3L, form);
        assertEquals("Người dùng mới", target.getFullName()); assertFalse(target.isEnabled());
        assertTrue(target.isLockedByAdmin()); assertEquals(1, target.getAccessVersion()); assertNull(target.getOtpCode());
        verify(emailChanges).deleteByUser(target); verify(audit).record(eq("admin01"), eq("ACCOUNT_UPDATE"), eq("user:3"), anyString());
    }
    @Test void adminCanReopenLockedUserWithoutRestoringOldSessions() {
        lockTarget(); target.setEnabled(false); target.setLockedByAdmin(true);
        service.updateAccount("admin01", 3L, update());
        assertTrue(target.isEnabled()); assertFalse(target.isLockedByAdmin()); assertEquals(1, target.getAccessVersion());
    }
    @Test void duplicateUpdateEmailDoesNotMutateUser() {
        lockTarget(); when(users.existsByEmailIgnoreCaseAndIdNot("user@example.com", 3L)).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 3L, update()));
        assertEquals("Người dùng", target.getFullName()); verifyNoInteractions(emailChanges, audit);
    }
    @Test void unchangedUpdateDoesNotWrite() {
        lockTarget(); var form = update(); form.getProfile().setFullName(target.getFullName());
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 3L, form));
        verify(staff, never()).flush(); verifyNoInteractions(audit);
    }
    @Test void cannotPromoteOtpAccountOrDemoteIssuedAccountIntoPublicGroup() {
        lockTarget(); var form = update(); form.getProfile().setRole(AppUser.Role.ADMIN);
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 3L, form));
        target.setRole(AppUser.Role.MANAGER); form.getProfile().setRole(AppUser.Role.USER);
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 3L, form));
        verifyNoInteractions(audit);
    }
    @Test void userAndShipperRolesMayChangeOnlyWithoutLinkedData() {
        lockTarget(); var form = update(); form.getProfile().setRole(AppUser.Role.SHIPPER);
        when(users.countOrders(3L)).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 3L, form));
        when(users.countOrders(3L)).thenReturn(0L); service.updateAccount("admin01", 3L, form);
        assertEquals(AppUser.Role.SHIPPER, target.getRole());
    }
    @Test void assignedShipperCannotBeLockedUntilUnfinishedOrdersHandled() {
        lockTarget(); target.setRole(AppUser.Role.SHIPPER); var form = update();
        form.getProfile().setRole(AppUser.Role.SHIPPER); form.setActive(false);
        when(users.countUnfinishedAssignments(eq(3L), any())).thenReturn(1L);
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 3L, form));
        assertTrue(target.isEnabled()); verifyNoInteractions(audit);
    }
    @Test void internalRoleChangeUpdatesBothRecordsAndRevokesStaffSession() {
        target.setRole(AppUser.Role.MANAGER); var account = staffAccount(target);
        when(staff.lockAccounts()).thenReturn(List.of(admin, account)); when(users.lockUser(3L)).thenReturn(Optional.of(target));
        var form = update(); form.getProfile().setRole(AppUser.Role.ADMIN);
        service.updateAccount("admin01", 3L, form);
        assertEquals(AppUser.Role.ADMIN, target.getRole()); assertEquals(1, account.getAccessVersion());
    }
    @Test void incorrectAdminPasswordAndStaleRevisionCannotMutate() {
        when(staff.lockAccounts()).thenReturn(List.of(admin)); var form = deletion(); form.setCurrentPassword("WrongPassword@2026");
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", 3L, form));
        verifyNoInteractions(users);
        form.setCurrentPassword("OriginalPass@2026"); form.setRevision(9L); when(users.lockUser(3L)).thenReturn(Optional.of(target));
        assertThrows(StaffConflictException.class, () -> service.deleteAccount("admin01", 3L, form));
        verify(users, never()).delete(any());
    }
    @Test void selfUpdateResetAndDeleteAreForbidden() {
        when(staff.lockAccounts()).thenReturn(List.of(admin)); when(users.lockUser(1L)).thenReturn(Optional.of(admin.getUser()));
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 1L, update()));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", 1L, deletion()));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("admin01", 1L, reset()));
        verify(users, never()).delete(any()); verifyNoInteractions(audit);
    }
    @Test void incorrectDeleteUsernameDoesNotDelete() {
        lockTarget(); var form = deletion(); form.setConfirmedUsername("other-user");
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", 3L, form));
        verify(users, never()).delete(any());
    }
    @Test void repeatedIncorrectConfirmationIsRateLimitedWithoutTouchingTarget() {
        when(staff.lockAccounts()).thenReturn(List.of(admin));
        var form = deletion(); form.setCurrentPassword("WrongPassword@2026");
        for (int i = 0; i < 5; i++) {
            assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", 3L, form));
        }
        form.setCurrentPassword("OriginalPass@2026");
        var error = assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", 3L, form));
        assertTrue(error.getMessage().contains("15 phút")); verifyNoInteractions(users, audit);
    }
    @ParameterizedTest @EnumSource(AppUser.Role.class)
    void unusedAccountsOfAllRolesCanBeDeletedWithAudit(AppUser.Role role) {
        target.setRole(role);
        var account = staffAccount(target);
        when(staff.lockAccounts()).thenReturn(List.of(admin, account)); when(users.lockUser(3L)).thenReturn(Optional.of(target));
        service.deleteAccount("admin01", 3L, deletion()); verify(users).delete(target);
        if (role == AppUser.Role.ADMIN || role == AppUser.Role.MANAGER) { verify(staff).delete(account); }
        else { verify(staff, never()).delete(any()); }
        verify(audit).record(eq("admin01"), eq("ACCOUNT_DELETE"), eq("user:3"), anyString());
    }
    @Test void everyBusinessRelationshipAndIssuanceHistoryPreventsDeletion() {
        lockTarget();
        when(users.countOrders(3L)).thenReturn(1L); rejectsDeletion(); when(users.countOrders(3L)).thenReturn(0L);
        when(users.countAddresses(3L)).thenReturn(1L); rejectsDeletion(); when(users.countAddresses(3L)).thenReturn(0L);
        when(users.countDrafts(3L)).thenReturn(1L); rejectsDeletion(); when(users.countDrafts(3L)).thenReturn(0L);
        when(users.countFormSubmissions(3L)).thenReturn(1L); rejectsDeletion(); when(users.countFormSubmissions(3L)).thenReturn(0L);
        when(users.countReviews(3L)).thenReturn(1L); rejectsDeletion(); when(users.countReviews(3L)).thenReturn(0L);
        when(users.countProofs(3L)).thenReturn(1L); rejectsDeletion(); when(users.countProofs(3L)).thenReturn(0L);
        when(users.countEmailChanges(3L)).thenReturn(1L); rejectsDeletion(); when(users.countEmailChanges(3L)).thenReturn(0L);
        when(users.countIssuedAccounts("userdemo")).thenReturn(1L); rejectsDeletion();
        verify(users, never()).delete(any()); verify(staff, never()).delete(any()); verifyNoInteractions(audit);
    }
    @Test void invalidIdAndMissingAccountGiveSafeValidationErrors() {
        when(staff.lockAccounts()).thenReturn(List.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", -1L, deletion()));
        assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", 99L, deletion()));
        verify(users, never()).delete(any());
    }
    @Test void resetRejectsLockedSamePasswordAndInvalidConfirmation() {
        lockTarget(); target.setEnabled(false);
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("admin01", 3L, reset()));
        target.setEnabled(true); var form = reset(); form.getCredentials().setPassword("OriginalPass@2026");
        form.getCredentials().setConfirmPassword("OriginalPass@2026");
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("admin01", 3L, form));
        form.getCredentials().setConfirmPassword("Mismatch@2026");
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("admin01", 3L, form));
        verifyNoInteractions(audit);
    }
    @Test void resetPasswordRevokesPublicSessionAndRecoveryWithoutLoggingPassword() {
        lockTarget(); service.resetPassword("admin01", 3L, reset());
        assertTrue(encoder.matches("NewPassword@2026", target.getPassword())); assertEquals(1, target.getAccessVersion());
        verify(emailChanges).deleteByUser(target);
        var detail = ArgumentCaptor.forClass(String.class);
        verify(audit).record(eq("admin01"), eq("ACCOUNT_PASSWORD_RESET"), eq("user:3"), detail.capture());
        assertFalse(detail.getValue().contains("Password")); assertFalse(detail.getValue().contains(target.getPassword()));
    }
    private void rejectsDeletion() { assertThrows(IllegalArgumentException.class, () -> service.deleteAccount("admin01", 3L, deletion())); }
    private void lockTarget() {
        when(staff.lockAccounts()).thenReturn(List.of(admin)); when(users.lockUser(3L)).thenReturn(Optional.of(target));
    }
    private AppUser user(Long id, String username, AppUser.Role role) {
        var user = new AppUser(username, encoder.encode("OriginalPass@2026"), "user@example.com"); user.setId(id);
        user.setRole(role); user.setEnabled(true); user.setFullName("Người dùng"); user.setPhone("0901234567"); return user;
    }
    private StaffAccount staffAccount(AppUser user) {
        var account = new StaffAccount(); account.setId(user.getId()); account.setUser(user); account.setIssuedBy("bootstrap");
        account.setPasswordChangeRequired(false); return account;
    }
    private AdminAccountCreateRequest create(AppUser.Role role) {
        var form = new AdminAccountCreateRequest(); form.setUsername(" USERDEMO "); form.setFullName("Người dùng");
        form.setEmail(" USER@EXAMPLE.COM "); form.setPhone("+84 901 234 567"); form.setRole(role);
        credentials(form.getCredentials()); return form;
    }
    private AdminAccountUpdateRequest update() {
        var form = new AdminAccountUpdateRequest(); action(form); form.setActive(true);
        form.getProfile().setFullName("Người dùng mới"); form.getProfile().setEmail("user@example.com");
        form.getProfile().setPhone("0901234567"); form.getProfile().setRole(AppUser.Role.USER); return form;
    }
    private AdminAccountDeleteRequest deletion() {
        var form = new AdminAccountDeleteRequest(); action(form); form.setConfirmedUsername("userdemo"); return form;
    }
    private AdminAccountPasswordRequest reset() {
        var form = new AdminAccountPasswordRequest(); action(form); credentials(form.getCredentials()); return form;
    }
    private void action(AdminAccountActionRequest form) { form.setRevision(0L); form.setCurrentPassword("OriginalPass@2026"); form.setReason("Kiểm thử chức năng quản trị"); }
    private void credentials(StaffPasswordRequest form) { form.setPassword("NewPassword@2026"); form.setConfirmPassword("NewPassword@2026"); }
}
