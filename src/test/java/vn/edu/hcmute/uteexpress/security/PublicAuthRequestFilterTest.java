package vn.edu.hcmute.uteexpress.security;

import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicAuthRequestFilterTest {
    @Test void unavailableDatabaseCannotBypassLockoutOrCountAsWrongPassword() throws Exception {
        var users=mock(AppUserRepository.class);var guard=new PublicAuthRateGuard(Clock.systemUTC());
        when(users.findByUsername("customer")).thenThrow(new DataAccessResourceFailureException("test outage"));
        var request=request("customer");var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        new PublicAuthRequestFilter(guard,users).doFilter(request,response,chain);
        assertEquals(503,response.getStatus());assertEquals("no-store",response.getHeader("Cache-Control"));
        assertEquals("5",response.getHeader("Retry-After"));assertEquals(0,guard.loginFailures("unknown:customer"));
        verifyNoInteractions(chain);
    }
    @Test void unknownInputCannotImpersonateAnInternalAccountRateKey() throws Exception {
        var users=mock(AppUserRepository.class);var guard=new PublicAuthRateGuard(Clock.systemUTC());
        var account=new AppUser("customer","hash","customer@test.invalid");account.setId(7L);
        when(users.findByUsername("customer")).thenReturn(Optional.of(account));
        var filter=new PublicAuthRequestFilter(guard,users);var known=request("customer");var unknown=request("account:7");
        filter.doFilter(known,new MockHttpServletResponse(),mock(FilterChain.class));
        filter.doFilter(unknown,new MockHttpServletResponse(),mock(FilterChain.class));
        assertEquals("account:7",PublicAuthRequestFilter.loginIdentity(known));
        assertNotEquals(PublicAuthRequestFilter.loginIdentity(known),PublicAuthRequestFilter.loginIdentity(unknown));
    }
    private MockHttpServletRequest request(String username) {
        var request=new MockHttpServletRequest("POST","/dang-nhap");request.addParameter("username",username);
        request.addParameter("password","ExamplePass123");return request;
    }
}
