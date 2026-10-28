package vn.edu.hcmute.uteexpress.security.admin;

import jakarta.servlet.FilterChain;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublicAccountSessionFilterTest {
    @Mock AppUserRepository users;
    @Mock FilterChain chain;
    private AppUser user;
    private PublicAccountPrincipal principal;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    @BeforeEach void setUp() {
        user = new AppUser("user01", "encoded-password-not-plain", "user@example.com"); user.setId(1L); user.setEnabled(true);
        principal = new PublicAccountPrincipal(user); principal.eraseCredentials();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        request = new MockHttpServletRequest("GET", "/nguoi-dung/trang-chu"); request.getSession(); response = new MockHttpServletResponse();
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void validLoginSurvivesErasedCredentials() throws Exception {
        when(users.findByUsername("user01")).thenReturn(Optional.of(user)); run(); verify(chain).doFilter(request, response);
        assertNull(principal.getPassword());
    }
    @Test void lockThenUnlockCannotReviveOldSession() throws Exception {
        user.setLockedByAdmin(true); user.revokeSessions(); user.setLockedByAdmin(false);
        when(users.findByUsername("user01")).thenReturn(Optional.of(user)); run(); assertRevoked();
    }
    @Test void deletedAccountRevokesOldSession() throws Exception {
        when(users.findByUsername("user01")).thenReturn(Optional.empty()); run(); assertRevoked();
    }
    @Test void passwordChangeRevokesOldSession() throws Exception {
        user.setPassword("different-encoded-password"); when(users.findByUsername("user01")).thenReturn(Optional.of(user)); run(); assertRevoked();
    }
    @Test void roleChangeRevokesOldSession() throws Exception {
        user.setRole(AppUser.Role.SHIPPER); when(users.findByUsername("user01")).thenReturn(Optional.of(user)); run(); assertRevoked();
    }
    @Test void newAccountReusingUsernameCannotReuseOldSession() throws Exception {
        user.setId(2L); when(users.findByUsername("user01")).thenReturn(Optional.of(user)); run(); assertRevoked();
    }
    @Test void cloudFailureFailsClosedWithoutSqlLeak() throws Exception {
        when(users.findByUsername("user01")).thenThrow(new DataAccessResourceFailureException("private SQL password details")); run();
        assertEquals(503, response.getStatus()); assertFalse(response.getContentAsString().contains("private SQL")); verifyNoInteractions(chain);
    }
    private void run() throws Exception { new PublicAccountSessionFilter(users).doFilter(request, response, chain); }
    private void assertRevoked() {
        assertEquals("/dang-nhap?expired", response.getRedirectedUrl()); assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertNull(request.getSession(false)); verifyNoInteractions(chain);
    }
}
