package vn.edu.hcmute.uteexpress.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
@PropertySource(value = "file:./config/cloudinary-local.properties", ignoreResourceNotFound = true)
public class CloudinaryConfig {
    private static final int MAX_MULTIPART_REQUEST_BYTES=12*1024*1024;
    @Bean
    public org.springframework.boot.web.server.WebServerFactoryCustomizer<org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory> cloudFormPostLimit() {
        // Chữ ký base64 dài hơn PNG gốc: tránh Tomcat chặn form 2 MiB trước validation của ứng dụng.
        return factory -> factory.addConnectorCustomizers(connector -> connector.setMaxPostSize(MAX_MULTIPART_REQUEST_BYTES));
    }
    @Bean
    public jakarta.servlet.MultipartConfigElement cloudMultipartConfig() {
        var factory = new org.springframework.boot.web.servlet.MultipartConfigFactory();
        factory.setMaxFileSize(org.springframework.util.unit.DataSize.ofMegabytes(10));
        factory.setMaxRequestSize(org.springframework.util.unit.DataSize.ofBytes(MAX_MULTIPART_REQUEST_BYTES));
        return factory.createMultipartConfig();
    }
    @Bean
    public Settings cloudinarySettings(
            @Value("${APP_CLOUDINARY_ENABLED:${app.cloudinary.enabled:true}}") boolean enabled,
            @Value("${APP_CLOUDINARY_CLOUD_NAME:${app.cloudinary.cloud-name:}}") String cloudName,
            @Value("${APP_CLOUDINARY_API_KEY:${app.cloudinary.api-key:}}") String key,
            @Value("${APP_CLOUDINARY_API_SECRET:${app.cloudinary.api-secret:}}") String secret) {
        return new Settings(enabled, cloudName.strip(), key.strip(), secret.strip());
    }

    public record Settings(boolean enabled, String cloudName, String apiKey, String apiSecret) {
        public boolean configured() {
            return enabled && cloudName.matches("[a-zA-Z0-9_-]{1,100}") && !cloudName.equals("CHANGE_ME")
                    && apiKey.matches("[0-9]{5,30}") && apiSecret.matches("[a-zA-Z0-9_-]{16,256}")
                    && !apiSecret.contains("CHANGE_ME");
        }
        @Override public String toString() { return "CloudinarySettings[credentials=REDACTED]"; }
    }
}
