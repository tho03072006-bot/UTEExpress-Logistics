package vn.edu.hcmute.uteexpress.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cau hinh Spring Security cho toan he thong.
 *
 * Da bat dang nhap that (UserDetailsServiceImpl doc tu bang app_user) nen tu day
 * "/nguoi-dung/**" bat buoc dang nhap. Cac vai tro Shipper/Manager/Admin van dang
 * permitAll tam thoi (chua co du lieu that de test) - siet lai khi TV2/TV3 lam xong
 * dang nhap phan quyen theo role cho phan cua minh.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Dung de ma hoa mat khau truoc khi luu DB (theo dung yeu cau de bai)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            vn.edu.hcmute.uteexpress.repository.AppUserRepository users,
            vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository staff) throws Exception {
        var pageRequestCache = new vn.edu.hcmute.uteexpress.security.PageRequestCache();
        http
            .requestCache(cache -> cache.requestCache(pageRequestCache))
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                    vn.edu.hcmute.uteexpress.security.PageRequestCache::isBackgroundRequest))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/uploads/giao-hang/**", "/private-proof/**").denyAll()
                .requestMatchers("/", "/trang-chu", "/tra-cuu/**", "/uoc-tinh-cuoc",
                        "/dang-ky", "/dang-nhap", "/xac-thuc-otp",
                        "/quen-mat-khau", "/dat-lai-mat-khau",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/api/tracking/**").permitAll()
                // Trang lỗi chung (templates/error.html) phải mở được cả khi chưa đăng nhập.
                .requestMatchers("/error").permitAll()
                .requestMatchers("/nguoi-dung/**").authenticated()
             // TV2: khu vuc shipper chi danh cho vai tro SHIPPER.
             // TODO (TV3): bao ve khu vuc manager/admin khi chuc nang quan ly hoan thien.
                .requestMatchers("/shipper/**").hasRole("SHIPPER")
                .requestMatchers("/manager/**", "/admin/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/dang-nhap")
                .successHandler((request, response, authentication) -> {
                    var handler = new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
                    handler.setRequestCache(pageRequestCache);
                    handler.setDefaultTargetUrl(authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_SHIPPER")) ? "/shipper/trang-chu" : "/nguoi-dung/trang-chu");
                    handler.onAuthenticationSuccess(request, response, authentication);
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            // Bảo vệ form Shipper và gửi khiếu nại; các route cũ khác giữ chính sách hiện có.
            .csrf(csrf -> csrf.requireCsrfProtectionMatcher(request -> {
                String path = request.getServletPath();
                if (path.isEmpty()) path = request.getRequestURI().substring(request.getContextPath().length());
                return !java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod())
                        && (path.startsWith("/shipper/") || path.matches("/nguoi-dung/don-hang/[0-9]+/khieu-nai"));
            }))
            .addFilterBefore(new vn.edu.hcmute.uteexpress.security.admin.PublicAccountSessionFilter(users),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .addFilterBefore(new vn.edu.hcmute.uteexpress.security.admin.StaffSessionFilter(staff),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);

        return http.build();
    }
}
