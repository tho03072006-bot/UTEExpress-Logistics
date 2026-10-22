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
 * User và Shipper dùng khu vực riêng. Admin/Manager được bảo vệ bởi StaffSecurityConfig
 * có ưu tiên cao hơn, với tài khoản nội bộ được cấp và CSRF mặc định.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public vn.edu.hcmute.uteexpress.security.PublicAuthRateGuard publicAuthRateGuard() {
        return new vn.edu.hcmute.uteexpress.security.PublicAuthRateGuard(java.time.Clock.systemUTC());
    }

    // Dung de ma hoa mat khau truoc khi luu DB (theo dung yeu cau de bai)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
            vn.edu.hcmute.uteexpress.repository.AppUserRepository users,
            vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository staff,
            vn.edu.hcmute.uteexpress.security.PublicAuthRateGuard authRateGuard) throws Exception {
        var pageRequestCache = new vn.edu.hcmute.uteexpress.security.PageRequestCache();
        http
            .requestCache(cache -> cache.requestCache(pageRequestCache))
            .exceptionHandling(errors -> errors.defaultAuthenticationEntryPointFor(
                    new org.springframework.security.web.authentication.HttpStatusEntryPoint(org.springframework.http.HttpStatus.UNAUTHORIZED),
                    vn.edu.hcmute.uteexpress.security.PageRequestCache::isBackgroundRequest))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/uploads/**", "/private-proof/**").denyAll()
                .requestMatchers("/tep-danh-gia/**").permitAll()
                .requestMatchers("/", "/trang-chu", "/tra-cuu/**", "/uoc-tinh-cuoc",
                        "/dang-ky", "/dang-nhap", "/xac-thuc-otp", "/xac-thuc-otp/gui-lai",
                        "/quen-mat-khau", "/dat-lai-mat-khau",
                        "/css/**", "/js/**", "/images/**", "/api/tracking/**").permitAll()
                // Trang lỗi chung (templates/error.html) phải mở được cả khi chưa đăng nhập.
                .requestMatchers("/error").permitAll()
                // Kiểm tra "app còn sống" cho Render/giám sát: chỉ trả "ok", không đọc dữ liệu.
                .requestMatchers("/healthz").permitAll()
                .requestMatchers("/api/assistant/**").hasAnyRole("USER","SHIPPER")
                .requestMatchers("/api/payments/**").hasRole("USER")
                .requestMatchers("/nguoi-dung/**").hasRole("USER")
                .requestMatchers("/shipper/**").hasRole("SHIPPER")
                .requestMatchers("/manager/**", "/admin/**").denyAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/dang-nhap")
                .successHandler((request, response, authentication) -> {
                    authRateGuard.loginSucceeded(authentication.getName());
                    authRateGuard.loginSucceeded(request.getParameter("username")); // gõ email thì xóa cả bộ đếm theo email
                    vn.edu.hcmute.uteexpress.security.PublicAuthRequestFilter.forgetLoginFailures(request);
                    var handler = new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
                    handler.setRequestCache(pageRequestCache);
                    handler.setDefaultTargetUrl(authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_SHIPPER")) ? "/shipper/trang-chu" : "/nguoi-dung/trang-chu");
                    handler.onAuthenticationSuccess(request, response, authentication);
                })
                .failureHandler((request,response,exception) -> {
                    authRateGuard.loginFailed(request.getParameter("username"));
                    vn.edu.hcmute.uteexpress.security.PublicAuthRequestFilter.rememberLoginFailure(request,authRateGuard,request.getParameter("username"));
                    response.sendRedirect(request.getContextPath()+"/dang-nhap?error"
                            + (authRateGuard.loginBlocked(request.getParameter("username")) ? "&locked" : ""));
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            // Bảo vệ mọi POST, kể cả đăng nhập/đăng ký/OTP. SockJS chỉ vận chuyển sự kiện đọc,
            // kiểm tra cùng origin và chặn SEND vào topic; không phải API thay đổi nghiệp vụ.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/ws-tracking/**"))
            .addFilterBefore(new vn.edu.hcmute.uteexpress.security.PublicAuthRequestFilter(authRateGuard),
                org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new vn.edu.hcmute.uteexpress.security.admin.PublicAccountSessionFilter(users),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .addFilterBefore(new vn.edu.hcmute.uteexpress.security.admin.StaffSessionFilter(staff),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class)
            .addFilterAfter(new vn.edu.hcmute.uteexpress.security.AssistantRequestFilter(),
                org.springframework.security.web.access.intercept.AuthorizationFilter.class);

        return http.build();
    }
}
