package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/** Kiểm tra kết nối Aiven chỉ đọc, không in URL, user hoặc thông tin bí mật. */
public final class CheckAivenConnection {
    private static final Set<String> EXPECTED_TABLES = Set.of(
            "app_user", "shipment_order", "saved_address", "draft_order", "promo_code",
            "order_payment", "service_review", "email_change_request", "delivery_proof", "staff_account");

    private CheckAivenConnection() { }

    public static void main(String[] args) {
        try (Connection connection = AivenConnectionSettings.open()) {
            connection.setReadOnly(true);
            var metadata = connection.getMetaData();
            if (!"MySQL".equals(metadata.getDatabaseProductName())) {
                throw new IllegalStateException("Máy chủ không phải MySQL.");
            }
            if (metadata.getDatabaseMajorVersion() < 8) {
                throw new IllegalStateException("Cần MySQL 8 trở lên.");
            }
            try (var statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT 1")) {
                if (!result.next() || result.getInt(1) != 1) {
                    throw new IllegalStateException("SELECT 1 không trả về kết quả mong đợi.");
                }
            }
            String cipher = "";
            try (var statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SHOW SESSION STATUS LIKE 'Ssl_cipher'")) {
                if (result.next()) { cipher = result.getString(2); }
            }
            if (cipher == null || cipher.isBlank()) {
                throw new IllegalStateException("Kết nối không xác nhận được mã hóa TLS.");
            }
            Set<String> tables = new HashSet<>();
            try (ResultSet result = metadata.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (result.next()) { tables.add(result.getString("TABLE_NAME").toLowerCase()); }
            }
            Set<String> missing = new HashSet<>(EXPECTED_TABLES);
            missing.removeAll(tables);
            System.out.println("KẾT NỐI AIVEN THÀNH CÔNG");
            System.out.println("MySQL: " + metadata.getDatabaseMajorVersion() + "." + metadata.getDatabaseMinorVersion());
            System.out.println("TLS: đã mã hóa và xác minh danh tính máy chủ");
            System.out.println("Số bảng hiện có: " + tables.size());
            if (missing.isEmpty()) {
                System.out.println("Schema UTEExpress: đầy đủ " + EXPECTED_TABLES.size() + " bảng");
            } else {
                System.out.println("Schema UTEExpress: chưa khởi tạo hoặc còn thiếu " + missing.size() + " bảng");
            }
        } catch (Exception ex) {
            System.err.println("KẾT NỐI AIVEN THẤT BẠI");
            System.err.println(safeMessage(ex));
            System.exit(1);
        }
    }

    private static String safeMessage(Exception ex) {
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            return ex.getMessage();
        }
        if (ex instanceof java.sql.SQLException sqlException) {
            return "SQLState=" + sqlException.getSQLState()
                    + ". Kiểm tra HOST/PORT/DB/USER/PASSWORD, TLS, IP allowlist và trạng thái dịch vụ.";
        }
        return "Kiểm tra truststore, mật khẩu truststore và cấu hình local.";
    }
}
