package vn.edu.hcmute.uteexpress.config.admin;

import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.security.*;

@Configuration
@PropertySource(value = "file:./config/jwt-local.properties", ignoreResourceNotFound = true)
public class StaffApiSecurityConfig {
    @Bean @Order(0)
    public SecurityFilterChain staffApiFilterChain(HttpSecurity http, JwtTokenProvider tokens,
            StaffAccountRepository accounts) throws Exception {
        http.securityMatcher("/api/internal/**")
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
            .requestCache(cache -> cache.requestCache(new NullRequestCache()))
            // API này chỉ nhận Bearer header, không nhận cookie/session; không có thao tác ghi.
            .csrf(csrf -> csrf.disable()).formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/internal/admin/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/internal/operations/queues", "/api/internal/operations/tracking").hasAnyRole("ADMIN", "MANAGER")
                .anyRequest().denyAll())
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(401); response.setHeader("WWW-Authenticate", "Bearer");
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Cần Bearer token hợp lệ.\"}");
                })
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(403); response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"Không có quyền truy cập API này.\"}");
                }))
            .addFilterBefore(new JwtAuthFilter(tokens, accounts), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
