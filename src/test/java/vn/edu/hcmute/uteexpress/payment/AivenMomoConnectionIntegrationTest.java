package vn.edu.hcmute.uteexpress.payment;

import java.io.Reader;
import java.nio.file.*;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import vn.edu.hcmute.momosimulator.SimulatorConfiguration;
import vn.edu.hcmute.uteexpress.service.payment.MomoSettings;
import static org.junit.jupiter.api.Assertions.*;

/** Chạy tường minh: chỉ SELECT/SHOW, không tạo tài khoản, đơn, bảng hoặc thanh toán trên cloud. */
class AivenMomoConnectionIntegrationTest {
    @Test void simulatorConnectsToSharedAivenUsingPemAndVerifiedTls() throws Exception {
        var properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("config/aiven-local.properties"))) { properties.load(reader); }
        var environment = new MockEnvironment();
        for (String name : new String[]{"AIVEN_HOST", "AIVEN_PORT", "AIVEN_DATABASE", "AIVEN_USER", "AIVEN_PASSWORD"}) {
            String value = System.getenv(name); if (value == null || value.isBlank()) value = properties.getProperty(name);
            assertNotNull(value, "Cần cấu hình Aiven riêng trước khi chạy kiểm thử kết nối."); environment.setProperty(name, value);
        }
        // Origin/key fixture chỉ kiểm tra khởi tạo TLS, không sinh QR hoặc vé cho dữ liệu thật.
        environment.setProperty("MOMO_SIMULATOR_URL", "https://tls-test-only.onrender.com");
        environment.setProperty("MOMO_SIM_SECRET", MomoTicketCodecTest.KEY);
        environment.setProperty("AIVEN_CA_FILE", Path.of("config/certificates/ca.pem").toAbsolutePath().toString());
        try (var source = (com.zaxxer.hikari.HikariDataSource) new SimulatorConfiguration().dataSource(environment, new MomoSettings(environment));
             var connection = source.getConnection()) {
            connection.setReadOnly(true);
            assertTrue(connection.getMetaData().getURL().contains(".aivencloud.com:"));
            try (var query = connection.createStatement(); var result = query.executeQuery("SELECT 1")) { assertTrue(result.next()); assertEquals(1, result.getInt(1)); }
            try (var query = connection.createStatement(); var result = query.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                assertTrue(result.next()); assertFalse(result.getString(2).isBlank(), "Kết nối phải được mã hóa TLS.");
            }
            assertEquals("VERIFY_IDENTITY", source.getDataSourceProperties().getProperty("sslMode"));
        }
    }
}
