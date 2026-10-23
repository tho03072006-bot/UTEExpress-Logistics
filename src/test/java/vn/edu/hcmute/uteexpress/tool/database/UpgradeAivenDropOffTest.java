package vn.edu.hcmute.uteexpress.tool.database;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Chỉ dùng JDBC giả lập để kiểm tra đường ghi; không chạy --apply lên Aiven. */
class UpgradeAivenDropOffTest {
    @Test void printSchemaDoesNotConnectAndOnlyAddsTwoDefaultedColumns() throws Exception {
        var bytes = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (var settings = mockStatic(AivenConnectionSettings.class); var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            try { System.setOut(output); UpgradeAivenDropOff.main(new String[]{"--print-schema"}); }
            finally { System.setOut(previous); }
            settings.verifyNoInteractions();
        }
        String sql = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(sql.contains("ALTER TABLE order_route ADD COLUMN pickup_mode ENUM('COURIER','DROP_OFF') NOT NULL DEFAULT 'COURIER';"));
        assertTrue(sql.contains("ALTER TABLE operations_policy ADD COLUMN drop_off_discount DECIMAL(12,0) NOT NULL DEFAULT 3000;"));
        assertEquals(2, sql.lines().filter(line -> line.startsWith("ALTER TABLE ")).count());
        assertFalse(sql.lines().anyMatch(line -> line.matches("(?i)^\\s*(DROP|DELETE|TRUNCATE|UPDATE|INSERT)\\b.*")));
    }

    @Test void checkOnlyReadsAndCountsMissingColumns() throws Exception {
        var database = new Fixture();
        var out = new ByteArrayOutputStream();
        assertEquals(2, UpgradeAivenDropOff.check(database.connection, new PrintStream(out, true, StandardCharsets.UTF_8)));
        verify(database.connection).setReadOnly(true);
        verify(database.statement, never()).executeUpdate(anyString());
        verify(database.statement, never()).executeQuery(contains("GET_LOCK"));
        assertTrue(out.toString(StandardCharsets.UTF_8).contains("order_route.pickup_mode: còn thiếu"));
    }

    @Test void upgradeAddsBothColumnsOnceAndSecondRunWritesNothing() throws Exception {
        var database = new Fixture();
        UpgradeAivenDropOff.upgrade(database.connection);
        UpgradeAivenDropOff.upgrade(database.connection);
        for (var change : UpgradeAivenDropOff.CHANGES) verify(database.statement, times(1)).executeUpdate(change.ddl());
        verify(database.statement, times(2)).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
        assertEquals(0, UpgradeAivenDropOff.check(database.connection, new PrintStream(OutputStream.nullOutputStream())));
    }

