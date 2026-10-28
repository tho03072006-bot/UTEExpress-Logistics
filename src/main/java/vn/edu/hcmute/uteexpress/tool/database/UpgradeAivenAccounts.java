package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;

/** Bổ sung chống ghi đè và thu hồi phiên, không xóa/ghi đè tài khoản hiện có. */
public final class UpgradeAivenAccounts {
    private UpgradeAivenAccounts() { }
    public static void main(String[] args) {
        if (args.length != 1 || !"--apply".equals(args[0])) {
            System.err.println("Dừng ứng dụng nhóm, rồi dùng --apply để bổ sung schema tài khoản Aiven."); System.exit(1);
        }
        try (var connection = AivenConnectionSettings.open()) {
            upgrade(connection);
            System.out.println("Nâng cấp tài khoản Aiven thành công: revision, locked_by_admin, access_version.");
            System.out.println("Không xóa tài khoản, đơn hàng hoặc dữ liệu liên kết; có thể chạy lại an toàn.");
        } catch (Exception ex) {
            System.err.println("Nâng cấp tài khoản chưa hoàn tất; kiểm tra schema/cấu hình. DDL MySQL tự commit."
                + (ex instanceof SQLException sql ? " SQLState=" + sql.getSQLState() : "")); System.exit(1);
        }
    }
    public static void upgrade(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        if (!"MySQL".equals(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() < 8
                || connection.getCatalog() == null) { throw new IllegalArgumentException("Cần Aiven MySQL 8."); }
        try (var tables = metadata.getTables(connection.getCatalog(), null, "app_user", new String[]{"TABLE"})) {
            if (!tables.next()) { throw new IllegalStateException("Thiếu app_user; không tự khởi tạo/ghi đè."); }
        }
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if (!result.next() || result.getInt(1) != 1) { throw new IllegalStateException("Schema đang được nâng cấp."); }
        }
        try {
            addColumn(connection, "revision", "BIGINT", Types.BIGINT);
            addColumn(connection, "locked_by_admin", "BIT", Types.BIT);
            addColumn(connection, "access_version", "BIGINT", Types.BIGINT);
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')"); }
        }
    }
    private static void addColumn(Connection connection, String column, String sqlType, int jdbcType) throws SQLException {
        try (var existing = connection.getMetaData().getColumns(connection.getCatalog(), null, "app_user", column)) {
            if (existing.next()) {
                if (existing.getInt("DATA_TYPE") != jdbcType || existing.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls) {
                    throw new IllegalStateException("Cột hiện có không đúng kiểu; không tự sửa/xóa.");
                }
                return;
            }
        }
        // Tên cột/kiểu chỉ từ hằng số trên, không nhận từ tham số người dùng.
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("ALTER TABLE app_user ADD COLUMN " + column + " " + sqlType + " NOT NULL DEFAULT 0");
        }
    }
}
