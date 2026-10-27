package vn.edu.hcmute.uteexpress.controller.profile;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DisplayNameAdviceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final DisplayNameAdvice advice = new DisplayNameAdvice(users);
    private TestingAuthenticationToken auth(String username) { return new TestingAuthenticationToken(username, "test", "ROLE_USER"); }
    private AppUser account(String name) { var user = new AppUser(); user.setFullName(name); return user; }
    private MockHttpServletRequest request() { var request = new MockHttpServletRequest(); request.getSession(true); return request; }

    @Test void sameAccountUsesItsCachedName() {
        when(users.findByUsername("first")).thenReturn(Optional.of(account(" Người thứ nhất ")));
        var request = request();
        assertEquals("Người thứ nhất", advice.displayName(auth("first"), request));
        assertEquals("Người thứ nhất", advice.displayName(auth("first"), request));
        verify(users, times(1)).findByUsername("first");
    }
    @Test void changingAccountInTheSameSessionLoadsTheNewName() {
        when(users.findByUsername("first")).thenReturn(Optional.of(account("Người thứ nhất")));
        when(users.findByUsername("second")).thenReturn(Optional.of(account("Người thứ hai")));
        var request = request();
        assertEquals("Người thứ nhất", advice.displayName(auth("first"), request));
        assertEquals("Người thứ hai", advice.displayName(auth("second"), request));
        assertEquals("Người thứ hai", advice.displayName(auth("second"), request));
        verify(users, times(1)).findByUsername("second");
    }
    @Test void oldUnscopedCacheCannotSupplyAnotherAccountsName() {
        var request = request(); request.getSession().setAttribute(DisplayNameAdvice.SESSION_KEY, "Tên tài khoản cũ");
        when(users.findByUsername("current")).thenReturn(Optional.of(account("Người hiện tại")));
        assertEquals("Người hiện tại", advice.displayName(auth("current"), request));
    }
    @Test void blankOrMissingFullNameFallsBackToTheCurrentUsername() {
        when(users.findByUsername("blank")).thenReturn(Optional.of(account("   ")));
        when(users.findByUsername("missing")).thenReturn(Optional.empty());
        var request = request();
        assertEquals("blank", advice.displayName(auth("blank"), request));
        assertEquals("missing", advice.displayName(auth("missing"), request));
    }
    @Test void anonymousDoesNotReadNames() {
        var token = new AnonymousAuthenticationToken("test", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"));
        assertNull(advice.displayName(token, request()));
        assertNull(advice.displayName(null, request())); verifyNoInteractions(users);
    }
    @Test void requestWithoutSessionDoesNotCreateOneOrQueryNames() {
        var request = new MockHttpServletRequest();
        assertNull(advice.displayName(auth("first"), request));
        assertNull(request.getSession(false)); verifyNoInteractions(users);
    }
}
