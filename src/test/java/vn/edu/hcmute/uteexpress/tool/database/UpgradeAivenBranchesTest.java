package vn.edu.hcmute.uteexpress.tool.database;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Chỉ dùng JDBC giả lập để kiểm tra đường ghi; không chạy --apply lên Aiven. */
class UpgradeAivenBranchesTest {
    @Test void printSchemaDoesNotOpenAConnectionAndOnlyTargetsNewTablesAndThePolicyColumn() throws Exception {
        var bytes = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (var settings = mockStatic(AivenConnectionSettings.class); var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            try { System.setOut(output); UpgradeAivenBranches.main(new String[]{"--print-schema"}); }
            finally { System.setOut(previous); }
            settings.verifyNoInteractions();
        }
        String sql = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(11, sql.lines().filter(line -> line.startsWith("CREATE TABLE IF NOT EXISTS ")).count());
        assertTrue(sql.contains("ALTER TABLE operations_policy ADD COLUMN max_active_orders INT NOT NULL DEFAULT 15;"));
        assertFalse(sql.lines().anyMatch(line -> line.matches("(?i)^\\s*(DROP|DELETE|TRUNCATE|UPDATE)\\b.*")));
        var cascadingKeys = BranchSchema.TABLES.stream().flatMap(t -> t.foreignKeys().stream())
                .filter(BranchSchema.ForeignKey::cascadeDelete).map(BranchSchema.ForeignKey::name).collect(java.util.stream.Collectors.toSet());
        assertEquals(Set.of("fk_saved_area_address", "fk_draft_area_order", "fk_staff_branch_user", "fk_zone_shipper"), cascadingKeys);
        assertEquals(4, sql.lines().filter(line -> line.contains("ON DELETE CASCADE")).count());
        assertEquals(24, sql.lines().filter(line -> line.contains("ON DELETE RESTRICT")).count());
        assertEquals(4, AivenSchema.statements().stream().filter(line -> line.toLowerCase(Locale.ROOT).contains("on delete cascade")).count());
        assertFalse(sql.contains("INSERT INTO app_user")); assertFalse(sql.contains("INSERT INTO shipment_order"));
        assertFalse(sql.contains("ALTER TABLE app_user")); assertFalse(sql.contains("ALTER TABLE shipment_order"));
        assertFalse(sql.contains("district"));
        assertEquals(353, sql.lines().filter(line -> line.startsWith("INSERT INTO ")).count());
    }

    @Test void checkOnlyReadsAndReportsEveryMissingObjectWithoutAcquiringALock() throws Exception {
        var database = new DatabaseFixture();
        var report = UpgradeAivenBranches.check(database.connection);
        assertFalse(report.ready()); assertEquals(11, report.missingTables()); assertFalse(report.policyReady());
        assertEquals(3, report.missingBranches()); assertEquals(59, report.missingAreas());
        assertEquals(270, report.missingNeighbors()); assertEquals(21, report.missingTariffs());
        verify(database.connection).setReadOnly(true);
        verify(database.statement, never()).executeUpdate(anyString());
        verify(database.statement, never()).executeBatch(); verify(database.connection, never()).commit();
        verify(database.statement, never()).executeQuery(contains("GET_LOCK"));
    }

    @Test void upgradeIsAdditiveAndSecondRunDoesNotWriteAgain() throws Exception {
        var database = new DatabaseFixture();
        UpgradeAivenBranches.upgrade(database.connection);
        UpgradeAivenBranches.upgrade(database.connection);
        verify(database.statement, times(11)).executeUpdate(startsWith("CREATE TABLE IF NOT EXISTS "));
        verify(database.statement).executeUpdate(BranchSchema.POLICY_DDL);
        verify(database.statement, times(353)).executeUpdate(startsWith("INSERT INTO "));
        verify(database.statement, never()).executeBatch(); verify(database.connection, times(1)).commit();
        verify(database.statement, times(2)).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
        verify(database.statement, never()).executeUpdate(matches("(?is)^\\s*(DROP|DELETE|TRUNCATE|UPDATE|ALTER TABLE (app_user|shipment_order))\\b.*"));
        assertTrue(UpgradeAivenBranches.check(database.connection).ready());
    }

