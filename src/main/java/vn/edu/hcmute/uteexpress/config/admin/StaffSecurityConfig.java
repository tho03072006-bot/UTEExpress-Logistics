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
                .requestMatchers("/admin/**").access((authentication, context) ->
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
                    boolean admin = authentication.getAuthorities().stream()
                            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                    response.sendRedirect(request.getContextPath()
                            + (admin ? "/admin/trang-chu" : "/manager/trang-chu"));
                })
                .failureHandler((request, response, exception) -> {
                    attempts.recordFailure(request.getParameter("username"));
                    response.sendRedirect(request.getContextPath() + "/noi-bo/dang-nhap?error");
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
