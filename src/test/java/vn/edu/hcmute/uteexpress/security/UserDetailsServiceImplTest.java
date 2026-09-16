package vn.edu.hcmute.uteexpress.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private AppUserRepository appUserRepository;

    @InjectMocks
    private UserDetailsServiceImpl userDetailsService;

    @Test
    void disabledAccountCannotLogIn() {
        AppUser user = user(false);
        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername("nguyentai");

        assertFalse(details.isEnabled());
        assertTrue(details.getAuthorities().stream()
                .anyMatch(role -> role.getAuthority().equals("ROLE_SHIPPER")));
    }

    @Test
    void activatedAccountCanLogIn() {
        when(appUserRepository.findByUsername("nguyentai"))
                .thenReturn(Optional.of(user(true)));

        assertTrue(userDetailsService.loadUserByUsername("nguyentai").isEnabled());
    }

    @Test
    void unknownAccountIsRejected() {
        when(appUserRepository.findByUsername("unknown"))
                .thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByUsername("unknown"));
    }

    private AppUser user(boolean enabled) {
        AppUser user = new AppUser("nguyentai", "encodedPassword", "shipper@example.com");
        user.setRole(AppUser.Role.SHIPPER);
        user.setEnabled(enabled);
        return user;
    }
}
