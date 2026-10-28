package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;

/** Migration bổ sung, chạy tay: không DROP/UPDATE/DELETE dữ liệu nghiệp vụ, không tự chạy khi mở app. */
public final class UpgradeAivenAuthValidation {
    record Column(String table,String name,String definition,int type,int size,boolean nullable) { }
    static final List<Column> COLUMNS=List.of(
        new Column("app_user","otp_attempts","INT NOT NULL DEFAULT 0",Types.INTEGER,0,false),
        new Column("app_user","otp_last_sent_at","DATETIME(6) NULL",Types.TIMESTAMP,0,true),
        new Column("app_user","otp_window_start","DATETIME(6) NULL",Types.TIMESTAMP,0,true),
        new Column("app_user","otp_send_count","INT NOT NULL DEFAULT 0",Types.INTEGER,0,false),
        new Column("shipment_order","creation_token","VARCHAR(36) NULL",Types.VARCHAR,36,true),
        new Column("shipment_order","creation_hash","VARCHAR(64) NULL",Types.VARCHAR,64,true));
    private UpgradeAivenAuthValidation() { }
    public static void main(String[] args) {
        if(args.length!=1 || !Set.of("--check","--apply","--print-schema").contains(args[0])) {
            System.err.println("Dùng --check, --apply hoặc --print-schema."); System.exit(1);
        }
        try {
            if(args[0].equals("--print-schema")) { for(var column:COLUMNS) System.out.println(sql(column)+";"); System.out.println(indexSql()+";"); return; }
            try(var connection=AivenConnectionSettings.open()) {
                if(args[0].equals("--apply")) upgrade(connection);
                validateTarget(connection);
                for(var column:COLUMNS) System.out.println(column.table()+"."+column.name()+": "+(exists(connection,column)?"đã có":"chưa có"));
                System.out.println("Khóa duy nhất creation_token: "+(hasUniqueToken(connection)?"đã có":"chưa có"));
                System.out.println("Không xóa hoặc sửa tài khoản/đơn hàng hiện có; không in thông tin kết nối.");
            }
        } catch(Exception ex) {
            System.err.println("Chưa hoàn tất migration. DDL MySQL tự commit; chạy --check trước khi thử lại."
                +(ex instanceof SQLException sql?" SQLState="+sql.getSQLState():"")); System.exit(1);
        }
    }
    public static void upgrade(Connection connection) throws SQLException {
        validateTarget(connection);
        try(var statement=connection.createStatement();var result=statement.executeQuery("SELECT GET_LOCK('uteexpress-schema-initialize', 10)")) {
            if(!result.next() || result.getInt(1)!=1) throw new IllegalStateException("Schema đang được nâng cấp.");
        }
        try {
            // Kiểm tra toàn bộ cột có sẵn trước khi thêm bất kỳ cột nào; không tự đổi kiểu/xóa cột.
            for(var column:COLUMNS) exists(connection,column);
            for(var column:COLUMNS) if(!exists(connection,column)) execute(connection,sql(column));
            if(!hasUniqueToken(connection)) execute(connection,indexSql());
            for(var column:COLUMNS) if(!exists(connection,column)) throw new IllegalStateException("Schema chưa đầy đủ.");
        } finally { try(var statement=connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')"); } }
    }
    private static String sql(Column column) { return "ALTER TABLE "+column.table()+" ADD COLUMN "+column.name()+" "+column.definition(); }
    private static String indexSql() { return "ALTER TABLE shipment_order ADD CONSTRAINT uk_shipment_creation_token UNIQUE (creation_token)"; }
    static void validateTarget(Connection connection) throws SQLException {
        var meta=connection.getMetaData();
        if(!"MySQL".equals(meta.getDatabaseProductName()) || meta.getDatabaseMajorVersion()<8 || connection.getCatalog()==null)
            throw new IllegalArgumentException("Chỉ nâng cấp Aiven MySQL 8+ đã có schema UTEExpress.");
        for(String table:List.of("app_user","shipment_order")) {
            boolean found=false;
            try(var rows=meta.getTables(connection.getCatalog(),null,table,new String[]{"TABLE"})) {
                while(rows.next()) found|=table.equalsIgnoreCase(rows.getString("TABLE_NAME"));
            }
            if(!found) throw new IllegalStateException("Thiếu bảng nền. Không tự khởi tạo/ghi đè.");
        }
    }
    static boolean exists(Connection connection,Column column) throws SQLException {
        try(var rows=connection.getMetaData().getColumns(connection.getCatalog(),null,column.table(),column.name())) {
            while(rows.next()) {
                if(!column.table().equalsIgnoreCase(rows.getString("TABLE_NAME")) || !column.name().equalsIgnoreCase(rows.getString("COLUMN_NAME")))continue;
                if(rows.getInt("DATA_TYPE")!=column.type() || (column.size()>0 && rows.getInt("COLUMN_SIZE")!=column.size())
                    || rows.getInt("NULLABLE")!=(column.nullable()?DatabaseMetaData.columnNullable:DatabaseMetaData.columnNoNulls))
                    throw new IllegalStateException("Cột hiện có không tương thích; không tự đổi hoặc xóa.");
                return true;
            }
            return false;
        }
    }
    static boolean hasUniqueToken(Connection connection) throws SQLException {
        var indexes=new HashMap<String,List<String>>();
        try(var rows=connection.getMetaData().getIndexInfo(connection.getCatalog(),null,"shipment_order",true,false)) {
            while(rows.next()) {
                String name=rows.getString("INDEX_NAME"),column=rows.getString("COLUMN_NAME");
                if(name!=null && column!=null) indexes.computeIfAbsent(name,ignored->new ArrayList<>()).add(column);
            }
        }
        return indexes.values().stream().anyMatch(columns->columns.size()==1 && columns.get(0).equalsIgnoreCase("creation_token"));
    }
    private static void execute(Connection connection,String sql) throws SQLException {
        try(var statement=connection.createStatement()) { statement.setQueryTimeout(30); statement.executeUpdate(sql); }
    }
}
