package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** Chạy tay đúng một lần trên database Aiven trống; không tự chạy khi ứng dụng khởi động. */
public final class InitializeAivenDatabase {
    private InitializeAivenDatabase() { }

    public static void main(String[] args) {
        if (args.length != 1 || !("--print-schema".equals(args[0]) || "--initialize-empty".equals(args[0]))) {
            System.err.println("Dùng --print-schema (offline) hoặc --initialize-empty (tạo bảng trên DB trống).");
            System.exit(1);
        }
        try {
            List<String> statements = AivenSchema.statements();
            if ("--print-schema".equals(args[0])) {
                statements.forEach(sql -> System.out.println(sql + ";"));
                return;
            }
            var console = System.console();
            if (console == null || !"TAO DATABASE TRONG".equals(console.readLine(
                    "Dừng ứng dụng của cả nhóm. Đã kiểm tra đúng database mới? Nhập TAO DATABASE TRONG: "))) {
                throw new IllegalArgumentException("Đã hủy; chỉ khởi tạo từ terminal tương tác sau khi xác nhận.");
            }
            try (Connection connection = AivenConnectionSettings.open()) {
                initialize(connection, statements);
            }
            System.out.println("Đã tạo schema chung cho User/Shipper/Manager/Admin. Chưa tạo dữ liệu mẫu/tài khoản.");
        } catch (IllegalArgumentException ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        } catch (Exception ex) {
            // MySQL DDL tự commit: không tuyên bố rollback cấu trúc khi có lỗi giữa chừng.
            System.err.println("Khởi tạo thất bại. Có thể đã tạo một phần bảng; cần kiểm tra DB trước khi thử lại. "
                    + "Không tự xóa bảng. Kiểm tra TLS, quyền CREATE/ALTER và kết nối.");
            System.exit(1);
        }
    }

    static void initialize(Connection connection, List<String> statements) throws SQLException {
        var metadata = connection.getMetaData();
        if (!"MySQL".equals(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() < 8) {
            throw new IllegalArgumentException("Cần MySQL 8 trở lên trên Aiven.");
        }
        if (connection.getCatalog() == null || connection.getCatalog().isBlank()) {
            throw new IllegalArgumentException("Chưa chọn database đích.");
        }
        try (var lock = connection.createStatement();
             var result = lock.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if (!result.next() || result.getInt(1) != 1) {
                throw new IllegalArgumentException("Một tiến trình khác đang khởi tạo; chưa thay đổi database.");
            }
        }
        try {
            try (var tables = metadata.getTables(connection.getCatalog(), null, "%", null)) {
                if (tables.next()) {
                    throw new IllegalArgumentException("Database đã có bảng/view. Từ chối ghi đè; hãy chọn database mới trống.");
                }
            }
            if (statements.isEmpty() || statements.stream().anyMatch(sql ->
                    !(sql.startsWith("create table ") || sql.startsWith("alter table ")))) {
                throw new IllegalArgumentException("DDL không hợp lệ; chưa thay đổi database.");
            }
            try (var statement = connection.createStatement()) {
                statement.execute("SET SESSION sql_mode = 'STRICT_ALL_TABLES,NO_ZERO_DATE,NO_ZERO_IN_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION'");
                for (String sql : statements) { statement.executeUpdate(sql); }
            }
        } finally {
            try (var release = connection.createStatement()) {
                release.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
            }
        }
    }
}
