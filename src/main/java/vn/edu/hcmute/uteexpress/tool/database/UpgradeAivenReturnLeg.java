package vn.edu.hcmute.uteexpress.tool.database;

import java.io.PrintStream;
import java.sql.*;
import java.util.*;

/**
 * Chạy tay (B9 hoàn hàng liên tỉnh): thêm cột đánh dấu chiều ngược cho tuyến của vận đơn.
 * Chỉ ADD COLUMN có giá trị mặc định (mọi tuyến hiện có là chiều đi), không sửa hay xóa dữ liệu; chạy lại thì bỏ qua cột đã có.
 */
public final class UpgradeAivenReturnLeg {
    private static final String LOCK_NAME = "uteexpress-schema-initialize";
    static final String TABLE = "order_route", COLUMN = "return_leg", COLUMN_TYPE = "bit(1)";
    static final String DDL = "ALTER TABLE order_route ADD COLUMN return_leg BIT NOT NULL DEFAULT 0";
    private UpgradeAivenReturnLeg() { }

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
                    System.out.println("Đã bổ sung cột chiều hoàn cho tuyến vận đơn. Không sửa/xóa dữ liệu có sẵn.");
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
        output.println("-- Migration chạy tay: hoàn hàng liên tỉnh qua chuyến trung chuyển ngược. Chỉ thêm cột có giá trị mặc định.");
        output.println("-- --apply tự bỏ qua cột đã tồn tại; chạy lại nhiều lần không lỗi.");
        output.println(DDL + ";");
    }

    /** Chỉ đọc. Trả về 1 nếu cột còn thiếu, 0 nếu đủ; ném lỗi nếu cột đã có nhưng sai kiểu hoặc sai mặc định. */
    public static int check(Connection connection, PrintStream output) throws SQLException {
        connection.setReadOnly(true);
        validateTarget(connection);
        output.println("Kiểm tra chỉ đọc: KHÔNG tạo cột, KHÔNG ghi dữ liệu.");
        var actual = column(connection);
        if (actual == null) {
            output.println(TABLE + "." + COLUMN + ": còn thiếu");
            output.println("Chưa đầy đủ. Dừng tại đây, người vận hành review rồi tự chạy --apply.");
            return 1;
        }
        verify(actual);
        output.println(TABLE + "." + COLUMN + ": đã có (" + actual.type() + " NOT NULL DEFAULT " + actual.defaultValue() + ")");
        output.println("Schema đã đầy đủ.");
        return 0;
    }

    public static void upgrade(Connection connection) throws SQLException {
        if (!connection.getAutoCommit() || connection.isReadOnly()) throw new IllegalArgumentException("Migration cần kết nối ghi riêng.");
        validateTarget(connection);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT GET_LOCK('" + LOCK_NAME + "', 10)")) {
            if (!rows.next() || rows.getInt(1) != 1) throw new IllegalStateException("Schema đang được nâng cấp bởi tiến trình khác; chưa ghi dữ liệu.");
        }
        try {
            var actual = column(connection);
            if (actual != null) { verify(actual); return; }
            try (var statement = connection.createStatement()) { statement.setQueryTimeout(60); statement.executeUpdate(DDL); }
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('" + LOCK_NAME + "')"); }
        }
    }

    record ActualColumn(String type, boolean nullable, String defaultValue) { }

    /** MySQL ghi mặc định của BIT là b'0'; chấp nhận cả dạng 0. */
    static void verify(ActualColumn actual) {
        String defaultValue = actual.defaultValue() == null ? null : actual.defaultValue().replaceAll("['()\\s]", "");
        boolean defaultOk = "0".equals(defaultValue) || "b0".equalsIgnoreCase(defaultValue);
        if (!COLUMN_TYPE.equalsIgnoreCase(actual.type()) || actual.nullable() || !defaultOk)
            throw new IllegalStateException("Cột " + TABLE + "." + COLUMN + " đã có nhưng khác định nghĩa mong muốn (" + COLUMN_TYPE
                    + " NOT NULL DEFAULT 0; thực tế " + actual.type() + (actual.nullable() ? " NULL" : " NOT NULL")
                    + " DEFAULT " + actual.defaultValue() + "). Không tự sửa.");
    }

    private static ActualColumn column(Connection connection) throws SQLException {
        try (var statement = connection.prepareStatement("SELECT column_type, is_nullable, column_default FROM information_schema.columns"
                + " WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            statement.setString(1, TABLE); statement.setString(2, COLUMN);
            try (var rows = statement.executeQuery()) {
                return rows.next() ? new ActualColumn(rows.getString(1), "YES".equalsIgnoreCase(rows.getString(2)), rows.getString(3)) : null;
            }
        }
    }

    private static void validateTarget(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        if (!"MySQL".equals(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() < 8)
            throw new IllegalArgumentException("Chỉ kiểm tra/nâng cấp MySQL 8+ đã có schema UTEExpress.");
        if (!UpgradeAivenOrderSubmissions.tableExists(connection, TABLE))
            throw new IllegalStateException("Thiếu bảng order_route; hãy chạy UpgradeAivenBranches trước.");
    }
}
