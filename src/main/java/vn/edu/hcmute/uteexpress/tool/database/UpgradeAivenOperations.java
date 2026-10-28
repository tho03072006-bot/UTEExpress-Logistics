package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;

/** Nâng cấp bổ sung, chạy tay; không DROP, không đổi dữ liệu đơn/khách hàng hiện có. */
public final class UpgradeAivenOperations {
    private static final Set<String> BASE_TABLES = Set.of("app_user", "shipment_order", "saved_address",
        "draft_order", "promo_code", "order_payment", "service_review", "email_change_request", "delivery_proof", "staff_account");
    private UpgradeAivenOperations() { }
    public static void main(String[] args) {
        if (args.length != 1 || !"--apply".equals(args[0])) {
            System.err.println("Dừng ứng dụng của cả nhóm, rồi dùng --apply để bổ sung schema trên Aiven.");
            System.exit(1);
        }
        try (var connection = AivenConnectionSettings.open()) {
            upgrade(connection);
            System.out.println("Nâng cấp Aiven thành công: thêm bảng cước, nhật ký và phiên bản chống cập nhật đè.");
            System.out.println("Không xóa bảng/dữ liệu cũ. Có thể chạy lại; không ghi đè cước đã cấu hình.");
        } catch (Exception ex) {
            System.err.println("Nâng cấp chưa hoàn tất. DDL MySQL tự commit; kiểm tra schema trước khi chạy lại."
                + (ex instanceof SQLException sql ? " SQLState=" + sql.getSQLState() : ""));
            System.exit(1);
        }
    }
    public static void upgrade(Connection connection) throws SQLException {
        var meta = connection.getMetaData();
        if (!"MySQL".equals(meta.getDatabaseProductName()) || meta.getDatabaseMajorVersion() < 8
            || connection.getCatalog() == null) { throw new IllegalArgumentException("Cần database Aiven MySQL 8."); }
        try (var statement = connection.createStatement(); var lock = statement.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if (!lock.next() || lock.getInt(1) != 1) { throw new IllegalStateException("Schema đang được nâng cấp bởi tiến trình khác."); }
        }
        try {
            var tables = new HashSet<String>();
            try (var result = meta.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (result.next()) { tables.add(result.getString("TABLE_NAME").toLowerCase(Locale.ROOT)); }
            }
            if (!tables.containsAll(BASE_TABLES)) { throw new IllegalStateException("Schema gốc chưa đủ; không tự khởi tạo/ghi đè."); }
            for (String table : List.of("shipment_order", "promo_code")) {
                if (!hasColumn(connection, table, "revision")) {
                    try (var statement = connection.createStatement()) {
                        statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN revision BIGINT NOT NULL DEFAULT 0");
                    }
                }
            }
            for (String sql : AivenSchema.statements()) {
                if ((sql.startsWith("create table shipping_tariff ") && !tables.contains("shipping_tariff"))
                    || (sql.startsWith("create table audit_event ") && !tables.contains("audit_event"))) {
                    try (var statement = connection.createStatement()) { statement.executeUpdate(sql); }
                }
            }
            seedTariffs(connection);
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')"); }
        }
    }
    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (var result = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column)) {
            if (!result.next()) { return false; }
            if (result.getInt("DATA_TYPE") != Types.BIGINT || result.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls) {
                throw new IllegalStateException("Cột revision hiện có không hợp lệ; không tự đổi/xóa.");
            }
            return true;
        }
    }
    static void seedTariffs(Connection connection) throws SQLException {
        // Giữ giá demo cũ để việc nâng cấp không đổi cước của User; Admin đổi sau qua UI.
        String sql = "INSERT INTO shipping_tariff(service_type, base_fee, fee_per_kg, revision, updated_at)"
            + " SELECT ?, ?, 3000, 0, NOW() FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM shipping_tariff WHERE service_type = ?)";
        try (var statement = connection.prepareStatement(sql)) {
            String[] types = {"STANDARD", "EXPRESS", "SUPER_EXPRESS"};
            int[] fees = {15000, 25000, 40000};
            for (int i = 0; i < types.length; i++) {
                statement.setString(1, types[i]); statement.setInt(2, fees[i]); statement.setString(3, types[i]);
                statement.executeUpdate();
            }
        }
    }
}