    @Test void existingColumnWithWrongDefaultIsNeverRepaired() throws Exception {
        var database = new Fixture();
        database.columns.put("operations_policy.drop_off_discount", new String[]{"decimal(12,0)", "NO", "5000"});
        assertThrows(IllegalStateException.class, () -> UpgradeAivenDropOff.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(UpgradeAivenDropOff.CHANGES.get(1).ddl());
        database.columns.put("operations_policy.drop_off_discount", new String[]{"decimal(12,0)", "YES", "3000"});
        assertThrows(IllegalStateException.class, () -> UpgradeAivenDropOff.check(database.connection, new PrintStream(OutputStream.nullOutputStream())));
    }

    @Test void mysqlQuotedEnumDefaultIsAccepted() {
        var change = UpgradeAivenDropOff.CHANGES.get(0);
        assertDoesNotThrow(() -> UpgradeAivenDropOff.verify(change, new UpgradeAivenDropOff.ActualColumn("enum('COURIER','DROP_OFF')", false, "COURIER")));
        assertDoesNotThrow(() -> UpgradeAivenDropOff.verify(change, new UpgradeAivenDropOff.ActualColumn("enum('COURIER','DROP_OFF')", false, "'COURIER'")));
        assertThrows(IllegalStateException.class, () -> UpgradeAivenDropOff.verify(change,
                new UpgradeAivenDropOff.ActualColumn("varchar(20)", false, "COURIER")));
    }

    @Test void missingRouteTableOrWrongEngineFailsBeforeAnyLockOrWrite() throws Exception {
        var database = new Fixture(); database.tables.remove("order_route");
        assertThrows(IllegalStateException.class, () -> UpgradeAivenDropOff.upgrade(database.connection));
        database.tables.add("order_route"); when(database.metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThrows(IllegalArgumentException.class, () -> UpgradeAivenDropOff.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(anyString());
        verify(database.statement, never()).executeQuery(contains("GET_LOCK"));
    }

    @Test void lockTimeoutDoesNotChangeTheSchema() throws Exception {
        var database = new Fixture(); database.lockAvailable = false;
        assertThrows(IllegalStateException.class, () -> UpgradeAivenDropOff.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(anyString());
        verify(database.statement, never()).execute(contains("RELEASE_LOCK"));
    }

    /** CSDL giả: các bảng có sẵn và cột information_schema theo "bảng.cột" → {column_type, is_nullable, column_default}. */
    static final class Fixture {
        final Connection connection = mock(Connection.class);
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        final Statement statement = mock(Statement.class);
        final Set<String> tables = new HashSet<>(Set.of("order_route", "operations_policy"));
        final Map<String, String[]> columns = new HashMap<>();
        boolean lockAvailable = true;

        Fixture() throws Exception {
            when(connection.getMetaData()).thenReturn(metadata);
            when(connection.getAutoCommit()).thenReturn(true);
            when(metadata.getDatabaseProductName()).thenReturn("MySQL");
            when(metadata.getDatabaseMajorVersion()).thenReturn(8);
            when(metadata.getTables(any(), any(), anyString(), any())).thenAnswer(call -> rows(tables.contains(call.<String>getArgument(2))
                    ? List.of(Map.of("TABLE_NAME", call.getArgument(2))) : List.of()));
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(startsWith("SELECT GET_LOCK"))).thenAnswer(call -> {
                var result = mock(ResultSet.class);
                when(result.next()).thenReturn(true);
                when(result.getInt(1)).thenReturn(lockAvailable ? 1 : 0);
                return result;
            });
            when(statement.executeUpdate(anyString())).thenAnswer(call -> {
                String ddl = call.getArgument(0);
                for (var change : UpgradeAivenDropOff.CHANGES)
                    if (change.ddl().equals(ddl)) columns.put(change.table() + "." + change.column(), new String[]{change.columnType(), "NO", change.defaultValue()});
                return 0;
            });
            when(connection.prepareStatement(contains("information_schema.columns"))).thenAnswer(call -> {
                var prepared = mock(PreparedStatement.class);
                var params = new String[2];
                doAnswer(set -> { params[set.<Integer>getArgument(0) - 1] = set.getArgument(1); return null; })
                        .when(prepared).setString(anyInt(), anyString());
                when(prepared.executeQuery()).thenAnswer(query -> {
                    var found = columns.get(params[0] + "." + params[1]);
                    var result = mock(ResultSet.class);
                    when(result.next()).thenReturn(found != null);
                    if (found != null) {
                        when(result.getString(1)).thenReturn(found[0]);
                        when(result.getString(2)).thenReturn(found[1]);
                        when(result.getString(3)).thenReturn(found[2]);
                    }
                    return result;
                });
                return prepared;
            });
        }

        private static ResultSet rows(List<Map<String, String>> values) throws SQLException {
            var result = mock(ResultSet.class);
            var index = new int[]{-1};
            when(result.next()).thenAnswer(call -> ++index[0] < values.size());
            when(result.getString(anyString())).thenAnswer(call -> values.get(index[0]).get(call.<String>getArgument(0)));
            return result;
        }
    }
}
