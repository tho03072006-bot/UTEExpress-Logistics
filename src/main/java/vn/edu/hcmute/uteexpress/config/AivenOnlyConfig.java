package vn.edu.hcmute.uteexpress.config;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Chặn profile cũ hoặc biến môi trường vô tình đưa cả nhóm về database riêng. */
@Configuration
public class AivenOnlyConfig {
    @Bean public static BeanFactoryPostProcessor requireSharedAiven(Environment env) {
        return factory -> {
            String url = env.getProperty("spring.datasource.url", "");
            if (!url.matches("jdbc:mysql://[a-zA-Z0-9][a-zA-Z0-9.-]*\\.aivencloud\\.com:[0-9]{1,5}/[a-zA-Z0-9_]+")
                || !"validate".equals(env.getProperty("spring.jpa.hibernate.ddl-auto"))
                || !"VERIFY_IDENTITY".equals(env.getProperty("spring.datasource.hikari.data-source-properties.sslMode"))
                || !"false".equals(env.getProperty("spring.datasource.hikari.data-source-properties.fallbackToSystemTrustStore"))
                || !url.equals(env.getProperty("spring.datasource.hikari.jdbc-url", url))
                || env.containsProperty("spring.datasource.jndi-name")
                || env.containsProperty("spring.datasource.hikari.data-source-class-name")) {
                throw new IllegalStateException("Chỉ cho phép Aiven MySQL, TLS VERIFY_IDENTITY và ddl-auto=validate. Kiểm tra cấu hình; không dùng database local/profile cũ.");
            }
        };
    }
}

