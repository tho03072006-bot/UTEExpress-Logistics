package vn.edu.hcmute.momosimulator;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;
import javax.sql.DataSource;
import com.zaxxer.hikari.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.payment.MomoPaymentRepository;
import vn.edu.hcmute.uteexpress.service.payment.MomoSettings;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(basePackageClasses = MomoPaymentRepository.class)
public class SimulatorConfiguration {
    @Bean public PersistenceManagedTypes persistenceManagedTypes() {
        return PersistenceManagedTypes.of(AppUser.class.getName(), Order.class.getName(),
                OrderPayment.class.getName(), PromoCode.class.getName());
    }

    @Bean public DataSource dataSource(Environment environment, MomoSettings settings) throws Exception {
        settings.secretKey(); // Dừng sớm nếu chưa có khóa, không chạy với khóa mặc định.
        settings.baseUrl();
        String host = required(environment, "AIVEN_HOST");
        String database = required(environment, "AIVEN_DATABASE");
        int port = Integer.parseInt(required(environment, "AIVEN_PORT"));
        if (!host.matches("[A-Za-z0-9.-]+\\.aivencloud\\.com") || !database.matches("[A-Za-z0-9_]{1,64}") || port < 1 || port > 65535)
            throw new IllegalStateException("Cấu hình địa chỉ Aiven không hợp lệ.");
        String pem = environment.getProperty("AIVEN_CA_PEM", "").replace("\\n", "\n");
        if (pem.isBlank()) pem = Files.readString(Path.of(environment.getProperty("AIVEN_CA_FILE", "/etc/secrets/aiven-ca.pem")));
        var certificates = CertificateFactory.getInstance("X.509")
                .generateCertificates(new ByteArrayInputStream(pem.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        if (certificates.isEmpty() || certificates.size() > 10) throw new IllegalStateException("CA Aiven không hợp lệ.");
        var keyStore = KeyStore.getInstance("PKCS12"); keyStore.load(null, null);
        int index = 0;
        for (var certificate : certificates) {
            var ca = (X509Certificate) certificate; ca.checkValidity();
            if (ca.getBasicConstraints() < 0) throw new IllegalStateException("Tệp chứng chỉ phải là CA Aiven.");
            keyStore.setCertificateEntry("aiven-" + index++, ca);
        }
        String password = UUID.randomUUID().toString();
        Path trustStore = Files.createTempFile("momo-aiven-ca-", ".p12");
        try { Files.setPosixFilePermissions(trustStore, java.nio.file.attribute.PosixFilePermissions.fromString("rw-------")); }
        catch (UnsupportedOperationException ignored) { /* Windows dùng ACL của thư mục temp riêng. */ }
        trustStore.toFile().deleteOnExit();
        try (var output = Files.newOutputStream(trustStore)) { keyStore.store(output, password.toCharArray()); }
        var config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
        config.setUsername(required(environment, "AIVEN_USER")); config.setPassword(required(environment, "AIVEN_PASSWORD"));
        config.addDataSourceProperty("sslMode", "VERIFY_IDENTITY");
        config.addDataSourceProperty("trustCertificateKeyStoreUrl", trustStore.toUri().toString());
        config.addDataSourceProperty("trustCertificateKeyStorePassword", password);
        config.addDataSourceProperty("trustCertificateKeyStoreType", "PKCS12");
        config.addDataSourceProperty("fallbackToSystemTrustStore", "false");
        config.addDataSourceProperty("allowPublicKeyRetrieval", "false");
        config.addDataSourceProperty("connectionTimeZone", "+07:00");
        config.addDataSourceProperty("forceConnectionTimeZoneToSession", "true");
        config.addDataSourceProperty("preserveInstants", "false");
        config.addDataSourceProperty("connectTimeout", "10000"); config.addDataSourceProperty("socketTimeout", "15000");
        config.setMaximumPoolSize(3); config.setMinimumIdle(0); config.setConnectionTimeout(10000);
        config.setInitializationFailTimeout(10000); config.setPoolName("momo-aiven");
        return new HikariDataSource(config);
    }

    @Bean public SecurityFilterChain simulatorSecurity(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/healthz", "/momo/**", "/css/momo-simulator.css", "/js/momo-simulator.js", "/error").permitAll()
                .anyRequest().denyAll())
                .headers(headers -> headers
                        .cacheControl(cache -> { })
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; style-src 'self'; script-src 'self'; img-src 'self' data:; form-action 'self'; base-uri 'none'; frame-ancestors 'none'")))
                // CSRF mặc định được giữ nguyên cho mọi POST, không mở CORS/callback công khai.
                .build();
    }
    private static String required(Environment environment, String name) {
        String value = environment.getProperty(name, "");
        if (value.isBlank() || value.equals("CHANGE_ME")) throw new IllegalStateException("Thiếu biến cấu hình " + name);
        return value;
    }
}
