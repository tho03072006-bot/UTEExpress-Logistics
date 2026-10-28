package vn.edu.hcmute.uteexpress.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Timeout do servlet container thực thi; không phụ thuộc JavaScript trên trình duyệt. */
@Configuration
public class SessionTimeoutConfig {
    @Bean
    public ServletContextInitializer configureSessionTimeout(
            @Value("${APP_WEB_SESSION_IDLE_MINUTES:${app.session.idle-minutes:60}}") int idleMinutes) {
        if (idleMinutes < 15 || idleMinutes > 120)
            throw new IllegalStateException("Phiên web phải hết hạn sau 15–120 phút không hoạt động.");
        return context -> context.setSessionTimeout(idleMinutes);
    }
}
