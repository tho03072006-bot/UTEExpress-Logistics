package vn.edu.hcmute.uteexpress.tool.database;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/**
 * Nâng cấp V4: thêm bảng user_profile_detail (ảnh đại diện, thông tin cá nhân, tùy chọn email) cho trang
 * "Tài khoản của tôi". Chỉ tạo bảng mới nếu chưa có, không sửa bảng hay dữ liệu cũ; không tự chạy lúc khởi động.
 * Dùng: --check (chỉ kiểm tra), --apply (tạo bảng), --print-schema (in câu lệnh, không kết nối database).
 */
public final class UpgradeAivenProfiles {
    static final String TABLE = "user_profile_detail";
    static final Set<String> COLUMNS = Set.of("id", "user_id", "revision", "avatar_asset", "birth_date", "gender", "bio",
            "province", "address_line", "notify_order_email", "updated_at");
    private static final String SCRIPT = "/db/migration/V4__user_profile_detail.sql";

    private UpgradeAivenProfiles() { }

    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("--check", "--apply", "--print-schema").contains(args[0])) {
            System.err.println("Dùng --check, --apply hoặc --print-schema."); System.exit(1);
        }
        try {
            if (args[0].equals("--print-schema")) { System.out.println(statement() + ";"); return; }
            try (var connection = AivenConnectionSettings.open()) {
                validateTarget(connection);
                if (args[0].equals("--apply")) apply(connection);
                else if (exists(connection)) validateTable(connection);
                System.out.println(TABLE + ": " + (exists(connection) ? "đã có" : "chưa có"));
                System.out.println("Không thay đổi dữ liệu tài khoản, vận đơn hoặc cấu hình bí mật.");
            }
        } catch (IllegalArgumentException ex) { System.err.println(ex.getMessage()); System.exit(1); }
        catch (Exception ex) {
            System.err.println("Không hoàn tất nâng cấp. Kiểm tra kết nối TLS và quyền CREATE; chạy --check để xem bảng. Không tự xóa bảng.");
            System.exit(1);
        }
    }

    static String statement() throws java.io.IOException {
        try (var input = UpgradeAivenProfiles.class.getResourceAsStream(SCRIPT)) {
            if (input == null) throw new IllegalArgumentException("Thiếu script nâng cấp: " + SCRIPT);
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
            var parts = Arrays.stream(script.split(";")).map(String::strip).filter(s -> !s.isEmpty()).toList();
            if (parts.size() != 1 || !parts.get(0).startsWith("CREATE TABLE IF NOT EXISTS " + TABLE + " "))
                throw new IllegalArgumentException("Script V4 chỉ được tạo đúng một bảng " + TABLE + ".");
            return parts.get(0);
        }
    }

    static void apply(Connection connection) throws Exception {
        validateTarget(connection);
        try (var statement = connection.createStatement(); var lock = statement.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if (!lock.next() || lock.getInt(1) != 1) throw new IllegalArgumentException("Database đang được nâng cấp bởi tiến trình khác.");
        }
        try {
            if (exists(connection)) validateTable(connection);
            try (var statement = connection.createStatement()) { statement.executeUpdate(statement()); }
            validateTable(connection);
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')"); }
        }
    }

    private static void validateTarget(Connection connection) throws SQLException {
        var meta = connection.getMetaData();
        if (!"MySQL".equals(meta.getDatabaseProductName()) || meta.getDatabaseMajorVersion() < 8 || connection.getCatalog() == null
                || !tableExists(connection, "app_user"))
            throw new IllegalArgumentException("Chỉ nâng cấp MySQL 8+ đã có bảng app_user của UTEExpress.");
    }

    private static boolean exists(Connection connection) throws SQLException { return tableExists(connection, TABLE); }

    private static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var result = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            while (result.next()) if (table.equalsIgnoreCase(result.getString("TABLE_NAME"))) return true;
            return false;
        }
    }

    private static void validateTable(Connection connection) throws SQLException {
        var meta = connection.getMetaData();
        Set<String> columns = new HashSet<>();
        try (var result = meta.getColumns(connection.getCatalog(), null, TABLE, "%")) {
            while (result.next()) if (TABLE.equalsIgnoreCase(result.getString("TABLE_NAME")))
                columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        if (!columns.containsAll(COLUMNS))
            throw new IllegalArgumentException("Bảng " + TABLE + " không đúng cấu trúc; cần kiểm tra thủ công, không tự sửa.");
        boolean foreignKey = false;
        try (var result = meta.getImportedKeys(connection.getCatalog(), null, TABLE)) {
            while (result.next()) foreignKey |= "user_id".equalsIgnoreCase(result.getString("FKCOLUMN_NAME"))
                    && "app_user".equalsIgnoreCase(result.getString("PKTABLE_NAME"));
        }
        boolean unique = false;
        try (var result = meta.getIndexInfo(connection.getCatalog(), null, TABLE, true, false)) {
            while (result.next()) unique |= "user_id".equalsIgnoreCase(result.getString("COLUMN_NAME"));
        }
        if (!foreignKey || !unique)
            throw new IllegalArgumentException("Bảng " + TABLE + " thiếu khóa ngoại hoặc ràng buộc duy nhất; cần kiểm tra thủ công.");
    }
}
