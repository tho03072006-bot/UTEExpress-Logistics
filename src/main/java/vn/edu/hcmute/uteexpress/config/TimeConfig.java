package vn.edu.hcmute.uteexpress.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public Clock applicationClock() {
        // Cả nhóm lưu DATETIME theo giờ Việt Nam, kể cả máy có múi giờ hệ điều hành khác.
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        return Clock.system(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
