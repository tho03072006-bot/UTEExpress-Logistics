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
 * LUU Y QUAN TRONG (doc truoc khi sua):
 * - Cau hinh nay dang o che do "mo" (permitAll toan bo) de ca nhom co the
 *   chay thu tung trang ngay tu dau, KHONG phai cau hinh cuoi cung.
 * - Khi lam xong dang nhap/dang ky (Tuan 2 theo ke hoach), nguoi phu trach
 *   phai thay authorizeHttpRequests ben duoi bang phan quyen that theo role
 *   (USER, SHIPPER, MANAGER, ADMIN) va bat lai CSRF cho cac form khong phai API.
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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/trang-chu", "/tra-cuu/**", "/dang-ky", "/dang-nhap",
                        "/css/**", "/js/**", "/images/**", "/api/tracking/**").permitAll()
                // TODO: thay dong duoi bang phan quyen that cho tung role khi lam xong dang nhap
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/dang-nhap")
                .permitAll()
            )
            // Tam tat CSRF de nhom de test cac API (vd tra cuu van don) trong luc build.
            // TODO: bat lai csrf cho cac form HTML khi hoan thien.
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
