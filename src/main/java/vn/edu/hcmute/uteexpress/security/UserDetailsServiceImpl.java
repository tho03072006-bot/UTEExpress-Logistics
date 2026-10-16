package vn.edu.hcmute.uteexpress.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;

/**
 * Cau noi giua bang app_user va Spring Security: Spring Boot tu dong dung Bean
 * nay (cung voi PasswordEncoder o SecurityConfig) de kiem tra dang nhap - khong
 * can tu tao AuthenticationManager tay.
 * Tai khoan enabled=false (chua xac thuc OTP) se bi Spring Security chan dang nhap.
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final AppUserRepository appUserRepository;

    public UserDetailsServiceImpl(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Khong tim thay tai khoan: " + username));

        if (appUser.getRole() == AppUser.Role.ADMIN || appUser.getRole() == AppUser.Role.MANAGER) {
            throw new UsernameNotFoundException("Tài khoản nội bộ phải dùng trang đăng nhập nội bộ.");
        }
        return new vn.edu.hcmute.uteexpress.security.admin.PublicAccountPrincipal(appUser);
    }
}