    @Test void existingPolicyColumnIsSkippedEvenWhenAllNewTablesAreMissing() throws Exception {
        var database = new DatabaseFixture(); database.policy = true;
        UpgradeAivenBranches.upgrade(database.connection);
        UpgradeAivenBranches.upgrade(database.connection);
        verify(database.statement, never()).executeUpdate(BranchSchema.POLICY_DDL);
        verify(database.statement, times(11)).executeUpdate(startsWith("CREATE TABLE IF NOT EXISTS "));
        verify(database.statement, times(353)).executeUpdate(startsWith("INSERT INTO "));
        verify(database.statement, never()).executeBatch();
        assertTrue(UpgradeAivenBranches.check(database.connection).ready());
    }

    @Test void wrongEngineAndMissingBaseTableFailBeforeAnyWriteOrLock() throws Exception {
        var database = new DatabaseFixture();
        when(database.metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThrows(IllegalArgumentException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        when(database.metadata.getDatabaseProductName()).thenReturn("MySQL"); database.baseTables.remove("shipment_order");
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(anyString());
        verify(database.statement, never()).executeQuery(contains("GET_LOCK"));
    }

    @Test void lockTimeoutDoesNotChangeTheSchema() throws Exception {
        var database = new DatabaseFixture(); database.lockAvailable = false;
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(anyString()); verify(database.statement, never()).executeBatch();
        verify(database.statement, never()).execute(contains("RELEASE_LOCK"));
    }

    @Test void incompatibleColumnAndMissingUniqueKeyAreNeverRepairedAutomatically() throws Exception {
        var database = new DatabaseFixture(); database.existingTables.add("branch"); database.wrongColumn = true;
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        database.wrongColumn = false; database.missingUnique = true;
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(anyString());
        verify(database.statement, never()).executeQuery(contains("GET_LOCK"));
    }

    @Test void missingForeignKeyAndCascadingForeignKeyFailClosed() throws Exception {
        var database = new DatabaseFixture(); database.existingTables.add("province_area"); database.missingForeign = true;
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        database.missingForeign = false; database.cascadeForeign = true;
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"fk_saved_area_address", "fk_draft_area_order", "fk_staff_branch_user", "fk_zone_shipper"})
    void checkReportsExpectedCascadeAndActualRestrictForEachAuxiliaryForeignKey(String keyName) throws Exception {
        var database = new DatabaseFixture(); database.complete(); database.wrongDeleteForeignKey = keyName;
        var error = assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        assertTrue(error.getMessage().contains(keyName));
        assertTrue(error.getMessage().contains("mong muốn"));
        assertTrue(error.getMessage().contains("ON DELETE CASCADE"));
        assertTrue(error.getMessage().contains("thực tế"));
        assertTrue(error.getMessage().contains("ON DELETE RESTRICT"));
        verify(database.statement, never()).executeUpdate(anyString());
        verify(database.statement, never()).executeBatch();
        verify(database.statement, never()).executeQuery(contains("GET_LOCK"));
    }

    @Test void checkRejectsCascadingHistoricalForeignKeyAndCascadingUpdatesWithClearDiagnostics() throws Exception {
        var database = new DatabaseFixture(); database.complete(); database.wrongDeleteForeignKey = "fk_route_order";
        var error = assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        assertTrue(error.getMessage().contains("fk_route_order"));
        assertTrue(error.getMessage().contains("ON DELETE RESTRICT")); assertTrue(error.getMessage().contains("ON DELETE CASCADE"));
        database.wrongDeleteForeignKey = null; database.wrongUpdateForeignKey = "fk_zone_shipper";
        error = assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        assertTrue(error.getMessage().contains("fk_zone_shipper"));
        assertTrue(error.getMessage().contains("ON UPDATE RESTRICT")); assertTrue(error.getMessage().contains("ON UPDATE CASCADE"));
        verify(database.statement, never()).executeUpdate(anyString());
    }

    @Test void wrongPolicyDefaultIsRejectedWithoutUpdatingExistingValues() throws Exception {
        var database = new DatabaseFixture(); database.policy = true; database.policyDefault = "10";
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(anyString()); verify(database.statement, never()).executeBatch();
    }

    @Test void aSeedFailureRollsBackOnlyTheSeedAndAlwaysReleasesTheLock() throws Exception {
        var database = new DatabaseFixture();
        doThrow(new SQLException("Lỗi giả lập")).when(database.statement).executeUpdate(startsWith("INSERT INTO province_area "));
        assertThrows(SQLException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        verify(database.connection).rollback(); verify(database.connection, never()).commit();
        assertEquals(3, database.executedSeeds.size()); assertFalse(database.seeded);
        verify(database.statement).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
        assertEquals(11, database.existingTables.size());
        assertTrue(database.autoCommit);
    }

    @Test void readySchemaCanBeCheckedWithoutWritesAndConflictingBranchIdentityIsRejected() throws Exception {
        var database = new DatabaseFixture(); database.complete();
        assertTrue(UpgradeAivenBranches.check(database.connection).ready());
        database.wrongProvince = true;
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        verify(database.statement, never()).executeUpdate(anyString()); verify(database.statement, never()).executeBatch();
    }

    @Test void partialDdlFailureReleasesLockWithoutPretendingThatDdlWasRolledBack() throws Exception {
        var database = new DatabaseFixture();
        doThrow(new SQLException("Lỗi giả lập")).when(database.statement).executeUpdate(startsWith("CREATE TABLE IF NOT EXISTS province_area"));
        assertThrows(SQLException.class, () -> UpgradeAivenBranches.upgrade(database.connection));
        assertEquals(Set.of("branch"), database.existingTables);
        verify(database.statement).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
        verify(database.connection, never()).rollback(); verify(database.connection, never()).commit();
    }

    @Test void mysqlDatetimeMetadataWithZeroDecimalDigitsIsValidAndEmptyTablesOnlyNeedSeeds() throws Exception {
        var database = new DatabaseFixture(); database.schemaOnly();
        var timestamp = database.columnRow("branch", database.table("branch").columns().stream()
                .filter(column -> column.name().equals("created_at")).findFirst().orElseThrow());
        assertEquals(93, timestamp.get("DATA_TYPE")); assertEquals("DATETIME", timestamp.get("TYPE_NAME"));
        assertEquals(26, timestamp.get("COLUMN_SIZE")); assertEquals(0, timestamp.get("DECIMAL_DIGITS"));
        var report = UpgradeAivenBranches.check(database.connection);
        assertEquals(0, report.missingTables()); assertTrue(report.policyReady()); assertFalse(report.ready());
        assertEquals(3, report.missingBranches()); assertEquals(59, report.missingAreas());
        assertEquals(270, report.missingNeighbors()); assertEquals(21, report.missingTariffs());
        verify(database.statement, never()).executeUpdate(anyString());
    }

    @Test void existingEmptySchemaIsSeededStatementByStatementWithinOneTransactionAndCanBeRetried() throws Exception {
        var database = new DatabaseFixture(); database.schemaOnly();
        UpgradeAivenBranches.upgrade(database.connection);
        UpgradeAivenBranches.upgrade(database.connection);
        assertEquals(UpgradeAivenBranches.seedStatements(BranchSeedData.load()), database.executedSeeds);
        verify(database.statement, never()).executeUpdate(startsWith("CREATE TABLE"));
        verify(database.statement, never()).executeUpdate(startsWith("ALTER TABLE"));
        verify(database.statement, never()).addBatch(anyString()); verify(database.statement, never()).executeBatch();
        var ordered = inOrder(database.connection, database.statement);
        ordered.verify(database.connection).setAutoCommit(false);
        ordered.verify(database.statement).setQueryTimeout(30);
        ordered.verify(database.statement, times(353)).executeUpdate(startsWith("INSERT INTO "));
        ordered.verify(database.connection).commit();
        ordered.verify(database.connection).setAutoCommit(true);
        verify(database.connection, times(1)).commit();
        assertTrue(UpgradeAivenBranches.check(database.connection).ready());
    }

    @Test void datetimePrecisionAndNativeTypeRemainStrictWithoutUsingDecimalDigits() throws Exception {
        var database = new DatabaseFixture(); database.schemaOnly();
        database.columnOverrides.put("branch.created_at", Map.of("COLUMN_SIZE", 23, "DECIMAL_DIGITS", 6));
        var error = assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        assertTrue(error.getMessage().contains("DATETIME(6)"));
        database.columnOverrides.put("branch.created_at", Map.of("TYPE_NAME", "TIMESTAMP"));
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        verify(database.statement, never()).executeUpdate(anyString());
    }

    @Test void decimalPrecisionAndScaleBitWidthAndNativeEnumRemainStrictWithMysqlMetadata() throws Exception {
        var database = new DatabaseFixture(); database.schemaOnly();
        assertEquals(Types.CHAR, database.columnRow("order_route", database.table("order_route").columns().stream()
                .filter(column -> column.name().equals("stage")).findFirst().orElseThrow()).get("DATA_TYPE"));
        assertEquals(0, UpgradeAivenBranches.check(database.connection).missingTables());
        for (var override : List.of(Map.of("COLUMN_SIZE", 8), Map.of("DECIMAL_DIGITS", 5))) {
            database.columnOverrides.put("province_area.lat", new HashMap<>(override));
            assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        }
        database.columnOverrides.clear(); database.columnOverrides.put("branch.active", Map.of("COLUMN_SIZE", 8));
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        database.columnOverrides.clear(); database.columnOverrides.put("order_route.stage", Map.of("TYPE_NAME", "CHAR"));
        assertThrows(IllegalStateException.class, () -> UpgradeAivenBranches.check(database.connection));
        verify(database.statement, never()).executeUpdate(anyString());
    }

    @Test void rollbackAndLockReleaseFailuresDoNotHideTheOriginalSeedErrorOrEnableAutoCommit() throws Exception {
        var database = new DatabaseFixture(); database.schemaOnly();
        var seedError = new SQLTimeoutException("Statement cancelled due to timeout or client request", null, 0);
        doThrow(seedError).when(database.statement).executeUpdate(startsWith("INSERT INTO province_area "));
        doThrow(new SQLException("Rollback failed", "08006", 10)).when(database.connection).rollback();
        doThrow(new SQLException("Lock release failed", "08006", 11)).when(database.statement).execute(contains("RELEASE_LOCK"));
        assertSame(seedError, assertThrows(SQLException.class, () -> UpgradeAivenBranches.upgrade(database.connection)));
        assertEquals(2, seedError.getSuppressed().length);
        verify(database.connection, never()).commit(); verify(database.connection, never()).setAutoCommit(true);
        assertFalse(database.autoCommit);
    }

    @Test void diagnosticsIncludeClassSqlStateCodeAndMessageEvenWhenSqlStateIsNull() throws Exception {
        var timeout = new SQLTimeoutException("Statement cancelled due to timeout or client request", null, 0);
        timeout.setNextException(new SQLException("Duplicate entry 'SGN' for key 'uk_branch_code'", "23000", 1062));
        var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) { UpgradeAivenBranches.printFailure(timeout, output); }
        String diagnostic = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(diagnostic.contains("java.sql.SQLTimeoutException"));
        assertTrue(diagnostic.contains("SQLState=null; errorCode=0"));
        assertTrue(diagnostic.contains(timeout.getMessage())); assertTrue(diagnostic.contains("SQLState=23000; errorCode=1062"));
        assertTrue(diagnostic.contains("Duplicate entry 'SGN'"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Access denied for user 'private-user'@'private-host' (using password: YES)",
            "Failed jdbc:mysql://private-host:1234/db?user=private-user&password=secret", "Could not resolve private-host.aivencloud.com"})
    void diagnosticsNeverPrintConnectionDetailsEvenWithoutSqlState(String message) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            UpgradeAivenBranches.printFailure(new SQLException(message, null, 1045), output);
        }
        String diagnostic = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(diagnostic.contains("errorCode=1045")); assertTrue(diagnostic.contains("Đã ẩn"));
        assertFalse(diagnostic.contains("private-user")); assertFalse(diagnostic.contains("private-host"));
        assertFalse(diagnostic.contains("jdbc:mysql")); assertFalse(diagnostic.contains("secret"));
    }

