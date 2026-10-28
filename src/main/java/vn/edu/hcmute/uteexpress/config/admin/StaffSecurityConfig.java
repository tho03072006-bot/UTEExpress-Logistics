package vn.edu.hcmute.uteexpress.config.admin;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.*;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.admin.*;

@Configuration
public class StaffSecurityConfig {
    @Bean
    @Order(1)
    public SecurityFilterChain staffFilterChain(HttpSecurity http, StaffAccountRepository accounts,
            PasswordEncoder encoder, StaffLoginAttemptService attempts) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(encoder);
        provider.setUserDetailsService(username -> {
            if (attempts.isBlocked(username)) {
                throw new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ.");
            }
            var account = accounts.findByUserUsername(username)
                    .orElseThrow(() -> new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ."));
            var role = account.getUser().getRole();
            if (role != AppUser.Role.ADMIN && role != AppUser.Role.MANAGER) {
                throw new UsernameNotFoundException("Thông tin đăng nhập không hợp lệ.");
            }
            return new StaffPrincipal(account);
        });
        // Chain riêng có ưu tiên cao hơn khung chung, không đổi quyền của các module khác.
        http.securityMatcher("/admin/**", "/manager/**", "/noi-bo/**")
            .authenticationManager(new ProviderManager(provider))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/noi-bo/dang-nhap", "/noi-bo/tu-choi").permitAll()
                .requestMatchers("/admin/**", "/noi-bo/truy-cap-api", "/noi-bo/truy-cap-api/**").access((authentication, context) ->
                    new org.springframework.security.authorization.AuthorizationDecision(
                        authentication.get().getPrincipal() instanceof StaffPrincipal
                        && authentication.get().getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))))
                .anyRequest().access((authentication, context) ->
                    new org.springframework.security.authorization.AuthorizationDecision(
                        authentication.get().getPrincipal() instanceof StaffPrincipal)))
            .formLogin(form -> form.loginPage("/noi-bo/dang-nhap")
                .loginProcessingUrl("/noi-bo/dang-nhap")
                .successHandler((request, response, authentication) -> {
                    attempts.clearFailures(authentication.getName());
                    var now = java.time.Instant.now();
                    request.getSession().setAttribute(StaffSessionFilter.AUTHENTICATED_AT, now);
                    request.getSession().setAttribute(StaffSessionFilter.LAST_ACTIVITY_AT, now);
                    boolean admin = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    response.sendRedirect(request.getContextPath()
                            + (admin ? "/admin/trang-chu" : "/manager/trang-chu"));
                })
                .failureHandler((request, response, exception) -> {
                    attempts.recordFailure(request.getParameter("username"));
                    // Đang bị khóa tạm thì báo rõ (nhập đúng mật khẩu lúc này vẫn bị từ chối). Lần sai được đếm theo tên
                    // gõ vào, kể cả tên không tồn tại, nên thông báo này không làm lộ tài khoản nào có thật.
                    boolean locked = attempts.isBlocked(request.getParameter("username"));
                    String target = request.getContextPath() + "/noi-bo/dang-nhap?error" + (locked ? "&locked" : "");
                    // Số lần sai gửi qua flash (dùng một lần, lưu trong phiên) để trang đăng nhập hiện "lần N/5"; không đặt lên URL.
                    // Bộ lọc này chạy trước DispatcherServlet nên tự lưu bằng SessionFlashMapManager (cùng cơ chế Spring MVC đọc lại).
                    var flash = new org.springframework.web.servlet.FlashMap();
                    flash.put("staffLoginFailures", attempts.failures(request.getParameter("username")));
                    flash.put("staffLoginMax", attempts.maxAttempts());
                    // Giữ lại tên đã gõ (không giữ mật khẩu) để khỏi nhập lại; Thymeleaf tự escape khi hiển thị.
                    String typed = request.getParameter("username");
                    flash.put("staffLoginUsername", typed == null ? "" : typed.strip().substring(0, Math.min(50, typed.strip().length())));
                    flash.setTargetRequestPath(request.getContextPath() + "/noi-bo/dang-nhap");
                    new org.springframework.web.servlet.support.SessionFlashMapManager().saveOutputFlashMap(flash, request, response);
                    response.sendRedirect(target);
                }))
            .logout(logout -> logout.logoutUrl("/noi-bo/dang-xuat")
                .logoutSuccessUrl("/noi-bo/dang-nhap?logout"))
            .exceptionHandling(errors -> errors.accessDeniedHandler((request, response, exception) -> {
                response.setStatus(403);
                request.getRequestDispatcher("/noi-bo/tu-choi").forward(request, response);
            }))
            .addFilterBefore(new StaffSessionFilter(accounts), AuthorizationFilter.class);
        // Giữ CSRF mặc định cho toàn bộ form nội bộ, kể cả đăng nhập.
        return http.build();
    }
}
