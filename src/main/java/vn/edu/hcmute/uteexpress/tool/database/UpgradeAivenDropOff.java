package vn.edu.hcmute.uteexpress.tool.database;

import java.io.PrintStream;
import java.sql.*;
import java.util.*;

/**
 * Chạy tay: thêm hình thức gửi hàng "Tự mang ra chi nhánh".
 * Chỉ ADD COLUMN có giá trị mặc định vào hai bảng đã có, không sửa hay xóa dữ liệu; chạy lại lần hai thì bỏ qua cột đã có.
 */
public final class UpgradeAivenDropOff {
    private static final String LOCK_NAME = "uteexpress-schema-initialize";
    /** Bảng, cột, kiểu MySQL mong muốn (information_schema.columns.column_type), giá trị mặc định, câu ALTER. */
    record Change(String table, String column, String columnType, String defaultValue, String ddl) { }
    static final List<Change> CHANGES = List.of(
            new Change("order_route", "pickup_mode", "enum('COURIER','DROP_OFF')", "COURIER",
                    "ALTER TABLE order_route ADD COLUMN pickup_mode ENUM('COURIER','DROP_OFF') NOT NULL DEFAULT 'COURIER'"),
            new Change("operations_policy", "drop_off_discount", "decimal(12,0)", "3000",
                    "ALTER TABLE operations_policy ADD COLUMN drop_off_discount DECIMAL(12,0) NOT NULL DEFAULT 3000"));
    private UpgradeAivenDropOff() { }

    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("--print-schema", "--check", "--apply").contains(args[0])) {
            System.err.println("Dùng --print-schema (không kết nối), --check (chỉ đọc) hoặc --apply (người vận hành tự chạy).");
            System.exit(1); return;
        }
        try {
            if (args[0].equals("--print-schema")) { printSchema(System.out); return; }
            try (var connection = AivenConnectionSettings.open()) {
                if (args[0].equals("--apply")) {
                    upgrade(connection);
                    System.out.println("Đã bổ sung cột cho hình thức tự mang ra chi nhánh. Không sửa/xóa dữ liệu có sẵn.");
                }
                int missing = check(connection, System.out);
                if (missing > 0) System.exit(2);
            }
        } catch (Exception ex) {
            System.err.println("Chưa hoàn tất kiểm tra/migration. Không in thông tin kết nối.");
            System.err.println("Exception=" + ex.getClass().getName() + (ex instanceof SQLException sql
                    ? "; SQLState=" + sql.getSQLState() + "; errorCode=" + sql.getErrorCode() : "") + "; message=" + ex.getMessage());
            System.exit(1);
        }
    }

    public static void printSchema(PrintStream output) {
        output.println("-- Migration chạy tay: hình thức gửi hàng tự mang ra chi nhánh. Chỉ thêm cột có giá trị mặc định.");
        output.println("-- --apply tự bỏ qua cột đã tồn tại; chạy lại nhiều lần không lỗi.");
        for (Change change : CHANGES) output.println(change.ddl() + ";");
    }

    /** Chỉ đọc. Trả về số cột còn thiếu; ném lỗi nếu cột đã có nhưng sai kiểu hoặc sai mặc định. */
    public static int check(Connection connection, PrintStream output) throws SQLException {
        connection.setReadOnly(true);
        validateTarget(connection);
        int missing = 0;
        output.println("Kiểm tra chỉ đọc: KHÔNG tạo cột, KHÔNG ghi dữ liệu.");
        for (Change change : CHANGES) {
            var actual = column(connection, change);
            if (actual == null) { missing++; output.println(change.table() + "." + change.column() + ": còn thiếu"); continue; }
            verify(change, actual);
            output.println(change.table() + "." + change.column() + ": đã có (" + actual.type() + " NOT NULL DEFAULT " + actual.defaultValue() + ")");
        }
        output.println(missing == 0 ? "Schema đã đầy đủ." : "Chưa đầy đủ. Dừng tại đây, người vận hành review rồi tự chạy --apply.");
        return missing;
    }

    public static void upgrade(Connection connection) throws SQLException {
        if (!connection.getAutoCommit() || connection.isReadOnly()) throw new IllegalArgumentException("Migration cần kết nối ghi riêng.");
        validateTarget(connection);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT GET_LOCK('" + LOCK_NAME + "', 10)")) {
            if (!rows.next() || rows.getInt(1) != 1) throw new IllegalStateException("Schema đang được nâng cấp bởi tiến trình khác; chưa ghi dữ liệu.");
        }
        try {
            for (Change change : CHANGES) {
                var actual = column(connection, change);
                if (actual != null) { verify(change, actual); continue; }
                try (var statement = connection.createStatement()) { statement.setQueryTimeout(60); statement.executeUpdate(change.ddl()); }
            }
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('" + LOCK_NAME + "')"); }
        }
    }

    record ActualColumn(String type, boolean nullable, String defaultValue) { }

    static void verify(Change change, ActualColumn actual) {
        String defaultValue = actual.defaultValue() == null ? null : actual.defaultValue().replaceAll("['()\\s]", "");
        if (!change.columnType().equalsIgnoreCase(actual.type()) || actual.nullable() || !change.defaultValue().equals(defaultValue))
            throw new IllegalStateException("Cột " + change.table() + "." + change.column() + " đã có nhưng khác định nghĩa mong muốn ("
                    + change.columnType() + " NOT NULL DEFAULT " + change.defaultValue() + "; thực tế " + actual.type()
                    + (actual.nullable() ? " NULL" : " NOT NULL") + " DEFAULT " + actual.defaultValue() + "). Không tự sửa.");
    }

    private static ActualColumn column(Connection connection, Change change) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT column_type, is_nullable, column_default FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, change.table()); statement.setString(2, change.column());
            try (var rows = statement.executeQuery()) {
                return rows.next() ? new ActualColumn(rows.getString(1), "YES".equalsIgnoreCase(rows.getString(2)), rows.getString(3)) : null;
            }
        }
    }

    private static void validateTarget(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        if (!"MySQL".equals(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() < 8)
            throw new IllegalArgumentException("Chỉ kiểm tra/nâng cấp MySQL 8+ đã có schema UTEExpress.");
        for (String table : List.of("order_route", "operations_policy"))
            if (!UpgradeAivenOrderSubmissions.tableExists(connection, table))
                throw new IllegalStateException("Thiếu bảng " + table + "; hãy chạy UpgradeAivenBranches trước.");
    }
}