    private static final class DatabaseFixture {
        final Connection connection = mock(Connection.class);
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        final Statement statement = mock(Statement.class);
        final Set<String> baseTables = new HashSet<>(Set.of("app_user", "shipment_order", "saved_address", "draft_order", "operations_policy"));
        final Set<String> existingTables = new HashSet<>();
        final List<String> executedSeeds = new ArrayList<>();
        final Map<String, Map<String, Object>> columnOverrides = new HashMap<>();
        boolean autoCommit = true, readOnly, policy, seeded, lockAvailable = true, wrongColumn, missingUnique, missingForeign, cascadeForeign, wrongProvince;
        String policyDefault = "15";
        String wrongDeleteForeignKey, wrongUpdateForeignKey;

        DatabaseFixture() throws Exception {
            when(connection.getMetaData()).thenReturn(metadata); when(connection.getCatalog()).thenReturn("test_only");
            when(connection.createStatement()).thenReturn(statement);
            when(connection.getAutoCommit()).thenAnswer(call -> autoCommit); when(connection.isReadOnly()).thenAnswer(call -> readOnly);
            doAnswer(call -> { autoCommit = call.getArgument(0); return null; }).when(connection).setAutoCommit(anyBoolean());
            doAnswer(call -> { readOnly = call.getArgument(0); return null; }).when(connection).setReadOnly(anyBoolean());
            when(metadata.getDatabaseProductName()).thenReturn("MySQL"); when(metadata.getDatabaseMajorVersion()).thenReturn(8);
            when(metadata.getTables(anyString(), isNull(), anyString(), any())).thenAnswer(call -> {
                String name = call.getArgument(2);
                return rows(baseTables.contains(name) || existingTables.contains(name) ? List.of(Map.of("TABLE_NAME", name)) : List.of());
            });
            when(metadata.getColumns(anyString(), isNull(), anyString(), anyString())).thenAnswer(call -> {
                String table = call.getArgument(2), column = call.getArgument(3);
                if (table.equals("operations_policy")) return rows(policy ? List.of(columnRow(table, BranchSchema.POLICY_COLUMN)) : List.of());
                var spec = table(table);
                return rows(spec.columns().stream().filter(c -> c.name().equals(column)).map(c -> columnRow(table, c)).toList());
            });
            when(metadata.getPrimaryKeys(anyString(), isNull(), anyString())).thenAnswer(call -> {
                var keys = table(call.getArgument(2)).primaryKey(); var data = new ArrayList<Map<String, Object>>();
                for (int i = 0; i < keys.size(); i++) data.add(Map.of("COLUMN_NAME", keys.get(i), "KEY_SEQ", i + 1));
                return rows(data);
            });
            when(metadata.getIndexInfo(anyString(), isNull(), anyString(), eq(true), eq(false))).thenAnswer(call -> {
                var data = new ArrayList<Map<String, Object>>();
                if (!missingUnique) for (var key : table(call.getArgument(2)).uniqueKeys())
                    for (int i = 0; i < key.columns().size(); i++) data.add(Map.of("INDEX_NAME", key.name(), "COLUMN_NAME", key.columns().get(i), "ORDINAL_POSITION", i + 1));
                return rows(data);
            });
            when(metadata.getImportedKeys(anyString(), isNull(), anyString())).thenAnswer(call -> {
                var data = new ArrayList<Map<String, Object>>();
                if (!missingForeign) for (var key : table(call.getArgument(2)).foreignKeys()) data.add(Map.of(
                        "FK_NAME", key.name(), "FKCOLUMN_NAME", key.column(), "PKTABLE_NAME", key.targetTable(), "PKCOLUMN_NAME", key.targetColumn(),
                        "DELETE_RULE", cascadeForeign || (key.cascadeDelete() != key.name().equals(wrongDeleteForeignKey))
                                ? DatabaseMetaData.importedKeyCascade : DatabaseMetaData.importedKeyRestrict,
                        "UPDATE_RULE", key.name().equals(wrongUpdateForeignKey) ? DatabaseMetaData.importedKeyCascade : DatabaseMetaData.importedKeyRestrict));
                return rows(data);
            });
            when(statement.executeQuery(anyString())).thenAnswer(call -> query(call.getArgument(0)));
            when(statement.executeUpdate(anyString())).thenAnswer(call -> {
                String sql = call.getArgument(0);
                if (sql.startsWith("CREATE TABLE IF NOT EXISTS ")) existingTables.add(sql.split("\\s+")[5]);
                if (sql.equals(BranchSchema.POLICY_DDL)) policy = true;
                if (sql.startsWith("INSERT INTO ")) {
                    assertFalse(autoCommit, "Seed phải nằm trong giao dịch riêng");
                    executedSeeds.add(sql); seeded = executedSeeds.size() == 353;
                }
                return 0;
            });
            doAnswer(call -> { seeded = false; return null; }).when(connection).rollback();
        }

