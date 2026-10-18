package vn.edu.hcmute.uteexpress.security.admin;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffSessionTimeoutTest {
    private static final Instant NOW = Instant.parse("2026-10-18T06:00:00Z");
    private MockHttpSession session;
    private StaffSessionFilter filter;
    @BeforeEach void setUp() {
        var accounts = mock(StaffAccountRepository.class);
        var user = new AppUser("manager01", "hash-not-exposed", "manager@example.com"); user.setId(1L); user.setEnabled(true); user.setRole(AppUser.Role.MANAGER);
        var staff = new StaffAccount(); staff.setUser(user); staff.setPasswordChangeRequired(false);
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.of(staff));
        var principal = new StaffPrincipal(staff);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        session = new MockHttpSession(); session.setMaxInactiveInterval(3600);
        session.setAttribute(StaffSessionFilter.AUTHENTICATED_AT,NOW.minusSeconds(7200));
        session.setAttribute(StaffSessionFilter.LAST_ACTIVITY_AT,NOW.minusSeconds(1200));
        filter = new StaffSessionFilter(accounts,Clock.fixed(NOW,ZoneOffset.UTC));
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); }
    @Test void foregroundNavigationRenewsIdleButNotAbsoluteTime() throws Exception {
        var response = execute("GET",false);
        assertEquals(200,response.getStatus()); assertEquals(NOW,session.getAttribute(StaffSessionFilter.LAST_ACTIVITY_AT));
        assertEquals(NOW.minusSeconds(7200),session.getAttribute(StaffSessionFilter.AUTHENTICATED_AT));
    }
    @Test void backgroundPollingDoesNotRenewIdleTime() throws Exception {
        assertEquals(200,execute("GET",true).getStatus());
        assertEquals(NOW.minusSeconds(1200),session.getAttribute(StaffSessionFilter.LAST_ACTIVITY_AT));
    }
    @Test void explicitFormPostCountsAsActivity() throws Exception {
        assertEquals(200,execute("POST",true).getStatus()); assertEquals(NOW,session.getAttribute(StaffSessionFilter.LAST_ACTIVITY_AT));
    }
    @Test void exactlySixtyMinutesIdleExpiresEvenWithPolling() throws Exception {
        session.setAttribute(StaffSessionFilter.LAST_ACTIVITY_AT,NOW.minusSeconds(3600));
        assertEquals(401,execute("GET",true).getStatus()); assertTrue(session.isInvalid());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void justBeforeIdleDeadlineStillWorks() throws Exception {
        session.setAttribute(StaffSessionFilter.LAST_ACTIVITY_AT,NOW.minusSeconds(3599)); assertEquals(200,execute("GET",true).getStatus());
    }
    @Test void eightHourAbsoluteLimitAppliesEvenWhenRecentlyActive() throws Exception {
        session.setAttribute(StaffSessionFilter.AUTHENTICATED_AT,NOW.minus(Duration.ofHours(8)));
        session.setAttribute(StaffSessionFilter.LAST_ACTIVITY_AT,NOW.minusSeconds(1));
        var response = execute("GET",false); assertEquals(302,response.getStatus());
        assertEquals("/noi-bo/dang-nhap?expired",response.getRedirectedUrl()); assertTrue(session.isInvalid());
    }
    @Test void nonStaffSessionIsNotChangedByStaffFilter() throws Exception {
        SecurityContextHolder.clearContext(); assertEquals(200,execute("GET",false).getStatus());
        assertEquals(NOW.minusSeconds(1200),session.getAttribute(StaffSessionFilter.LAST_ACTIVITY_AT));
    }
    private MockHttpServletResponse execute(String method, boolean background) throws Exception {
        var request = new MockHttpServletRequest(method,"/manager/trang-chu"); request.setSession(session);
        if (background) request.addHeader("X-Requested-With","XMLHttpRequest");
        var response = new MockHttpServletResponse(); filter.doFilter(request,response,(req,res)->{}); return response;
    }
}
