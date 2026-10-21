package vn.edu.hcmute.uteexpress.support;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Chạy thử ứng dụng trên máy KHÔNG cần Aiven: H2 trong bộ nhớ + dữ liệu mẫu ở src/test/resources/local-demo/seed.sql.
 * Dùng đúng cấu hình test của nhóm (profile isolated-logistics), không đụng AivenOnlyConfig của ứng dụng thật.
 * Tắt app là mất hết dữ liệu; ảnh tải lên giữ trong bộ nhớ (cloud giả).
 *
 * Cách chạy trong STS: đặt biến môi trường UTE_DEMO_PASSWORD (ít nhất 6 ký tự) trong Run Configurations → Environment,
 * rồi chuột phải file này → Run As → Java Application. Mở http://localhost:8085.
 * Tài khoản: shipper-demo, khach-demo, manager-demo, admin-demo, shipper-thongke1..3 — cùng mật khẩu bạn đặt ở trên.
 */
public final class LocalDemoApplication {
    private static final String PLACEHOLDER = "{local-demo}";

    private LocalDemoApplication() { }

    public static void main(String[] args) {
        String password = System.getenv("UTE_DEMO_PASSWORD");
        if (password == null || password.strip().length() < 6)
            throw new IllegalStateException("Hãy đặt biến môi trường UTE_DEMO_PASSWORD (ít nhất 6 ký tự) làm mật khẩu cho tài khoản mẫu.");
        Map<String, Object> props = new HashMap<>();
        props.put("server.port", System.getenv().getOrDefault("UTE_DEMO_PORT", "8085"));
        props.put("spring.datasource.url", "jdbc:h2:mem:local-demo;MODE=MySQL;DB_CLOSE_DELAY=-1;IGNORE_UNKNOWN_SETTINGS=TRUE");
        props.put("spring.datasource.driver-class-name", "org.h2.Driver");
        props.put("spring.datasource.username", "sa");
        props.put("spring.datasource.password", "");
        props.put("spring.jpa.database-platform", "org.hibernate.dialect.H2Dialect");
        props.put("spring.jpa.properties.hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        props.put("spring.jpa.hibernate.ddl-auto", "create");
        props.put("spring.jpa.defer-datasource-initialization", "true");
        props.put("spring.sql.init.mode", "always");
        props.put("spring.sql.init.encoding", "UTF-8");
        props.put("spring.sql.init.data-locations", "classpath:local-demo/seed.sql");
        props.put("app.mail.order-updates.enabled", "false");
        props.put("app.upload.dir", Path.of("target", "local-demo-uploads").toAbsolutePath().toString());
        // Đặt như cờ -D để được ưu tiên hơn application.properties (vốn trỏ tới Aiven); cờ -D người dùng tự thêm vẫn thắng.
        props.forEach((key, value) -> { if (System.getProperty(key) == null) System.setProperty(key, value.toString()); });
        new SpringApplicationBuilder(IsolatedLogisticsTestConfiguration.class, PasswordSetter.class)
                .profiles("isolated-logistics").run(args);
    }

    /** Đặt mật khẩu cho mọi tài khoản mẫu từ biến môi trường, sau khi dữ liệu mẫu đã nạp. */
    // Cố ý KHÔNG đánh @Configuration: ComponentScan của cấu hình test sẽ nhặt nó vào mọi test khác.
    static class PasswordSetter {
        @org.springframework.context.annotation.Bean
        ApplicationRunner setDemoPasswords(JdbcTemplate jdbc, PasswordEncoder encoder,
                                           org.springframework.transaction.PlatformTransactionManager transactions) {
            return args -> {
                String hash = encoder.encode(System.getenv("UTE_DEMO_PASSWORD").strip());
                // Dự án tắt auto-commit: phải chạy trong giao dịch thì cập nhật mới được lưu.
                Integer updated = new org.springframework.transaction.support.TransactionTemplate(transactions).execute(status ->
                        jdbc.update("UPDATE app_user SET password = ? WHERE password = ?", hash, PLACEHOLDER));
                System.out.println("[local-demo] Đã đặt mật khẩu cho " + updated + " tài khoản mẫu. Mở http://localhost:"
                        + System.getenv().getOrDefault("UTE_DEMO_PORT", "8085"));
            };
        }
    }
}
