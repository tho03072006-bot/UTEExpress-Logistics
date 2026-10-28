package vn.edu.hcmute.uteexpress.security.admin;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.uteexpress.config.*;
import vn.edu.hcmute.uteexpress.config.admin.*;
import vn.edu.hcmute.uteexpress.controller.api.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.JwtTokenProvider;
import vn.edu.hcmute.uteexpress.service.admin.StaffApiAccessService;
import vn.edu.hcmute.uteexpress.service.manager.StaffWorkbenchService;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = {StaffOperationsApiController.class, TrackingApiController.class})
@Import({SecurityConfig.class, StaffSecurityConfig.class, StaffApiSecurityConfig.class, TimeConfig.class, JwtTokenProvider.class, StaffLoginAttemptService.class})
@TestPropertySource(properties = {"app.jwt.api.enabled=true", "app.jwt.api.signing-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="})
class StaffApiSecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JwtTokenProvider tokens;
    @MockBean StaffAccountRepository accounts;
    @MockBean vn.edu.hcmute.uteexpress.repository.admin.AdminUserRepository adminUsers;
    @MockBean vn.edu.hcmute.uteexpress.repository.AppUserRepository users;
    @MockBean StaffWorkbenchService workbench;
    @MockBean StaffApiAccessService access;
    @MockBean vn.edu.hcmute.uteexpress.service.OrderDraftService drafts;
    @MockBean vn.edu.hcmute.uteexpress.service.OrderService orders;
    private StaffAccount manager, admin;
    @BeforeEach void setUp() {
        manager = account(2L, "manager01", AppUser.Role.MANAGER); admin = account(1L, "admin01", AppUser.Role.ADMIN);
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.of(manager));
        when(accounts.findByUserUsername("admin01")).thenReturn(Optional.of(admin));
        when(workbench.getWorkQueues(anyString())).thenReturn(List.of());
        when(access.findAccess(anyString(), anyInt())).thenReturn(org.springframework.data.domain.Page.empty());
    }
    @Test void missingInvalidOrUrlTokenIsUnauthorizedNotRedirected() throws Exception {
        mvc.perform(get("/api/internal/operations/queues")).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/internal/operations/queues").header("Authorization", "Bearer not.a.token")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/internal/operations/queues").param("token", tokens.generateToken(manager))).andExpect(status().isUnauthorized());
        verifyNoInteractions(workbench);
    }
    @Test void sessionCannotSubstituteForBearer() throws Exception {
        var principal = new StaffPrincipal(admin);
        var session = new MockHttpSession();
        session.setAttribute("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(
            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())));
        mvc.perform(get("/api/internal/operations/queues").session(session)).andExpect(status().isUnauthorized());
    }
    @Test void validManagerCanOnlyReadOperationsAndNoSessionIsCreated() throws Exception {
        mvc.perform(get("/api/internal/operations/queues").header("Authorization", "Bearer " + tokens.generateToken(manager)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(result -> Assertions.assertNull(result.getRequest().getSession(false)));
        mvc.perform(get("/api/internal/admin/access").header("Authorization", "Bearer " + tokens.generateToken(manager))).andExpect(status().isForbidden());
        verify(workbench).getWorkQueues("manager01"); verifyNoInteractions(access);
    }
    @Test void adminCanReadAccessButNoRoleCanWriteThroughJwt() throws Exception {
        String token = tokens.generateToken(admin);
        mvc.perform(get("/api/internal/admin/access").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        mvc.perform(post("/api/internal/admin/access").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/internal/operations/tracking").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }
    @Test void duplicateMalformedOrOversizedAuthorizationIsRejected() throws Exception {
        String token = tokens.generateToken(manager);
        for (String header : List.of("Basic abc", "Bearer  " + token, "Bearer " + "a".repeat(4097))) {
            mvc.perform(get("/api/internal/operations/queues").header("Authorization", header)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/internal/operations/queues").header("Authorization", "Bearer " + token, "Bearer " + token)).andExpect(status().isUnauthorized());
    }
    @Test void revokedDisabledMissingAndChangedRoleAreRejectedImmediately() throws Exception {
        String token = tokens.generateToken(manager); manager.revokeSessions();
        mvc.perform(get("/api/internal/operations/queues").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        token = tokens.generateToken(manager); manager.getUser().setEnabled(false);
        mvc.perform(get("/api/internal/operations/queues").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        manager.getUser().setEnabled(true); token = tokens.generateToken(manager); manager.getUser().setRole(AppUser.Role.ADMIN);
        mvc.perform(get("/api/internal/admin/access").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.empty());
        mvc.perform(get("/api/internal/operations/queues").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        verifyNoInteractions(workbench, access);
    }
    @Test void databaseFailureFailsClosedWith503() throws Exception {
        String token = tokens.generateToken(manager);
        when(accounts.findByUserUsername("manager01")).thenThrow(new DataAccessResourceFailureException("private database detail"));
        mvc.perform(get("/api/internal/operations/queues").header("Authorization", "Bearer " + token))
            .andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private database"))));
        verifyNoInteractions(workbench);
    }
    @Test void publicTrackingRemainsAnonymousButDoesNotRevealContactData() throws Exception {
        var order = new vn.edu.hcmute.uteexpress.entity.Order(); order.setTrackingCode("UTE123");
        order.setStatus(vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP);
        order.setServiceType(vn.edu.hcmute.uteexpress.entity.Order.ServiceType.STANDARD);
        order.setReceiverName("Thông tin riêng tư");
        when(orders.findByTrackingCode("UTE123")).thenReturn(Optional.of(order));
        mvc.perform(get("/api/tracking/UTE123")).andExpect(status().isOk())
            .andExpect(jsonPath("$.trackingCode").value("UTE123"))
            .andExpect(jsonPath("$.receiverName").doesNotExist()).andExpect(jsonPath("$.receiverPhone").doesNotExist())
            .andExpect(jsonPath("$.receiverAddress").doesNotExist());
        mvc.perform(get("/api/tracking/UNKNOWN")).andExpect(status().isNotFound());
    }
    @Test void invalidPublicTrackingCodeDoesNotQueryDatabase() throws Exception {
        for (String code : List.of("UTE 123", "UTE123<script>", "A".repeat(21))) {
            mvc.perform(get("/api/tracking/{code}", code)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(orders);
    }
    @Test void legacyNullableTrackingFieldsDoNotCauseServerError() throws Exception {
        var order = new vn.edu.hcmute.uteexpress.entity.Order(); order.setTrackingCode("UTE123");
        order.setServiceType(null); order.setCreatedAt(null);
        when(orders.findByTrackingCode("UTE123")).thenReturn(Optional.of(order));
        mvc.perform(get("/api/tracking/UTE123")).andExpect(status().isOk()).andExpect(jsonPath("$.trackingCode").value("UTE123"));
    }

    private static StaffAccount account(Long id, String username, AppUser.Role role) {
        var user = new AppUser(username, "not-exposed-hash", username + "@example.com"); user.setId(id); user.setRole(role); user.setEnabled(true);
        var account = new StaffAccount(); account.setId(id); account.setUser(user); account.setPasswordChangeRequired(false); return account;
    }
}
