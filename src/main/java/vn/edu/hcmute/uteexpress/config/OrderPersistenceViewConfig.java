package vn.edu.hcmute.uteexpress.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.servlet.config.annotation.*;

/**
 * Giữ lazy loading của các trang hiện có, nhưng không giữ phiên JPA/kết nối xuyên suốt
 * các request phải đợi bản đồ. Mỗi bước đọc/ghi của luồng đặt đơn tự có transaction ngắn.
 * Không đổi connection handling toàn cục vì thanh toán/điều phối cần isolation riêng.
 */
@Configuration(proxyBeanMethods=false)
@ConditionalOnProperty(prefix="spring.jpa", name="open-in-view", havingValue="true", matchIfMissing=true)
public class OrderPersistenceViewConfig {
    @Bean
    public OpenEntityManagerInViewInterceptor orderAwareOpenEntityManagerInViewInterceptor() {
        return new OpenEntityManagerInViewInterceptor();
    }

    @Bean
    public WebMvcConfigurer orderAwarePersistenceViews(OpenEntityManagerInViewInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override public void addInterceptors(InterceptorRegistry registry) {
                registry.addWebRequestInterceptor(interceptor).excludePathPatterns(
                        "/nguoi-dung/tao-don", "/nguoi-dung/tao-don/**",
                        "/nguoi-dung/gio-don/*/xac-nhan", "/nguoi-dung/gio-don/xac-nhan");
            }
        };
    }
}
