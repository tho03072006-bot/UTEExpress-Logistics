package vn.edu.hcmute.uteexpress.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Bao ve cac trang cong khai, User va Shipper bang dang nhap session va CSRF.
 * StaffSecurityConfig xu ly rieng Admin/Manager voi filter chain uu tien cao hon.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private vn.edu.hcmute.uteexpress.security.PublicLoginAttempts publicAttempts;

    // Dung de ma hoa mat khau truoc khi luu DB (theo dung yeu cau de bai)
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/trang-chu", "/tra-cuu/**", "/uoc-tinh-cuoc",
                        "/dang-ky", "/dang-nhap", "/xac-thuc-otp", "/xac-thuc-otp/gui-lai",
                        "/quen-mat-khau", "/dat-lai-mat-khau",
                        "/css/**", "/js/**", "/images/**", "/uploads/danh-gia/**", "/api/tracking/**").permitAll()
                .requestMatchers("/thanh-toan/momo/ket-qua", "/thanh-toan/momo/ipn").permitAll()
                .requestMatchers("/uploads/**").denyAll()
                .requestMatchers("/nguoi-dung/**").hasRole("USER")
                .requestMatchers("/shipper/**").hasRole("SHIPPER")
                // Fail closed neu chain noi bo khong duoc nap.
                .requestMatchers("/manager/**", "/admin/**", "/noi-bo/**").denyAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/dang-nhap")
                .successHandler((request, response, authentication) -> {
                    if (publicAttempts != null) publicAttempts.clear(authentication.getName(), request.getRemoteAddr());
                    var handler = new org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler();
                    boolean shipper = authentication.getAuthorities().stream()
                            .anyMatch(role -> role.getAuthority().equals("ROLE_SHIPPER"));
                    handler.setDefaultTargetUrl(shipper ? "/shipper/trang-chu" : "/nguoi-dung/trang-chu");
                    handler.onAuthenticationSuccess(request, response, authentication);
                })
                .failureHandler((request, response, exception) -> {
                    if (publicAttempts != null) publicAttempts.fail(request.getParameter("username"), request.getRemoteAddr());
                    response.sendRedirect(request.getContextPath() + "/dang-nhap?error");
                })
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            // Thymeleaf tu them CSRF token vao form POST dung th:action.
            // SockJS POST transport khong gui duoc token trong HTTP form/header.
            // Chi mien cac transport nay; /ws-tracking/** van bat buoc dang nhap.
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                new AntPathRequestMatcher("/thanh-toan/momo/ipn", "POST"),
                new AntPathRequestMatcher("/ws-tracking/*/*/xhr", "POST"),
                new AntPathRequestMatcher("/ws-tracking/*/*/xhr_streaming", "POST"),
                new AntPathRequestMatcher("/ws-tracking/*/*/xhr_send", "POST"),
                new AntPathRequestMatcher("/ws-tracking/*/*/jsonp_send", "POST")
            ));

        return http.build();
    }
}
