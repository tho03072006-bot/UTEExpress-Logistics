package vn.edu.hcmute.uteexpress.tool.database;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** B9: chỉ dùng JDBC giả để kiểm tra đường ghi; không chạy --apply lên Aiven. */
class UpgradeAivenReturnLegTest {
    @Test void printSchemaDoesNotConnectAndOnlyAddsOneDefaultedColumn() throws Exception {
        var bytes = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (var settings = mockStatic(AivenConnectionSettings.class); var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            try { System.setOut(output); UpgradeAivenReturnLeg.main(new String[]{"--print-schema"}); }
            finally { System.setOut(previous); }
            settings.verifyNoInteractions();
        }
        String sql = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(sql.contains("ALTER TABLE order_route ADD COLUMN return_leg BIT NOT NULL DEFAULT 0;"));
        assertEquals(1, sql.lines().filter(line -> line.startsWith("ALTER TABLE ")).count());
        assertFalse(sql.lines().anyMatch(line -> line.matches("(?i)^\\s*(DROP|DELETE|TRUNCATE|UPDATE|INSERT)\\b.*")));
    }

    @Test void checkIsReadOnlyAndApplyAddsColumnOnceThenSecondRunWritesNothing() throws Exception {
        var database = new Fixture();
        assertEquals(1, UpgradeAivenReturnLeg.check(database.connection, new PrintStream(OutputStream.nullOutputStream())));
        verify(database.connection).setReadOnly(true);
        verify(database.statement, never()).executeUpdate(anyString());
        UpgradeAivenReturnLeg.upgrade(database.connection);
        UpgradeAivenReturnLeg.upgrade(database.connection);
        verify(database.statement, times(1)).executeUpdate(UpgradeAivenReturnLeg.DDL);
        verify(database.statement, times(2)).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
        assertEquals(0, UpgradeAivenReturnLeg.check(database.connection, new PrintStream(OutputStream.nullOutputStream())));
    }

    @Test void mysqlBitDefaultIsAcceptedButWrongDefinitionIsNeverRepaired() throws Exception {
        assertDoesNotThrow(() -> UpgradeAivenReturnLeg.verify(new UpgradeAivenReturnLeg.ActualColumn("bit(1)", false, "b'0'")));
        assertDoesNotThrow(() -> UpgradeAivenReturnLeg.verify(new UpgradeAivenReturnLeg.ActualColumn("bit(1)", false, "0")));
        assertThrows(IllegalStateException.class, () -> UpgradeAivenReturnLeg.verify(new UpgradeAivenReturnLeg.ActualColumn("bit(1)", false, "b'1'")));
        assertThrows(IllegalStateException.class, () -> UpgradeAivenReturnLeg.verify(new UpgradeAivenReturnLeg.ActualColumn("tinyint(1)", false, "0")));
        var database = new Fixture();
        database.columns.put("order_route.return_leg", new String[]{"bit(1)", "YES", "b'0'"});
        assertThrows(IllegalStateException.class, () -> UpgradeAivenReturnLeg.upgrade(database.connection));
        verify(database.statement, never()).executeUpdate(anyString());
    }

    @Test void wrongEngineMissingTableOrBusyLockWritesNothing() throws Exception {
        var database = new Fixture(); database.tables.remove("order_route");
        assertThrows(IllegalStateException.class, () -> UpgradeAivenReturnLeg.upgrade(database.connection));
        database.tables.add("order_route"); when(database.metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThrows(IllegalArgumentException.class, () -> UpgradeAivenReturnLeg.upgrade(database.connection));
        var busy = new Fixture(); busy.lockAvailable = false;
        assertThrows(IllegalStateException.class, () -> UpgradeAivenReturnLeg.upgrade(busy.connection));
        verify(database.statement, never()).executeUpdate(anyString());
        verify(busy.statement, never()).executeUpdate(anyString());
    }

    /** CSDL giả: các bảng có sẵn và cột information_schema theo "bảng.cột" → {column_type, is_nullable, column_default}. */
    static final class Fixture {
        final Connection connection = mock(Connection.class);
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        final Statement statement = mock(Statement.class);
        final Set<String> tables = new HashSet<>(Set.of("order_route"));
        final Map<String, String[]> columns = new HashMap<>();
        boolean lockAvailable = true;

        Fixture() throws Exception {
            when(connection.getMetaData()).thenReturn(metadata);
            when(connection.getAutoCommit()).thenReturn(true);
            when(metadata.getDatabaseProductName()).thenReturn("MySQL");
            when(metadata.getDatabaseMajorVersion()).thenReturn(8);
            when(metadata.getTables(any(), any(), anyString(), any())).thenAnswer(call -> {
                var result = mock(ResultSet.class); boolean found = tables.contains(call.<String>getArgument(2));
                when(result.next()).thenReturn(found, false); when(result.getString("TABLE_NAME")).thenReturn(call.getArgument(2));
                return result;
            });
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(startsWith("SELECT GET_LOCK"))).thenAnswer(call -> {
                var result = mock(ResultSet.class); when(result.next()).thenReturn(true); when(result.getInt(1)).thenReturn(lockAvailable ? 1 : 0);
                return result;
            });
            when(statement.executeUpdate(anyString())).thenAnswer(call -> {
                if (UpgradeAivenReturnLeg.DDL.equals(call.getArgument(0))) columns.put("order_route.return_leg", new String[]{"bit(1)", "NO", "b'0'"});
                return 0;
            });
            when(connection.prepareStatement(contains("information_schema.columns"))).thenAnswer(call -> {
                var prepared = mock(PreparedStatement.class); var params = new String[2];
                doAnswer(set -> { params[set.<Integer>getArgument(0) - 1] = set.getArgument(1); return null; }).when(prepared).setString(anyInt(), anyString());
                when(prepared.executeQuery()).thenAnswer(query -> {
                    var found = columns.get(params[0] + "." + params[1]); var result = mock(ResultSet.class);
                    when(result.next()).thenReturn(found != null);
                    if (found != null) { when(result.getString(1)).thenReturn(found[0]); when(result.getString(2)).thenReturn(found[1]); when(result.getString(3)).thenReturn(found[2]); }
                    return result;
                });
                return prepared;
            });
        }
    }
}
