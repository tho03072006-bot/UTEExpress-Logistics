package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;

/** Migration thêm bảng dấu vết token, không cập nhật/xóa dữ liệu nghiệp vụ. Không tự chạy lúc startup. */
public final class UpgradeAivenOrderSubmissions {
    static final String TABLE = "order_form_submission";
    static final String DDL = "CREATE TABLE order_form_submission ("
        + "id BIGINT NOT NULL AUTO_INCREMENT, token VARCHAR(36) NOT NULL, user_id BIGINT NOT NULL, "
        + "request_hash VARCHAR(64) NOT NULL, purpose VARCHAR(10) NOT NULL, resource_id BIGINT NULL, created_at DATETIME(6) NOT NULL, "
        + "PRIMARY KEY (id), CONSTRAINT uk_order_form_token UNIQUE (token), "
        + "CONSTRAINT fk_order_form_user FOREIGN KEY (user_id) REFERENCES app_user(id)) "
        + "ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci";
    static final List<UpgradeAivenAuthValidation.Column> COLUMNS = List.of(
        column("id", Types.BIGINT, 0, false), column("token", Types.VARCHAR, 36, false),
        column("user_id", Types.BIGINT, 0, false), column("request_hash", Types.VARCHAR, 64, false),
        column("purpose", Types.VARCHAR, 10, false), column("resource_id", Types.BIGINT, 0, true),
        column("created_at", Types.TIMESTAMP, 0, false));
    private UpgradeAivenOrderSubmissions() { }
    private static UpgradeAivenAuthValidation.Column column(String name, int type, int size, boolean nullable) {
        return new UpgradeAivenAuthValidation.Column(TABLE, name, "", type, size, nullable);
    }
    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("--check", "--apply", "--print-schema").contains(args[0])) {
            System.err.println("Dùng --check, --apply hoặc --print-schema."); System.exit(1);
        }
        try {
            if (args[0].equals("--print-schema")) { System.out.println(DDL + ";"); return; }
            try (var connection = AivenConnectionSettings.open()) {
                if (args[0].equals("--apply")) upgrade(connection);
                validateTarget(connection);
                System.out.println("Schema token biểu mẫu: " + (ready(connection) ? "đã đầy đủ" : "chưa có, cần migration"));
                System.out.println("Không xóa/sửa bản ghi hiện có; không in thông tin kết nối.");
            }
        } catch (Exception ex) {
            System.err.println("Chưa hoàn tất migration token. Không tự sửa/xóa bảng có sẵn; kiểm tra schema trước khi thử lại."
                + (ex instanceof SQLException sql ? " SQLState=" + sql.getSQLState() : "")); System.exit(1);
        }
    }
    public static void upgrade(Connection connection) throws SQLException {
        validateTarget(connection);
        try (var statement = connection.createStatement(); var lock = statement.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if (!lock.next() || lock.getInt(1) != 1) throw new IllegalStateException("Schema đang được nâng cấp.");
        }
        try {
            if (!ready(connection)) {
                try (var statement = connection.createStatement()) { statement.setQueryTimeout(30); statement.executeUpdate(DDL); }
            }
            if (!ready(connection)) throw new IllegalStateException("Schema chưa đầy đủ.");
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')"); }
        }
    }
    private static void validateTarget(Connection connection) throws SQLException {
        UpgradeAivenAuthValidation.validateTarget(connection);
        if (!tableExists(connection, "draft_order")) throw new IllegalStateException("Thiếu bảng giỏ đơn; không tự khởi tạo database.");
    }
    static boolean tableExists(Connection connection, String table) throws SQLException {
        try (var rows = connection.getMetaData().getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
            while (rows.next()) if (table.equalsIgnoreCase(rows.getString("TABLE_NAME"))) return true;
            return false;
        }
    }
    static boolean ready(Connection connection) throws SQLException {
        if (!tableExists(connection, TABLE)) return false;
        for (var column : COLUMNS) if (!UpgradeAivenAuthValidation.exists(connection, column)) throw incompatible();
        var keys = new ArrayList<String>();
        try (var rows = connection.getMetaData().getPrimaryKeys(connection.getCatalog(), null, TABLE)) {
            while (rows.next()) keys.add(rows.getString("COLUMN_NAME"));
        }
        if (!keys.equals(List.of("id"))) throw incompatible();
        try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), null, TABLE, "id")) {
            boolean generated = false;
            while (rows.next()) if (TABLE.equalsIgnoreCase(rows.getString("TABLE_NAME")) && "id".equalsIgnoreCase(rows.getString("COLUMN_NAME")))
                generated = "YES".equalsIgnoreCase(rows.getString("IS_AUTOINCREMENT"));
            if (!generated) throw incompatible();
        }
        var indexes = new HashMap<String, List<String>>();
        try (var rows = connection.getMetaData().getIndexInfo(connection.getCatalog(), null, TABLE, true, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME"), column = rows.getString("COLUMN_NAME");
                if (name != null && column != null) indexes.computeIfAbsent(name, ignored -> new ArrayList<>()).add(column);
            }
        }
        if (indexes.values().stream().noneMatch(columns -> columns.equals(List.of("token")))) throw incompatible();
        boolean ownerKey = false;
        try (var rows = connection.getMetaData().getImportedKeys(connection.getCatalog(), null, TABLE)) {
            while (rows.next()) if ("user_id".equalsIgnoreCase(rows.getString("FKCOLUMN_NAME"))
                    && "app_user".equalsIgnoreCase(rows.getString("PKTABLE_NAME")) && "id".equalsIgnoreCase(rows.getString("PKCOLUMN_NAME"))
                    && rows.getInt("DELETE_RULE") != DatabaseMetaData.importedKeyCascade) ownerKey = true;
        }
        if (!ownerKey) throw incompatible();
        return true;
    }
    private static IllegalStateException incompatible() { return new IllegalStateException("Bảng dấu vết token hiện có không tương thích; không tự ghi đè."); }
}
