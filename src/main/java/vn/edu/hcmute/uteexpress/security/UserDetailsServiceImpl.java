package vn.edu.hcmute.uteexpress.security;

import org.springframework.security.core.userdetails.User;
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
    private final PublicLoginAttempts attempts;

    public UserDetailsServiceImpl(AppUserRepository appUserRepository) {
        this(appUserRepository, new PublicLoginAttempts());
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UserDetailsServiceImpl(AppUserRepository appUserRepository, PublicLoginAttempts attempts) {
        this.appUserRepository = appUserRepository;
        this.attempts = attempts;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var attributes = org.springframework.web.context.request.RequestContextHolder.getRequestAttributes();
        if (attributes instanceof org.springframework.web.context.request.ServletRequestAttributes servlet
                && attempts.blocked(username, servlet.getRequest().getRemoteAddr())) {
            throw new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ. Vui lòng thử lại sau.");
        }
        AppUser appUser = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Khong tim thay tai khoan: " + username));

        if (appUser.getRole() != AppUser.Role.USER && appUser.getRole() != AppUser.Role.SHIPPER) {
            throw new UsernameNotFoundException("Vui lòng dùng trang đăng nhập nội bộ.");
        }

        return User.builder()
                .username(appUser.getUsername())
                .password(appUser.getPassword())
                .disabled(!appUser.isEnabled())
                .authorities("ROLE_" + appUser.getRole().name())
                .build();
    }
}
