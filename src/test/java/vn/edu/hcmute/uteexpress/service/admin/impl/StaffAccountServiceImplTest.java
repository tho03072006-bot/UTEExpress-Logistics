package vn.edu.hcmute.uteexpress.service.admin.impl;

import jakarta.validation.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StaffAccountServiceImplTest {
    @Mock StaffAccountRepository accounts;
    @Mock AdminUserRepository users;
    private ValidatorFactory factory;
    private StaffAccountServiceImpl service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private StaffAccount admin;
    private StaffAccount manager;
    @BeforeEach void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        service = new StaffAccountServiceImpl(accounts, users, encoder, factory.getValidator());
        admin = account(1L, "admin01", AppUser.Role.ADMIN);
        manager = account(2L, "manager01", AppUser.Role.MANAGER);
    }
    @AfterEach void closeValidator() { factory.close(); }

    @Test void createAccountEncodesPasswordAndNeverCreatesOtp() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        when(users.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var request = createRequest();
        service.createAccount("admin01", request);
        var captor = ArgumentCaptor.forClass(StaffAccount.class);
        verify(accounts).saveAndFlush(captor.capture());
        var created = captor.getValue();
        assertTrue(encoder.matches(request.getPassword(), created.getUser().getPassword()));
        assertNull(created.getUser().getOtpCode());
        assertNull(created.getUser().getOtpExpiry());
        assertTrue(created.getUser().isEnabled());
        assertTrue(created.isPasswordChangeRequired());
        assertEquals("admin01", created.getIssuedBy());
        assertEquals(AppUser.Role.MANAGER, created.getUser().getRole());
    }
    @Test void managerCannotProvisionAccounts() {
        when(accounts.lockAccounts()).thenReturn(List.of(manager));
        assertThrows(AccessDeniedException.class, () -> service.createAccount("manager01", createRequest()));
        verifyNoInteractions(users);
    }
    @Test void revokedAdminCannotCreateAccount() {
        admin.setActive(false);
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        assertThrows(AccessDeniedException.class, () -> service.createAccount("admin01", createRequest()));
    }
    @Test void adminMustChangeTemporaryPasswordBeforeProvisioning() {
        admin.setPasswordChangeRequired(true);
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        assertThrows(AccessDeniedException.class, () -> service.createAccount("admin01", createRequest()));
    }
    @Test void duplicateUsernameDoesNotOverwriteExistingAccount() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        when(users.existsByUsernameIgnoreCase("manager02")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createAccount("admin01", createRequest()));
        verify(users, never()).save(any());
    }
    @Test void duplicateEmailDoesNotPromoteCustomer() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        when(users.existsByEmailIgnoreCase("manager02@example.com")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.createAccount("admin01", createRequest()));
        verify(users, never()).save(any());
    }
    @Test void rejectsInvalidRoleEvenWhenCalledDirectly() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        var request = createRequest();
        request.setRole(AppUser.Role.SHIPPER);
        assertThrows(IllegalArgumentException.class, () -> service.createAccount("admin01", request));
        verifyNoInteractions(users);
    }
    @Test void adminCannotLockOrDemoteSelf() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 1L, update(false, AppUser.Role.MANAGER)));
        assertTrue(admin.isActive());
        assertEquals(AppUser.Role.ADMIN, admin.getUser().getRole());
    }
    @Test void lockRevokesSessionsAndClearsOtp() {
        manager.getUser().setOtpCode("123456");
        when(accounts.lockAccounts()).thenReturn(List.of(admin, manager));
        service.updateAccount("admin01", 2L, update(false, AppUser.Role.MANAGER));
        assertFalse(manager.isActive());
        assertFalse(manager.getUser().isEnabled());
        assertNull(manager.getUser().getOtpCode());
        assertEquals(1, manager.getAccessVersion());
    }
    @Test void roleChangeRevokesExistingSession() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin, manager));
        service.updateAccount("admin01", 2L, update(true, AppUser.Role.ADMIN));
        assertEquals(AppUser.Role.ADMIN, manager.getUser().getRole());
        assertEquals(1, manager.getAccessVersion());
    }
    @Test void demotedActorCannotDemoteRemainingAdmin() {
        admin.getUser().setRole(AppUser.Role.MANAGER);
        manager.getUser().setRole(AppUser.Role.ADMIN);
        when(accounts.lockAccounts()).thenReturn(List.of(admin, manager));
        assertThrows(AccessDeniedException.class, () -> service.updateAccount("admin01", 2L, update(true, AppUser.Role.MANAGER)));
        assertEquals(AppUser.Role.ADMIN, manager.getUser().getRole());
    }
    @Test void staleFormIsRejected() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin, manager));
        var request = update(false, AppUser.Role.MANAGER);
        request.setRevision(10L);
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 2L, request));
        assertTrue(manager.isActive());
    }
    @Test void missingTargetIsRejected() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 999L, update(false, AppUser.Role.MANAGER)));
    }
    @Test void unchangedFormDoesNotWrite() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin, manager));
        assertThrows(IllegalArgumentException.class, () -> service.updateAccount("admin01", 2L, update(true, AppUser.Role.MANAGER)));
        verify(accounts, never()).flush();
    }
    @Test void resetForcesPasswordChangeAndRevokesSessions() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin, manager));
        service.resetPassword("admin01", 2L, 0L, password("AnotherPass@2026"));
        assertTrue(manager.isPasswordChangeRequired());
        assertEquals(1, manager.getAccessVersion());
        assertTrue(encoder.matches("AnotherPass@2026", manager.getUser().getPassword()));
    }
    @Test void cannotResetSelfWithoutCurrentPassword() {
        when(accounts.lockAccounts()).thenReturn(List.of(admin));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("admin01", 1L, 0L, password("AnotherPass@2026")));
    }
    @Test void cannotResetLockedAccount() {
        manager.setActive(false);
        when(accounts.lockAccounts()).thenReturn(List.of(admin, manager));
        assertThrows(IllegalArgumentException.class, () -> service.resetPassword("admin01", 2L, 0L, password("AnotherPass@2026")));
    }
    @Test void changeRejectsWrongCurrentPassword() {
        when(accounts.lockAccounts()).thenReturn(List.of(manager));
        assertThrows(IllegalArgumentException.class, () -> service.changePassword("manager01", change("WrongCurrent!", "AnotherPass@2026")));
        assertEquals(0, manager.getAccessVersion());
    }
    @Test void changeRejectsReuse() {
        when(accounts.lockAccounts()).thenReturn(List.of(manager));
        assertThrows(IllegalArgumentException.class, () -> service.changePassword("manager01", change("OriginalPass@2026", "OriginalPass@2026")));
    }
    @Test void initialChangeEnablesFunctionsAndRevokesSessions() {
        manager.setPasswordChangeRequired(true);
        when(accounts.lockAccounts()).thenReturn(List.of(manager));
        service.changePassword("manager01", change("OriginalPass@2026", "AnotherPass@2026"));
        assertFalse(manager.isPasswordChangeRequired());
        assertEquals(1, manager.getAccessVersion());
    }
    @Test void managerCannotListInternalAccounts() {
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.of(manager));
        assertThrows(AccessDeniedException.class, () -> service.findAccounts("manager01", 0));
    }

    private StaffAccount account(Long id, String name, AppUser.Role role) {
        var user = new AppUser(name, encoder.encode("OriginalPass@2026"), name + "@example.com");
        user.setRole(role); user.setEnabled(true);
        var account = new StaffAccount();
        account.setId(id); account.setUser(user); account.setPasswordChangeRequired(false);
        return account;
    }
    private StaffAccountCreateRequest createRequest() {
        var request = new StaffAccountCreateRequest();
        request.setUsername("manager02"); request.setEmail("manager02@example.com");
        request.setFullName("Nguyễn Văn Thắng"); request.setRole(AppUser.Role.MANAGER);
        request.setPassword("OriginalPass@2026"); request.setConfirmPassword("OriginalPass@2026");
        return request;
    }
    private StaffAccountUpdateRequest update(boolean active, AppUser.Role role) {
        var request = new StaffAccountUpdateRequest();
        request.setActive(active); request.setRole(role); request.setRevision(0L);
        return request;
    }
    private StaffPasswordRequest password(String value) {
        var request = new StaffPasswordRequest();
        request.setPassword(value); request.setConfirmPassword(value);
        return request;
    }
    private StaffPasswordChangeRequest change(String old, String value) {
        var request = new StaffPasswordChangeRequest();
        request.setCurrentPassword(old); request.setPassword(value); request.setConfirmPassword(value);
        return request;
    }
}

