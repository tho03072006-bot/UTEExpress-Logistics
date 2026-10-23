package vn.edu.hcmute.uteexpress.tool.database;

import java.io.PrintStream;
import java.sql.*;
import java.util.*;

/** Chạy tay: chỉ bổ sung cấu trúc/danh mục, không đổi đơn hàng hoặc tài khoản của nhóm. */
public final class UpgradeAivenBranches {
    private static final String LOCK_NAME = "uteexpress-schema-initialize";
    private static final List<String> BASE_TABLES = List.of("app_user", "shipment_order", "operations_policy", "saved_address", "draft_order");
    public record CheckReport(int missingTables, boolean policyReady, int missingBranches, int missingAreas,
                              int missingNeighbors, int missingTariffs) {
        public boolean ready() { return missingTables == 0 && policyReady && missingBranches == 0 && missingAreas == 0 && missingNeighbors == 0 && missingTariffs == 0; }
    }
    private UpgradeAivenBranches() { }

    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("--print-schema", "--check", "--apply").contains(args[0])) {
            System.err.println("Dùng --print-schema (không kết nối), --check (chỉ đọc) hoặc --apply (người vận hành tự chạy).");
            System.exit(1); return;
        }
        try {
            // Nhánh này phải đứng trước open(): xem SQL không được đọc cấu hình hoặc kết nối Aiven.
            if (args[0].equals("--print-schema")) { printSchema(System.out); return; }
            try (var connection = AivenConnectionSettings.open()) {
                if (args[0].equals("--apply")) {
                    upgrade(connection);
                    System.out.println("Đã bổ sung schema và danh mục chi nhánh. Không sửa/xóa đơn hàng, tài khoản hoặc giá đã cấu hình.");
                }
                var report = check(connection);
                printReport(report, System.out);
                if (!report.ready()) System.exit(2);
            }
        } catch (Exception ex) {
            printFailure(ex, System.err);
            System.err.println("DDL MySQL tự commit; nếu từng chạy --apply, hãy kiểm tra phần đã tạo trước khi thử lại.");
            System.exit(1);
        }
    }

    /** Không in stack trace hoặc cấu hình; giữ chẩn đoán SQL và che thông báo lỗi kết nối. */
    static void printFailure(Exception failure, PrintStream output) {
        output.println("Chưa hoàn tất kiểm tra/migration chi nhánh.");
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var pending = new ArrayDeque<Throwable>(); pending.add(failure);
        while (!pending.isEmpty()) {
            var error = pending.removeFirst();
            if (!visited.add(error)) continue;
            if (error instanceof SQLException sql) {
                String state = sql.getSQLState();
                boolean connectionError = state != null && (state.startsWith("08") || state.startsWith("28"));
                output.println("Exception=" + sql.getClass().getName() + "; SQLState=" + state + "; errorCode=" + sql.getErrorCode()
                        + "; message=" + (connectionError ? "[Đã ẩn chi tiết kết nối]" : safeMessage(sql.getMessage())));
                if (sql.getNextException() != null) pending.addLast(sql.getNextException());
            } else {
                output.println("Exception=" + error.getClass().getName()
                        + (error instanceof IllegalArgumentException || error instanceof IllegalStateException
                        ? "; message=" + safeMessage(error.getMessage()) : ""));
            }
            if (error.getCause() != null) pending.addLast(error.getCause());
            Collections.addAll(pending, error.getSuppressed());
        }
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) return "(không có thông báo)";
        // Lỗi xác thực/mạng có thể chứa user@host, URL hoặc mật khẩu; không giả định mọi SQLException đều an toàn.
        if (message.matches("(?is).*(?:jdbc:|[a-z][a-z0-9+.-]*://|\\b(?:user|username|password|passwd|pwd|host|hostname|server|address|credentials)\\b|\\.aivencloud\\.com\\b).*"))
            return "[Đã ẩn thông báo có thể chứa thông tin kết nối]";
        String singleLine = message.replaceAll("[\\r\\n\\t]+", " ");
        return singleLine.length() > 2000 ? singleLine.substring(0, 2000) + "…" : singleLine;
    }

    public static void printSchema(PrintStream output) {
        var catalog = BranchSeedData.load();
        output.println("-- Migration chạy tay của UTEExpress; người vận hành review trước khi tự chạy --apply.");
        output.println("-- Khu vực giao nhận nội bộ theo địa danh cũ; tọa độ tâm xấp xỉ, dùng cho sắp lộ trình.");
        output.println("-- Chỉ thêm bảng, thêm max_active_orders DEFAULT 15 và INSERT danh mục còn thiếu.");
        output.println("-- Công cụ kiểm tra cấu trúc/khóa và dữ liệu có sẵn trước khi ghi; không chạy SQL này lên schema chưa review.");
        for (var table : BranchSchema.TABLES) output.println(table.ddl() + ";\n");
        output.println("-- Chỉ thực thi ALTER dưới đây nếu cột chưa tồn tại; --apply tự kiểm tra điều kiện này.");
        output.println(BranchSchema.POLICY_DDL + ";\n");
        output.println("-- DDL tự commit. Chỉ phần INSERT danh mục được chạy trong giao dịch riêng.");
        output.println("START TRANSACTION;");
        seedStatements(catalog).forEach(sql -> output.println(sql + ";"));
        output.println("COMMIT;");
    }

    public static CheckReport check(Connection connection) throws SQLException {
        connection.setReadOnly(true);
        return inspect(connection, BranchSeedData.load());
    }

    public static void printReport(CheckReport report, PrintStream output) {
        output.println("Kiểm tra chỉ đọc: KHÔNG tạo bảng, KHÔNG ghi dữ liệu, KHÔNG lấy khóa migration.");
        output.println("Bảng mới còn thiếu: " + report.missingTables() + "/" + BranchSchema.TABLES.size());
        output.println("operations_policy.max_active_orders INT NOT NULL DEFAULT 15: " + (report.policyReady() ? "đã có" : "còn thiếu"));
        output.println("Danh mục còn thiếu: " + report.missingBranches() + " chi nhánh, " + report.missingAreas()
                + " khu vực, " + report.missingNeighbors() + " quan hệ lân cận có hướng, " + report.missingTariffs() + " biểu giá tuyến.");
        output.println(report.ready() ? "Schema và danh mục đã đầy đủ." : "Chưa đầy đủ. Dừng tại đây, người vận hành review rồi tự chạy --apply.");
    }

    public static void upgrade(Connection connection) throws SQLException {
        var catalog = BranchSeedData.load();
        if (!connection.getAutoCommit() || connection.isReadOnly()) throw new IllegalArgumentException("Migration cần kết nối ghi riêng, không nằm trong giao dịch khác.");
        // Kiểm tra trước khi lấy khóa; kiểm tra lại dưới khóa để tránh hai lần apply đồng thời.
        inspect(connection, catalog);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT GET_LOCK('" + LOCK_NAME + "', 10)")) {
            if (!rows.next() || rows.getInt(1) != 1) throw new IllegalStateException("Schema đang được nâng cấp bởi tiến trình khác; chưa ghi dữ liệu.");
        }
        Exception migrationFailure = null;
        try {
            if (inspect(connection, catalog).ready()) return;
            for (var table : BranchSchema.TABLES) if (!tableExists(connection, table.name())) execute(connection, table.ddl());
            if (!policyExists(connection)) execute(connection, BranchSchema.POLICY_DDL);
            // Tách giao dịch seed khỏi DDL vì MySQL không rollback được các lệnh tạo/thêm cấu trúc.
            connection.setAutoCommit(false);
            boolean transactionEnded = false;
            Exception seedFailure = null;
            try {
                try (var statement = connection.createStatement()) {
                    // Timeout tính riêng cho từng câu; 353 lượt mạng không dùng chung giới hạn 30 giây của executeBatch.
                    statement.setQueryTimeout(30);
                    for (String sql : seedStatements(catalog)) statement.executeUpdate(sql);
                }
                if (!inspect(connection, catalog).ready()) throw new IllegalStateException("Danh mục sau migration chưa đầy đủ; rollback phần INSERT mới.");
                connection.commit();
                transactionEnded = true;
            } catch (SQLException | RuntimeException ex) {
                seedFailure = ex;
                try { connection.rollback(); transactionEnded = true; }
                catch (SQLException rollbackFailure) { ex.addSuppressed(rollbackFailure); }
                throw ex;
            } finally {
                // Không bật autocommit nếu rollback thất bại: thao tác đó có thể commit giao dịch chưa rõ trạng thái.
                if (transactionEnded) try { connection.setAutoCommit(true); }
                catch (SQLException restoreFailure) {
                    if (seedFailure != null) seedFailure.addSuppressed(restoreFailure); else throw restoreFailure;
                }
            }
        } catch (SQLException | RuntimeException ex) {
            migrationFailure = ex; throw ex;
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('" + LOCK_NAME + "')"); }
            catch (SQLException releaseFailure) {
                if (migrationFailure != null) migrationFailure.addSuppressed(releaseFailure); else throw releaseFailure;
            }
        }
    }

    private static CheckReport inspect(Connection connection, BranchSeedData.Catalog catalog) throws SQLException {
        validateTarget(connection);
        int missingTables = 0;
        for (var table : BranchSchema.TABLES) {
            if (tableExists(connection, table.name())) validateTable(connection, table); else missingTables++;
        }
        boolean policy = policyExists(connection);
        var branches = new HashMap<String, String>();
        var hubs = new HashMap<String, String>();
        if (tableExists(connection, "branch")) try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT code, province_code, hub_area_code FROM branch")) {
            while (rows.next()) { branches.put(rows.getString("code"), rows.getString("province_code")); hubs.put(rows.getString("code"), rows.getString("hub_area_code")); }
        }
        var expectedAreas = new HashMap<String, BranchSeedData.AreaSeed>();
        catalog.areas().forEach(area -> expectedAreas.put(area.code(), area));
        var areas = new HashMap<String, String>();
        if (tableExists(connection, "province_area")) try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT a.area_code, a.province_code, b.code AS branch_code FROM province_area a JOIN branch b ON b.id = a.branch_id")) {
            while (rows.next()) {
                String code = rows.getString("area_code"), branch = rows.getString("branch_code");
                var expected = expectedAreas.get(code);
                if (expected != null && (!expected.branchCode().equals(branch) || !expected.provinceCode().equals(rows.getString("province_code"))))
                    throw incompatible("province_area", "khu vực đã có thuộc sai chi nhánh/tỉnh");
                areas.put(code, branch);
            }
        }
        int missingBranches = 0;
        for (var branch : catalog.branches()) {
            if (!branches.containsKey(branch.code())) { missingBranches++; continue; }
            if (!branch.provinceCode().equals(branches.get(branch.code()))) throw incompatible("branch", "mã chi nhánh đã có thuộc tỉnh khác");
            String hub = hubs.get(branch.code());
            String hubBranch = areas.get(hub);
            if (hubBranch == null && expectedAreas.containsKey(hub)) hubBranch = expectedAreas.get(hub).branchCode();
            if (!branch.code().equals(hubBranch)) throw incompatible("branch", "khu vực kho không thuộc chi nhánh");
        }
        var neighbors = new HashSet<BranchSeedData.NeighborSeed>();
        if (tableExists(connection, "area_neighbor")) try (var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT area_code, neighbor_code FROM area_neighbor")) {
            while (rows.next()) neighbors.add(new BranchSeedData.NeighborSeed(rows.getString("area_code"), rows.getString("neighbor_code")));
        }
        var tariffs = new HashSet<String>();
        if (tableExists(connection, "route_tariff")) try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT b.code AS origin_code, d.code AS dest_code, t.service_type FROM route_tariff t "
                        + "JOIN branch b ON b.id = t.origin_branch_id JOIN branch d ON d.id = t.dest_branch_id")) {
            while (rows.next()) tariffs.add(rows.getString("origin_code") + ":" + rows.getString("dest_code") + ":" + rows.getString("service_type"));
        }
        return new CheckReport(missingTables, policy, missingBranches,
                (int) catalog.areas().stream().filter(area -> !areas.containsKey(area.code())).count(),
                (int) catalog.neighbors().stream().filter(neighbor -> !neighbors.contains(neighbor)).count(),
                (int) catalog.tariffs().stream().filter(tariff -> !tariffs.contains(tariff.origin() + ":" + tariff.destination() + ":" + tariff.service().name())).count());
    }

    private static void validateTarget(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        if (!"MySQL".equals(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() < 8 || connection.getCatalog() == null)
            throw new IllegalArgumentException("Chỉ kiểm tra/nâng cấp MySQL 8+ đã có schema UTEExpress.");
        for (String table : BASE_TABLES) if (!tableExists(connection, table)) throw incompatible(table, "thiếu bảng nền; không tự khởi tạo database");
    }

    private static void validateTable(Connection connection, BranchSchema.Table table) throws SQLException {
        var metadata = connection.getMetaData();
        for (var column : table.columns()) if (!columnExists(connection, table.name(), column)) throw incompatible(table.name(), "thiếu cột " + column.name());
        var primary = new TreeMap<Short, String>();
        try (var rows = metadata.getPrimaryKeys(connection.getCatalog(), null, table.name())) {
            while (rows.next()) primary.put(rows.getShort("KEY_SEQ"), rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
        }
        if (!new ArrayList<>(primary.values()).equals(table.primaryKey())) throw incompatible(table.name(), "sai khóa chính");
        var indexes = new HashMap<String, TreeMap<Short, String>>();
        try (var rows = metadata.getIndexInfo(connection.getCatalog(), null, table.name(), true, false)) {
            while (rows.next()) {
                String name = rows.getString("INDEX_NAME"), column = rows.getString("COLUMN_NAME");
                if (name != null && column != null) indexes.computeIfAbsent(name, ignored -> new TreeMap<>())
                        .put(rows.getShort("ORDINAL_POSITION"), column.toLowerCase(Locale.ROOT));
            }
        }
        for (var unique : table.uniqueKeys()) if (indexes.values().stream().noneMatch(index -> new ArrayList<>(index.values()).equals(unique.columns())))
            throw incompatible(table.name(), "thiếu khóa duy nhất " + unique.name());
        var foreign = new HashMap<String, ImportedForeignKey>();
        try (var rows = metadata.getImportedKeys(connection.getCatalog(), null, table.name())) {
            while (rows.next()) {
                String name = rows.getString("FK_NAME");
                if (name == null) throw incompatible(table.name(), "khóa ngoại không có tên, không thể đối chiếu định nghĩa");
                var definition = new ImportedForeignKey(rows.getString("FKCOLUMN_NAME"), rows.getString("PKTABLE_NAME"),
                        rows.getString("PKCOLUMN_NAME"), rows.getInt("DELETE_RULE"), rows.getInt("UPDATE_RULE"));
                if (foreign.put(name.toLowerCase(Locale.ROOT), definition) != null)
                    throw incompatible(table.name(), "khóa ngoại " + name + " có nhiều cột, khác định nghĩa một cột mong muốn");
            }
        }
        for (var key : table.foreignKeys()) {
            var actual = foreign.get(key.name());
            String expected = key.column() + " -> " + key.targetTable() + "(" + key.targetColumn() + ") ON DELETE "
                    + key.deleteAction() + " ON UPDATE RESTRICT";
            if (actual == null) throw incompatible(table.name(), "thiếu khóa ngoại " + key.name() + "; mong muốn " + expected);
            boolean deleteMatches = key.cascadeDelete() ? actual.deleteRule() == DatabaseMetaData.importedKeyCascade : restrictive(actual.deleteRule());
            if (!key.column().equalsIgnoreCase(actual.column()) || !key.targetTable().equalsIgnoreCase(actual.targetTable())
                    || !key.targetColumn().equalsIgnoreCase(actual.targetColumn()) || !deleteMatches || !restrictive(actual.updateRule()))
                throw incompatible(table.name(), "khóa ngoại " + key.name() + "; mong muốn " + expected + "; thực tế " + actual.description());
        }
    }

    private record ImportedForeignKey(String column, String targetTable, String targetColumn, int deleteRule, int updateRule) {
        String description() { return column + " -> " + targetTable + "(" + targetColumn + ") ON DELETE "
                + actionName(deleteRule) + " ON UPDATE " + actionName(updateRule); }
    }
    // Trong MySQL, NO ACTION và RESTRICT đều từ chối xóa/cập nhật bản ghi cha đang được tham chiếu.
    private static boolean restrictive(int rule) { return rule == DatabaseMetaData.importedKeyRestrict || rule == DatabaseMetaData.importedKeyNoAction; }
    private static String actionName(int rule) {
        return switch (rule) {
            case DatabaseMetaData.importedKeyCascade -> "CASCADE";
            case DatabaseMetaData.importedKeyRestrict -> "RESTRICT";
            case DatabaseMetaData.importedKeyNoAction -> "NO ACTION";
            case DatabaseMetaData.importedKeySetNull -> "SET NULL";
            case DatabaseMetaData.importedKeySetDefault -> "SET DEFAULT";
            default -> "không xác định (" + rule + ")";
        };
    }

    private static boolean columnExists(Connection connection, String table, BranchSchema.Column column) throws SQLException {
        try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), null, table, column.name())) {
            while (rows.next()) {
                if (!table.equalsIgnoreCase(rows.getString("TABLE_NAME")) || !column.name().equalsIgnoreCase(rows.getString("COLUMN_NAME"))) continue;
                int type = rows.getInt("DATA_TYPE");
                boolean enumeration = column.definition().startsWith("ENUM(");
                // Connector/J 8.3 khai báo ENUM là JDBC CHAR; đường metadata khác có thể trả VARCHAR.
                boolean compatibleType = type == column.type() || column.type() == Types.BIT && type == Types.BOOLEAN
                        || enumeration && (type == Types.CHAR || type == Types.VARCHAR);
                if (!compatibleType || rows.getInt("NULLABLE") != (column.nullable() ? DatabaseMetaData.columnNullable : DatabaseMetaData.columnNoNulls)
                        || column.size() > 0 && rows.getInt("COLUMN_SIZE") != column.size()
                        || column.type() == Types.DECIMAL && rows.getInt("DECIMAL_DIGITS") != column.scale()
                        || column.generated() && !"YES".equalsIgnoreCase(rows.getString("IS_AUTOINCREMENT")))
                    throw incompatible(table, "sai kiểu/độ dài/nullability của " + column.name());
                // MySQL trả DECIMAL_DIGITS=0 cho DATETIME(6). COLUMN_SIZE là 19 + dấu chấm + số chữ số phần giây.
                if (column.type() == Types.TIMESTAMP && (!"DATETIME".equalsIgnoreCase(rows.getString("TYPE_NAME"))
                        || rows.getInt("COLUMN_SIZE") != 19 + (column.scale() == 0 ? 0 : 1 + column.scale())))
                    throw incompatible(table, "cột " + column.name() + " phải là DATETIME(" + column.scale() + ")");
                if (column.type() == Types.BIT && rows.getInt("COLUMN_SIZE") != 1)
                    throw incompatible(table, "cột " + column.name() + " phải là BIT(1)");
                if (enumeration && !"ENUM".equalsIgnoreCase(rows.getString("TYPE_NAME")))
                    throw incompatible(table, "sai kiểu enum của " + column.name());
                return true;
            }
            return false;
        }
    }

    private static boolean policyExists(Connection connection) throws SQLException {
        if (!columnExists(connection, "operations_policy", BranchSchema.POLICY_COLUMN)) return false;
        try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), null, "operations_policy", "max_active_orders")) {
            while (rows.next()) if ("operations_policy".equalsIgnoreCase(rows.getString("TABLE_NAME")) && "max_active_orders".equalsIgnoreCase(rows.getString("COLUMN_NAME"))) {
                String value = rows.getString("COLUMN_DEF");
                if (value == null || !value.replaceAll("[()'\\s]", "").equals("15")) throw incompatible("operations_policy", "max_active_orders phải DEFAULT 15");
                return true;
            }
        }
        return false;
    }

    private static boolean tableExists(Connection connection, String name) throws SQLException {
        return UpgradeAivenOrderSubmissions.tableExists(connection, name);
    }
    private static void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.setQueryTimeout(30); statement.executeUpdate(sql); }
    }
    private static IllegalStateException incompatible(String table, String detail) {
        return new IllegalStateException("Schema/danh mục không tương thích ở " + table + ": " + detail + ". Không tự sửa/xóa cấu trúc hoặc dữ liệu có sẵn.");
    }
    private static String quote(String value) { return "'" + value.replace("'", "''") + "'"; }

    static List<String> seedStatements(BranchSeedData.Catalog catalog) {
        var result = new ArrayList<String>();
        for (var branch : catalog.branches()) result.add("INSERT INTO branch (code, name, province_code, hub_address, hub_area_code, active, revision, created_at, updated_at) SELECT "
                + quote(branch.code()) + ", " + quote(branch.name()) + ", " + quote(branch.provinceCode()) + ", " + quote(branch.hubAddress()) + ", " + quote(branch.hubAreaCode())
                + ", 1, 0, NOW(6), NOW(6) FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM branch WHERE code = " + quote(branch.code()) + ")");
        for (var area : catalog.areas()) result.add("INSERT INTO province_area (province_code, area_code, area_name, branch_id, lat, lng) SELECT "
                + quote(area.provinceCode()) + ", " + quote(area.code()) + ", " + quote(area.name()) + ", b.id, " + area.lat().toPlainString() + ", " + area.lng().toPlainString()
                + " FROM branch b WHERE b.code = " + quote(area.branchCode()) + " AND NOT EXISTS (SELECT 1 FROM province_area WHERE area_code = " + quote(area.code()) + ")");
        for (var neighbor : catalog.neighbors()) result.add("INSERT INTO area_neighbor (area_code, neighbor_code) SELECT " + quote(neighbor.areaCode()) + ", " + quote(neighbor.neighborCode())
                + " FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM area_neighbor WHERE area_code = " + quote(neighbor.areaCode()) + " AND neighbor_code = " + quote(neighbor.neighborCode()) + ")");
        for (var tariff : catalog.tariffs()) result.add("INSERT INTO route_tariff (origin_branch_id, dest_branch_id, service_type, surcharge, lead_time_text, revision) SELECT b.id, d.id, "
                + quote(tariff.service().name()) + ", " + tariff.surcharge().toPlainString() + ", " + quote(tariff.leadTime())
                + ", 0 FROM branch b CROSS JOIN branch d WHERE b.code = " + quote(tariff.origin()) + " AND d.code = " + quote(tariff.destination())
                + " AND NOT EXISTS (SELECT 1 FROM route_tariff t WHERE t.origin_branch_id = b.id AND t.dest_branch_id = d.id AND t.service_type = " + quote(tariff.service().name()) + ")");
        return List.copyOf(result);
    }
}