        void schemaOnly() { BranchSchema.TABLES.forEach(table -> existingTables.add(table.name())); policy = true; }
        void complete() { schemaOnly(); seeded = true; }
        BranchSchema.Table table(String name) { return BranchSchema.TABLES.stream().filter(table -> table.name().equals(name)).findFirst().orElseThrow(); }
        Map<String, Object> columnRow(String table, BranchSchema.Column column) {
            var row = new HashMap<String, Object>();
            row.put("TABLE_NAME", table); row.put("COLUMN_NAME", column.name()); row.put("DATA_TYPE", column.type());
            row.put("COLUMN_SIZE", wrongColumn && table.equals("branch") && column.name().equals("code") ? 1 : column.size());
            row.put("DECIMAL_DIGITS", column.scale()); row.put("NULLABLE", column.nullable() ? 1 : 0);
            row.put("IS_AUTOINCREMENT", column.generated() ? "YES" : "NO"); row.put("COLUMN_DEF", policyDefault);
            row.put("TYPE_NAME", column.definition().startsWith("ENUM(") ? "ENUM" : "VARCHAR");
            // Giá trị quan sát trên MySQL 8.4 / Connector-J 8.3, độc lập với kỳ vọng scale của descriptor.
            if (column.type() == Types.TIMESTAMP) {
                row.put("TYPE_NAME", "DATETIME"); row.put("COLUMN_SIZE", 26); row.put("DECIMAL_DIGITS", 0);
            }
            if (column.type() == Types.BIT) { row.put("TYPE_NAME", "BIT"); row.put("COLUMN_SIZE", 1); }
            if (column.type() == Types.DECIMAL) row.put("TYPE_NAME", "DECIMAL");
            if (column.definition().startsWith("ENUM(")) row.put("DATA_TYPE", Types.CHAR);
            row.putAll(columnOverrides.getOrDefault(table + "." + column.name(), Map.of()));
            return row;
        }
        ResultSet query(String sql) {
            if (sql.startsWith("SELECT GET_LOCK")) return rows(List.of(Map.of("1", lockAvailable ? 1 : 0)));
            if (!seeded) return rows(List.of());
            var catalog = BranchSeedData.load(); var data = new ArrayList<Map<String, Object>>();
            if (sql.startsWith("SELECT code,")) catalog.branches().forEach(b -> data.add(Map.of(
                    "code", b.code(), "province_code", wrongProvince && b.code().equals("SGN") ? "01" : b.provinceCode(), "hub_area_code", b.hubAreaCode())));
            else if (sql.startsWith("SELECT a.area_code")) catalog.areas().forEach(a -> data.add(Map.of("area_code", a.code(), "province_code", a.provinceCode(), "branch_code", a.branchCode())));
            else if (sql.startsWith("SELECT area_code,")) catalog.neighbors().forEach(n -> data.add(Map.of("area_code", n.areaCode(), "neighbor_code", n.neighborCode())));
            else if (sql.startsWith("SELECT b.code")) catalog.tariffs().forEach(t -> data.add(Map.of("origin_code", t.origin(), "dest_code", t.destination(), "service_type", t.service().name())));
            else throw new AssertionError("SQL không được mong đợi: " + sql);
            return rows(data);
        }
    }

    private static ResultSet rows(List<? extends Map<String, ?>> data) {
        var result = mock(ResultSet.class); var cursor = new AtomicInteger(-1);
        try {
            when(result.next()).thenAnswer(call -> cursor.incrementAndGet() < data.size());
            when(result.getString(anyString())).thenAnswer(call -> { Object value = data.get(cursor.get()).get(call.getArgument(0)); return value == null ? null : value.toString(); });
            when(result.getInt(anyString())).thenAnswer(call -> ((Number) data.get(cursor.get()).getOrDefault(call.getArgument(0), null)).intValue());
            when(result.getInt(anyInt())).thenAnswer(call -> ((Number) data.get(cursor.get()).get(Integer.toString(call.getArgument(0)))).intValue());
            when(result.getShort(anyString())).thenAnswer(call -> ((Number) data.get(cursor.get()).get(call.getArgument(0))).shortValue());
        } catch (SQLException ex) { throw new AssertionError(ex); }
        return result;
    }
}
