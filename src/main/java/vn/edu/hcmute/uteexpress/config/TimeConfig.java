package vn.edu.hcmute.uteexpress.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {

    @Bean
    public Clock applicationClock() {
        return Clock.system(java.time.ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
