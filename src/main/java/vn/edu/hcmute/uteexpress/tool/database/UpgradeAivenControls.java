package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;

/** Nâng cấp bổ sung: chỉ CREATE bảng còn thiếu và FK/index của bốn bảng mới, không DROP/ghi đè dữ liệu. */
public final class UpgradeAivenControls {
    static final Set<String> TABLES = Set.of("operations_policy", "operations_case", "complaint_activity", "shipping_tariff_version");
    private UpgradeAivenControls() { }
    public static void main(String[] args) {
        if (args.length != 1 || !"--apply".equals(args[0])) {
            System.err.println("Dừng app của nhóm; dùng --apply để thêm bảng kiểm soát vận hành trên Aiven."); System.exit(1);
        }
        try (var connection = AivenConnectionSettings.open()) {
            upgrade(connection); System.out.println("Đã bổ sung 4 bảng kiểm soát vận hành; không xóa dữ liệu hiện có.");
        } catch (Exception ex) {
            System.err.println("Nâng cấp chưa hoàn tất. MySQL DDL tự commit; kiểm tra trước khi chạy lại."
                    + (ex instanceof SQLException sql ? " SQLState=" + sql.getSQLState() : "")); System.exit(1);
        }
    }
    public static void upgrade(Connection connection) throws SQLException {
        var meta = connection.getMetaData();
        if (!"MySQL".equals(meta.getDatabaseProductName()) || meta.getDatabaseMajorVersion() < 8 || connection.getCatalog() == null)
            throw new IllegalArgumentException("Cần Aiven MySQL 8 đã có schema của nhóm.");
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if (!result.next() || result.getInt(1) != 1) throw new IllegalStateException("Một tiến trình khác đang nâng cấp.");
        }
        try {
            var existing = new HashSet<String>();
            try (var result = meta.getTables(connection.getCatalog(), null, "%", new String[]{"TABLE"})) {
                while (result.next()) existing.add(result.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
            if (!existing.containsAll(Set.of("shipment_order", "cod_settlement", "order_complaint", "shipping_tariff", "audit_event")))
                throw new IllegalStateException("Chưa đủ schema nền. Không tự khởi tạo hoặc ghi đè.");
            List<String> ddl = AivenSchema.statements();
            for (String sql : ddl) {
                if (!sql.startsWith("create table ")) continue;
                String table = sql.split(" ")[2];
                if (TABLES.contains(table) && !existing.contains(table)) execute(connection, sql);
            }
            // Tương thích chạy lại với bản migration đầu: cột token nullable cho hồ sơ cũ, không ghi đè dữ liệu.
            try (var columns = meta.getColumns(connection.getCatalog(), null, "operations_case", "request_token")) {
                if (!columns.next()) execute(connection, "ALTER TABLE operations_case ADD COLUMN request_token VARCHAR(36) NULL");
                else if (columns.getInt("DATA_TYPE") != Types.VARCHAR || columns.getInt("COLUMN_SIZE") != 36)
                    throw new IllegalStateException("Cột request_token không đúng kiểu; không tự đổi/xóa.");
            }
            if (!hasUniqueRequestToken(connection)) execute(connection,
                    "ALTER TABLE operations_case ADD CONSTRAINT uk_operations_case_request_token UNIQUE (request_token)");
            for (String sql : ddl) {
                if (sql.startsWith("alter table ")) {
                    String[] parts = sql.split(" "); String table = parts[2];
                    if (TABLES.contains(table) && parts.length > 5 && !hasForeignKey(connection, table, parts[5])) execute(connection, sql);
                } else if (sql.startsWith("create index ")) {
                    String[] parts = sql.split(" ");
                    if (parts.length > 4 && TABLES.contains(parts[4]) && !hasIndex(connection, parts[4], parts[2])) execute(connection, sql);
                }
            }
            seedPolicy(connection);
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')"); }
        }
    }
    static void seedPolicy(Connection connection) throws SQLException {
        execute(connection, "INSERT INTO operations_policy(id, revision, pickup_hours, standard_hours, express_hours, super_express_hours, "
                + "max_delivery_attempts, complaint_response_hours, complaint_resolve_hours, updated_by, updated_at) "
                + "SELECT 1, 0, 24, 120, 48, 4, 3, 24, 168, 'database-operator', NOW() FROM DUAL "
                + "WHERE NOT EXISTS (SELECT 1 FROM operations_policy WHERE id = 1)");
    }
    private static boolean hasForeignKey(Connection connection, String table, String name) throws SQLException {
        try (var result = connection.getMetaData().getImportedKeys(connection.getCatalog(), null, table)) {
            while (result.next()) if (name.equalsIgnoreCase(result.getString("FK_NAME"))) return true;
            return false;
        }
    }
    private static boolean hasUniqueRequestToken(Connection connection) throws SQLException {
        var indexes = new HashMap<String, List<String>>();
        try (var result = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, "operations_case", true, false)) {
            while (result.next()) {
                String index = result.getString("INDEX_NAME"), column = result.getString("COLUMN_NAME");
                if (index != null && column != null) indexes.computeIfAbsent(index, ignored -> new ArrayList<>()).add(column);
            }
            return indexes.values().stream().anyMatch(columns -> columns.size() == 1 && "request_token".equalsIgnoreCase(columns.get(0)));
        }
    }
    private static boolean hasIndex(Connection connection, String table, String name) throws SQLException {
        try (var result = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (result.next()) if (name.equalsIgnoreCase(result.getString("INDEX_NAME"))) return true;
            return false;
        }
    }
    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }
}
