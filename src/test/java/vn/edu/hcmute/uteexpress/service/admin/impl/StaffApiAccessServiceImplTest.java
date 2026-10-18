package vn.edu.hcmute.uteexpress.service.admin.impl;

import jakarta.validation.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.hcmute.uteexpress.dto.admin.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.JwtTokenProvider;
import vn.edu.hcmute.uteexpress.security.admin.StaffLoginAttemptService;
import vn.edu.hcmute.uteexpress.service.admin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StaffApiAccessServiceImplTest {
    private static ValidatorFactory factory;
    private StaffAccountRepository accounts;
    private StaffApiAccessServiceImpl service;
    private StaffAccessPolicy access;
    private JwtTokenProvider tokens;
    private AuditService audit;
    private StaffAccount admin, manager;
    private StaffLoginAttemptService attempts;
    private PasswordEncoder encoder;
    @BeforeAll static void open() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setUp() {
        accounts = mock(StaffAccountRepository.class); audit = mock(AuditService.class); encoder = mock(PasswordEncoder.class);
        admin = account(1L, "admin01", AppUser.Role.ADMIN); manager = account(2L, "manager01", AppUser.Role.MANAGER);
        when(accounts.findByUserUsername("admin01")).thenReturn(Optional.of(admin));
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.of(manager));
        when(accounts.lockAccounts()).thenReturn(List.of(admin, manager));
        when(encoder.matches(eq("CurrentPassword@2026"), anyString())).thenReturn(true);
        access = new StaffAccessPolicy(accounts, factory.getValidator());
        var clock = Clock.fixed(Instant.parse("2026-10-18T06:00:00Z"), ZoneOffset.UTC);
        attempts = new StaffLoginAttemptService(clock);
        tokens = new JwtTokenProvider(true, "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", clock);
        service = new StaffApiAccessServiceImpl(accounts, access, tokens, encoder, attempts, audit);
    }
    @Test void issuedTokenIsScopedToActorAndAuditDoesNotContainSecrets() {
        String token = service.issueToken("admin01", new ApiAccessRequest("CurrentPassword@2026"));
        assertEquals("admin01", tokens.parseToken(token).getSubject());
        verify(audit).record(eq("admin01"), eq("API_TOKEN_ISSUE"), eq("staff:1"), argThat(detail -> !detail.contains(token) && !detail.contains("CurrentPassword")));
    }
    @Test void onlyAdminCanRevokeAnyStaffAccount() {
        assertThrows(AccessDeniedException.class, () -> service.revokeAccess("manager01", 1L, validRevoke()));
        verify(accounts, never()).lockAccounts(); verifyNoInteractions(audit);
    }
    @Test void hiddenTokenToolCannotBeUsedByManagerThroughDirectServiceCall() {
        assertThrows(AccessDeniedException.class, () -> service.issueToken("manager01",new ApiAccessRequest("CurrentPassword@2026")));
        verify(accounts,never()).lockAccounts(); verifyNoInteractions(encoder,audit);
    }
    @Test void revocationPreservesPasswordRoleAndStatusButInvalidatesJwt() {
        String token = tokens.generateToken(manager); String password = manager.getUser().getPassword();
        service.revokeAccess("admin01", 2L, validRevoke());
        assertEquals(password, manager.getUser().getPassword()); assertTrue(manager.isActive());
        assertEquals(AppUser.Role.MANAGER, manager.getUser().getRole());
        assertFalse(tokens.matchesAccount(tokens.parseToken(token), manager));
        verify(accounts).flush(); verify(audit).record(eq("admin01"), eq("ACCESS_REVOKE"), eq("staff:2"), contains("Nghi ngờ lộ token"));
    }
    @Test void selfRevocationIsExplicitlyAllowedAndAlsoInvalidatesOwnToken() {
        String token = tokens.generateToken(admin);
        assertThrows(IllegalArgumentException.class, () -> service.revokeAccess("admin01", 1L, validRevoke()));
        assertTrue(tokens.matchesAccount(tokens.parseToken(token), admin));
        service.revokeAccess("admin01", 1L, new AccessRevocationRequest(0L,"CurrentPassword@2026","Nghi ngờ lộ token",true));
        assertFalse(tokens.matchesAccount(tokens.parseToken(token), admin));
    }
    @Test void staleVersionAndMissingTargetCannotRevoke() {
        assertThrows(StaffConflictException.class, () -> service.revokeAccess("admin01", 2L, new AccessRevocationRequest(1L,"CurrentPassword@2026","Nghi ngờ lộ token",false)));
        assertThrows(IllegalArgumentException.class, () -> service.revokeAccess("admin01", 99L, validRevoke()));
        assertEquals(0, manager.getAccessVersion()); verifyNoInteractions(audit);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", "bad", "<script>bad</script>", "bad\u0000note", "    a    "})
    void invalidReasonsCannotRevoke(String reason) {
        assertThrows(IllegalArgumentException.class, () -> service.revokeAccess("admin01", 2L, new AccessRevocationRequest(0L,"CurrentPassword@2026",reason,false)));
        assertEquals(0, manager.getAccessVersion()); verifyNoInteractions(audit);
    }
    @Test void wrongPasswordThrottlesAndNeverIssuesToken() {
        for (int i = 0; i < 5; i++) assertThrows(IllegalArgumentException.class,
            () -> service.issueToken("admin01", new ApiAccessRequest("wrong-password")));
        assertThrows(IllegalArgumentException.class, () -> service.issueToken("admin01", new ApiAccessRequest("CurrentPassword@2026")));
        verify(encoder, times(5)).matches(any(), any()); verifyNoInteractions(audit);
    }
    @Test void blockedStaffPasswordChangeRequiredAndOtpRolesCannotIssue() {
        admin.setPasswordChangeRequired(true);
        assertThrows(AccessDeniedException.class, () -> service.issueToken("admin01", new ApiAccessRequest("CurrentPassword@2026")));
        admin.setPasswordChangeRequired(false); admin.getUser().setLockedByAdmin(true);
        assertThrows(AccessDeniedException.class, () -> service.issueToken("admin01", new ApiAccessRequest("CurrentPassword@2026")));
        admin.getUser().setLockedByAdmin(false); admin.getUser().setRole(AppUser.Role.USER);
        assertThrows(AccessDeniedException.class, () -> service.issueToken("admin01", new ApiAccessRequest("CurrentPassword@2026")));
        verifyNoInteractions(audit);
    }
    @Test void invalidPaginationIdsMissingRevisionAndOversizeUtf8PasswordReject() {
        assertThrows(IllegalArgumentException.class, () -> service.findAccess("admin01", -1));
        assertThrows(IllegalArgumentException.class, () -> service.findAccess("admin01", 10001));
        assertThrows(IllegalArgumentException.class, () -> service.revokeAccess("admin01", 0L, validRevoke()));
        assertThrows(IllegalArgumentException.class, () -> service.revokeAccess("admin01", 2L, new AccessRevocationRequest(null,"CurrentPassword@2026","Nghi ngờ lộ token",false)));
        assertThrows(IllegalArgumentException.class, () -> service.issueToken("admin01", new ApiAccessRequest("ậ".repeat(30))));
        verifyNoInteractions(audit);
    }
    private static AccessRevocationRequest validRevoke() { return new AccessRevocationRequest(0L,"CurrentPassword@2026","Nghi ngờ lộ token",false); }
    private static StaffAccount account(Long id, String username, AppUser.Role role) {
        var user = new AppUser(username,"hash-not-exposed",username + "@example.com"); user.setId(id); user.setRole(role); user.setEnabled(true);
        var staff = new StaffAccount(); staff.setId(id); staff.setUser(user); staff.setPasswordChangeRequired(false); return staff;
    }
}
