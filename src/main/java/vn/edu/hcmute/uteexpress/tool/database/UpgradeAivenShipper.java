package vn.edu.hcmute.uteexpress.tool.database;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;

/** Nâng cấp bổ sung cho database đang có dữ liệu; không tự chạy lúc ứng dụng khởi động. */
public final class UpgradeAivenShipper {
    private UpgradeAivenShipper() { }
    private static final Map<String, Set<String>> COLUMNS = Map.of(
        "order_status_history", Set.of("id", "order_id", "actor_id", "actor_name", "previous_status", "status", "failure_reason", "note", "next_attempt_at", "occurred_at"),
        "shipper_notification", Set.of("id", "order_id", "recipient_id", "message", "created_at", "read_at"),
        "order_complaint", Set.of("id", "revision", "order_id", "subject", "description", "image_bytes", "status", "manager_reply", "shipper_note", "handled_by", "created_at", "updated_at"));
    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("--check", "--apply", "--print-schema").contains(args[0])) {
            System.err.println("Dùng --check, --apply hoặc --print-schema."); System.exit(1);
        }
        try {
            if (args[0].equals("--print-schema")) { statements().forEach(sql -> System.out.println(sql + ";")); return; }
            try (var connection = AivenConnectionSettings.open()) {
                validateTarget(connection);
                if (args[0].equals("--apply")) apply(connection);
                for (String table : new TreeSet<>(COLUMNS.keySet()))
                    System.out.println(table + ": " + (exists(connection, table) ? "đã có" : "chưa có"));
                System.out.println("Không thay đổi dữ liệu vận đơn, tài khoản hoặc cấu hình bí mật.");
            }
        } catch (IllegalArgumentException ex) { System.err.println(ex.getMessage()); System.exit(1); }
        catch (Exception ex) {
            System.err.println("Không hoàn tất nâng cấp. Kiểm tra kết nối TLS và quyền CREATE; chạy --check để xem bảng đã tạo. Không tự xóa bảng.");
            System.exit(1);
        }
    }
    static List<String> statements() throws java.io.IOException {
        try (var input = UpgradeAivenShipper.class.getResourceAsStream("/db/migration/V2__shipper_features.sql")) {
            if (input == null) throw new IllegalArgumentException("Thiếu script nâng cấp Shipper.");
            String script = new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
            var result = Arrays.stream(script.split(";")).map(String::strip).filter(s -> !s.isEmpty()).toList();
            if (result.size() != 3 || result.stream().anyMatch(s -> !s.startsWith("CREATE TABLE IF NOT EXISTS ")))
                throw new IllegalArgumentException("Script chỉ được thêm ba bảng chức năng mới.");
            return result;
        }
    }
    static void apply(Connection connection) throws Exception {
        validateTarget(connection);
        try (var statement = connection.createStatement(); var lock = statement.executeQuery("SELECT GET_LOCK('uteexpress-shipper-upgrade', 10)")) {
            if (!lock.next() || lock.getInt(1) != 1) throw new IllegalArgumentException("Database đang được nâng cấp bởi tiến trình khác.");
        }
        try {
            for (String table : COLUMNS.keySet()) if (exists(connection, table)) validateColumns(connection, table);
            for (String sql : statements()) {
                try (var statement = connection.createStatement()) { statement.executeUpdate(sql); }
            }
            for (String table : COLUMNS.keySet()) validateColumns(connection, table);
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-shipper-upgrade')"); }
        }
    }
    private static void validateTarget(Connection connection) throws SQLException {
        if (!"MySQL".equals(connection.getMetaData().getDatabaseProductName())
                || connection.getMetaData().getDatabaseMajorVersion() < 8
                || connection.getCatalog() == null
                || !exists(connection, "shipment_order") || !exists(connection, "app_user"))
            throw new IllegalArgumentException("Chỉ nâng cấp MySQL 8+ đã có các bảng UTEExpress.");
    }
    private static boolean exists(Connection connection, String table) throws SQLException {
        try (var result = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            while (result.next()) if (table.equalsIgnoreCase(result.getString("TABLE_NAME"))) return true;
            return false;
        }
    }
    private static void validateColumns(Connection connection, String table) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (var result = connection.getMetaData().getColumns(connection.getCatalog(), null, table, "%")) {
            while (result.next()) if (table.equalsIgnoreCase(result.getString("TABLE_NAME")))
                columns.add(result.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        if (!columns.containsAll(COLUMNS.get(table)))
            throw new IllegalArgumentException("Bảng " + table + " không đúng cấu trúc; cần kiểm tra thủ công, không tự sửa bảng cũ.");
        Map<String, String> expectedForeignKeys = switch (table) {
            case "order_status_history" -> Map.of("order_id", "shipment_order", "actor_id", "app_user");
            case "shipper_notification" -> Map.of("order_id", "shipment_order", "recipient_id", "app_user");
            default -> Map.of("order_id", "shipment_order", "handled_by", "app_user");
        };
        Map<String, String> foreignKeys = new HashMap<>();
        try (var result = connection.getMetaData().getImportedKeys(connection.getCatalog(), null, table)) {
            while (result.next()) foreignKeys.put(result.getString("FKCOLUMN_NAME"), result.getString("PKTABLE_NAME"));
        }
        if (!foreignKeys.entrySet().containsAll(expectedForeignKeys.entrySet()))
            throw new IllegalArgumentException("Bảng " + table + " thiếu khóa ngoại; cần kiểm tra thủ công.");
        if (!table.equals("order_status_history")) {
            Map<String, Set<String>> uniqueIndexes = new HashMap<>();
            try (var result = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, table, true, false)) {
                while (result.next()) {
                    String column = result.getString("COLUMN_NAME"), index = result.getString("INDEX_NAME");
                    if (index != null && column != null) uniqueIndexes.computeIfAbsent(index, k -> new HashSet<>()).add(column);
                }
            }
            Set<String> expected = table.equals("shipper_notification") ? Set.of("order_id", "recipient_id") : Set.of("order_id");
            if (!uniqueIndexes.containsValue(expected))
                throw new IllegalArgumentException("Bảng " + table + " thiếu ràng buộc duy nhất; cần kiểm tra thủ công.");
        }
    }
}
