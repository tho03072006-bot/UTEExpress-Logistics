package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.Map;

/** Chỉ thêm hai cột nullable chứa mã tệp; không xóa ảnh/blob hay cập nhật dữ liệu cũ. */
public final class UpgradeAivenCloudFiles {
    private static final Map<String,String> COLUMNS = Map.of("order_complaint","image_asset","operations_case","evidence_asset");
    private UpgradeAivenCloudFiles() { }
    public static void main(String[] args) {
        if (args.length!=1 || !java.util.Set.of("--check","--apply").contains(args[0])) {
            System.err.println("Dùng --check hoặc --apply. Không xóa dữ liệu/file cũ."); System.exit(1);
        }
        try (var connection = AivenConnectionSettings.open()) {
            upgrade(connection,args[0].equals("--apply"));
            System.out.println(args[0].equals("--apply") ? "Đã chuẩn bị hai cột mã tệp Cloudinary; giữ toàn bộ blob cũ." : "Đã kiểm tra schema; không thay đổi dữ liệu.");
        } catch (Exception ex) {
            System.err.println("Không hoàn tất kiểm tra/nâng cấp cloud-files; không tự chạy lại hoặc xóa dữ liệu."
                    +(ex instanceof SQLException sql ? " SQLState="+sql.getSQLState() : "")); System.exit(1);
        }
    }
    public static void upgrade(Connection connection,boolean apply) throws SQLException {
        var meta = connection.getMetaData();
        if (!"MySQL".equals(meta.getDatabaseProductName()) || meta.getDatabaseMajorVersion()<8 || connection.getCatalog()==null)
            throw new IllegalArgumentException("Chỉ dùng schema Aiven MySQL 8 của nhóm.");
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if (!result.next() || result.getInt(1)!=1) throw new IllegalStateException("Đang có tiến trình nâng cấp khác.");
        }
        try {
            // Kiểm tra cả hai bảng/cột trước, không tự tạo schema mới hoặc đổi kiểu cột đang có.
            var missing = new java.util.ArrayList<Map.Entry<String,String>>();
            for (var entry : COLUMNS.entrySet()) {
                try (var table = meta.getTables(connection.getCatalog(),null,entry.getKey(),new String[]{"TABLE"})) {
                    if (!table.next()) throw new IllegalStateException("Thiếu schema nền.");
                }
                try (var column = meta.getColumns(connection.getCatalog(),null,entry.getKey(),entry.getValue())) {
                    if (!column.next()) missing.add(entry);
                    else if (column.getInt("DATA_TYPE")!=Types.VARCHAR || column.getInt("COLUMN_SIZE")!=255
                            || column.getInt("NULLABLE")!=DatabaseMetaData.columnNullable)
                        throw new IllegalStateException("Cột cloud có cấu trúc khác dự kiến.");
                }
            }
            if (apply) for (var entry : missing) {
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE "+entry.getKey()+" ADD COLUMN "+entry.getValue()+" VARCHAR(255) NULL, ALGORITHM=INSTANT");
                }
            }
            else for (var entry : missing) System.out.println("Thiếu cột "+entry.getKey()+"."+entry.getValue());
        } finally {
            try (var statement=connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')"); }
        }
    }
}
