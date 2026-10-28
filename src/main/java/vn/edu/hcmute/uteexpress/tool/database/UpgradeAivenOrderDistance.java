package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;

/** Migration V7, chạy tay: chỉ thêm 2 cột NULL cho phụ phí khoảng cách; không UPDATE/DELETE đơn cũ, không tự chạy khi mở app. */
public final class UpgradeAivenOrderDistance {
    static final List<UpgradeAivenAuthValidation.Column> COLUMNS = List.of(
        new UpgradeAivenAuthValidation.Column("shipment_order", "distance_km", "DECIMAL(7,2) NULL", Types.DECIMAL, 7, true),
        new UpgradeAivenAuthValidation.Column("shipment_order", "distance_fee", "DECIMAL(12,0) NULL", Types.DECIMAL, 12, true));
    private UpgradeAivenOrderDistance() { }

    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("--check", "--apply", "--print-schema").contains(args[0])) {
            System.err.println("Dùng --check, --apply hoặc --print-schema."); System.exit(1);
        }
        try {
            if (args[0].equals("--print-schema")) { for (var column : COLUMNS) System.out.println(sql(column) + ";"); return; }
            try (var connection = AivenConnectionSettings.open()) {
                if (args[0].equals("--apply")) upgrade(connection);
                UpgradeAivenAuthValidation.validateTarget(connection);
                for (var column : COLUMNS) System.out.println(column.table() + "." + column.name() + ": "
                        + (UpgradeAivenAuthValidation.exists(connection, column) ? "đã có" : "chưa có"));
                System.out.println("Không xóa hoặc sửa đơn hàng hiện có; không in thông tin kết nối.");
            }
        } catch (Exception ex) {
            System.err.println("Chưa hoàn tất migration khoảng cách. DDL MySQL tự commit; chạy --check trước khi thử lại."
                + (ex instanceof SQLException sql ? " SQLState=" + sql.getSQLState() : "")); System.exit(1);
        }
    }

    public static void upgrade(Connection connection) throws SQLException {
        UpgradeAivenAuthValidation.validateTarget(connection);
        try (var statement = connection.createStatement(); var lock = statement.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if (!lock.next() || lock.getInt(1) != 1) throw new IllegalStateException("Schema đang được nâng cấp.");
        }
        try {
            // Kiểm tra cột có sẵn (nếu có) đúng kiểu trước khi thêm cột nào; không tự đổi kiểu/xóa cột.
            for (var column : COLUMNS) UpgradeAivenAuthValidation.exists(connection, column);
            for (var column : COLUMNS) if (!UpgradeAivenAuthValidation.exists(connection, column)) {
                try (var statement = connection.createStatement()) { statement.setQueryTimeout(30); statement.executeUpdate(sql(column)); }
            }
            for (var column : COLUMNS) if (!UpgradeAivenAuthValidation.exists(connection, column)) throw new IllegalStateException("Schema chưa đầy đủ.");
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')"); }
        }
    }

    static String sql(UpgradeAivenAuthValidation.Column column) {
        return "ALTER TABLE " + column.table() + " ADD COLUMN " + column.name() + " " + column.definition();
    }
}
