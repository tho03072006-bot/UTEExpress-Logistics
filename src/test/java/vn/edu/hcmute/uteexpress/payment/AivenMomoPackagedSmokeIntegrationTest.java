package vn.edu.hcmute.uteexpress.payment;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Properties;
import java.util.jar.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Kiểm tra đúng JAR dành cho Render, chỉ khởi tạo schema validate + SELECT, không tạo thanh toán. */
class AivenMomoPackagedSmokeIntegrationTest {
    @Test void packagedSimulatorStartsSeparatelyAndDoesNotContainPrivateApplicationProperties() throws Exception {
        Path jar = Path.of("target/momo-simulator/uteexpress-0.0.1-SNAPSHOT.jar").toAbsolutePath();
        assertTrue(Files.isRegularFile(jar), "Cần mvn -Pmomo-simulator -Dmaven.test.skip=true package trước.");
        try (var archive = new JarFile(jar.toFile())) {
            assertEquals("vn.edu.hcmute.momosimulator.MomoSimulatorApplication", archive.getManifest().getMainAttributes().getValue("Start-Class"));
            assertNull(archive.getEntry("BOOT-INF/classes/application.properties"));
            assertNull(archive.getEntry("BOOT-INF/classes/application-local.properties"));
            assertNull(archive.getEntry("BOOT-INF/classes/templates/admin/accounts.html"));
            assertNotNull(archive.getEntry("BOOT-INF/classes/templates/momo/checkout.html"));
        }
        int port;
        try (var socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) { port = socket.getLocalPort(); }
        var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Xmx256m", "-XX:MaxMetaspaceSize=160m", "-XX:+UseSerialGC", "-jar", jar.toString(),
                "--server.port=" + port, "--server.address=127.0.0.1");
        var properties = new Properties();
        try (var reader = Files.newBufferedReader(Path.of("config/aiven-local.properties"))) { properties.load(reader); }
        for (String name : new String[]{"AIVEN_HOST", "AIVEN_PORT", "AIVEN_DATABASE", "AIVEN_USER", "AIVEN_PASSWORD"}) {
            String value = System.getenv(name); if (value == null || value.isBlank()) value = properties.getProperty(name);
            assertNotNull(value, "Thiếu cấu hình Aiven riêng."); process.environment().put(name, value);
        }
        process.environment().put("AIVEN_CA_PEM", Files.readString(Path.of("config/certificates/ca.pem")));
        // Không sinh QR/đơn cho URL fixture, không cấu hình URL này vào ứng dụng local.
        process.environment().put("MOMO_SIMULATOR_URL", "https://smoke-test-only.onrender.com");
        process.environment().put("MOMO_SIM_SECRET", MomoTicketCodecTest.KEY);
        process.redirectErrorStream(true).redirectOutput(Path.of("target/momo-packaged-smoke.log").toFile());
        var child = process.start();
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
            var health = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/healthz")).timeout(Duration.ofSeconds(3)).build();
            boolean ready = false;
            for (int attempt = 0; attempt < 45 && child.isAlive(); attempt++) {
                try {
                    var result = client.send(health, HttpResponse.BodyHandlers.ofString());
                    if (result.statusCode() == 200 && result.headers().firstValue("X-Momo-Protocol").orElse("").equals("3")) { ready = true; break; }
                }
                catch (IOException ignored) { }
                Thread.sleep(1000);
            }
            assertTrue(ready, "JAR chưa khởi động đúng; kiểm tra target/momo-packaged-smoke.log (không gửi cấu hình bí mật).");
            var page = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(200, page.statusCode()); assertTrue(page.body().contains("<title>MoMo · Ví mô phỏng</title>"));
            assertTrue(page.body().contains("DEMO"));
            assertTrue(page.headers().firstValue("Content-Security-Policy").orElse("").contains("form-action 'self'"));
            var denied = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/admin/tai-khoan")).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(403, denied.statusCode());
        } finally {
            child.destroy(); if (!child.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) child.destroyForcibly();
        }
    }
}
