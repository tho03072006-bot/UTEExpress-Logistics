package vn.edu.hcmute.uteexpress.config;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffBranchPhotoAdviceTest {
    private final StaffBranchRepository repository = mock(StaffBranchRepository.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<StaffBranchRepository> provider = mock(ObjectProvider.class);
    private final StaffBranchPhotoAdvice advice = new StaffBranchPhotoAdvice(provider);

    private static TestingAuthenticationToken user(String name, String... roles) {
        var token = new TestingAuthenticationToken(name, "x", roles);
        token.setAuthenticated(true);
        return token;
    }

    @Test void managerGetsOwnBranchCodeAndItIsLookedUpOncePerSession() {
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.findBranchCodeByUsername("manager_han")).thenReturn(Optional.of("HAN"));
        var request = new MockHttpServletRequest(); request.getSession(true);
        assertEquals("han", advice.staffBranchPhoto(user("manager_han", "ROLE_MANAGER"), request));
        assertEquals("han", advice.staffBranchPhoto(user("manager_han", "ROLE_MANAGER"), request));
        verify(repository, times(1)).findBranchCodeByUsername("manager_han");
    }

    @Test void adminOtherRolesAndAnonymousNeverQuery() {
        var request = new MockHttpServletRequest();
        assertNull(advice.staffBranchPhoto(user("admin", "ROLE_ADMIN", "ROLE_MANAGER"), request));
        assertNull(advice.staffBranchPhoto(user("shipper_sgn1", "ROLE_SHIPPER"), request));
        assertNull(advice.staffBranchPhoto(null, request));
        verifyNoInteractions(provider, repository);
    }

    @Test void managerWithoutBranchOrUnsafeCodeOrDatabaseErrorFallsBackToDefaultPhoto() {
        when(provider.getIfAvailable()).thenReturn(repository);
        when(repository.findBranchCodeByUsername("m1")).thenReturn(Optional.empty());
        when(repository.findBranchCodeByUsername("m2")).thenReturn(Optional.of("x\" onload"));
        when(repository.findBranchCodeByUsername("m3")).thenThrow(new IllegalStateException("db"));
        assertNull(advice.staffBranchPhoto(user("m1", "ROLE_MANAGER"), new MockHttpServletRequest()));
        assertNull(advice.staffBranchPhoto(user("m2", "ROLE_MANAGER"), new MockHttpServletRequest()));
        assertNull(advice.staffBranchPhoto(user("m3", "ROLE_MANAGER"), new MockHttpServletRequest()));
    }
}
