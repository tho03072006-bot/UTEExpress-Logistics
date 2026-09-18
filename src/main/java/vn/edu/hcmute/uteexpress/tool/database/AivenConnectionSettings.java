package vn.edu.hcmute.uteexpress.tool.database;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/** Kết nối dành cho công cụ chạy tay; không in URL/mật khẩu khi lỗi. */
public final class AivenConnectionSettings {
    private AivenConnectionSettings() { }

    public static Connection open() throws IOException, SQLException {
        Properties values = new Properties();
        Path local = Path.of("config/aiven-local.properties");
        if (Files.exists(local)) {
            try (var reader = Files.newBufferedReader(local, StandardCharsets.UTF_8)) {
                values.load(reader);
            }
        }
        for (String name : new String[]{"AIVEN_HOST", "AIVEN_PORT", "AIVEN_DATABASE", "AIVEN_USER",
                "AIVEN_PASSWORD", "AIVEN_TRUSTSTORE_URL", "AIVEN_TRUSTSTORE_PASSWORD"}) {
            String value = System.getenv(name);
            if (value != null) { values.setProperty(name, value); }
        }
        String url = jdbcUrl(values);
        Properties options = connectionProperties(values);
        try {
            return DriverManager.getConnection(url, options);
        } finally {
            values.clear();
            options.clear();
        }
    }

    static String jdbcUrl(Properties values) {
        String host = required(values, "AIVEN_HOST");
        String database = required(values, "AIVEN_DATABASE");
        if (!host.matches("[a-zA-Z0-9][a-zA-Z0-9.-]*\\.aivencloud\\.com")
                || !database.matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")) {
            throw new IllegalArgumentException("HOST phải thuộc aivencloud.com; tên database chỉ gồm chữ, số, gạch dưới.");
        }
        int port;
        try { port = Integer.parseInt(required(values, "AIVEN_PORT")); }
        catch (NumberFormatException ex) { throw new IllegalArgumentException("AIVEN_PORT phải là số."); }
        if (port < 1 || port > 65535) { throw new IllegalArgumentException("AIVEN_PORT ngoài phạm vi 1–65535."); }
        return "jdbc:mysql://" + host + ":" + port + "/" + database;
    }

    static Properties connectionProperties(Properties values) {
        Properties options = new Properties();
        options.setProperty("user", required(values, "AIVEN_USER"));
        options.setProperty("password", required(values, "AIVEN_PASSWORD"));
        options.setProperty("sslMode", "VERIFY_IDENTITY");
        String truststore = required(values, "AIVEN_TRUSTSTORE_URL");
        if (!truststore.startsWith("file:/")) {
            throw new IllegalArgumentException("AIVEN_TRUSTSTORE_URL phải là file:/... trỏ tới truststore local.");
        }
        options.setProperty("trustCertificateKeyStoreUrl", truststore);
        options.setProperty("trustCertificateKeyStorePassword", required(values, "AIVEN_TRUSTSTORE_PASSWORD"));
        options.setProperty("trustCertificateKeyStoreType", "PKCS12");
        options.setProperty("fallbackToSystemTrustStore", "false");
        options.setProperty("allowPublicKeyRetrieval", "false");
        options.setProperty("characterEncoding", "UTF-8");
        options.setProperty("connectionTimeZone", "Asia/Ho_Chi_Minh");
        options.setProperty("preserveInstants", "false");
        options.setProperty("connectTimeout", "10000");
        options.setProperty("socketTimeout", "30000");
        return options;
    }

    private static String required(Properties values, String name) {
        String value = values.getProperty(name);
        if (value == null || value.isBlank() || value.contains("CHANGE_ME")) {
            throw new IllegalArgumentException("Chưa cấu hình " + name + ".");
        }
        return value;
    }
}
