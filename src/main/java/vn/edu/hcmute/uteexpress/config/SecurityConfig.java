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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/trang-chu", "/tra-cuu/**", "/dang-ky", "/dang-nhap", "/xac-thuc-otp",
                        "/quen-mat-khau", "/dat-lai-mat-khau",
                        "/css/**", "/js/**", "/images/**", "/uploads/**", "/api/tracking/**").permitAll()
                .requestMatchers("/nguoi-dung/**").authenticated()
             // TV2: khu vuc shipper chi danh cho vai tro SHIPPER.
             // TODO (TV3): bao ve khu vuc manager/admin khi chuc nang quan ly hoan thien.
                .requestMatchers("/shipper/**").hasRole("SHIPPER")
                .requestMatchers("/manager/**", "/admin/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/dang-nhap")
                .defaultSuccessUrl("/nguoi-dung/trang-chu", false)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
                .permitAll()
            )
            // Tam tat CSRF de nhom de test cac API (vd tra cuu van don) trong luc build.
            // TODO: bat lai csrf cho cac form HTML khi hoan thien.
            .csrf(csrf -> csrf.disable());

        return http.build();
    }
}
