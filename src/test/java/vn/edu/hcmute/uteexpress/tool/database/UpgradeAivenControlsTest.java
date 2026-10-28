package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UpgradeAivenControlsTest {
    @Test void refusesUnsupportedEngineWithoutRunningDdl() throws Exception {
        var c = connection(true, false); when(c.getMetaData().getDatabaseProductName()).thenReturn("H2");
        assertThrows(IllegalArgumentException.class, () -> UpgradeAivenControls.upgrade(c));
        verify(c.createStatement(), never()).executeUpdate(anyString());
    }
    @Test void refusesIncompleteBaseAndReleasesNamedLock() throws Exception {
        var c = connection(false, false);
        assertThrows(IllegalStateException.class, () -> UpgradeAivenControls.upgrade(c));
        verify(c.createStatement(), never()).executeUpdate(anyString());
        verify(c.createStatement()).execute(contains("RELEASE_LOCK"));
    }
    @Test void existingSchemaOnlySeedsMissingSingletonWithoutRecreatingTables() throws Exception {
        var c = connection(true, false); UpgradeAivenControls.upgrade(c);
        verify(c.createStatement(), times(1)).executeUpdate(startsWith("INSERT INTO operations_policy"));
        verify(c.createStatement(), times(1)).executeUpdate(anyString());
        verify(c.createStatement()).execute(contains("RELEASE_LOCK"));
    }
    @Test void compositeIndexIsNotMistakenForIdempotencyTokenUniqueness() throws Exception {
        var c = connection(true, true); UpgradeAivenControls.upgrade(c);
        verify(c.createStatement()).executeUpdate(contains("UNIQUE (request_token)"));
        verify(c.createStatement(), never()).executeUpdate(matches("(?i).*(drop|truncate|delete).*"));
    }
    @Test void rejectsIncompatibleTokenColumnWithoutDestructiveAlter() throws Exception {
        var c = connection(true, false); var columns = c.getMetaData().getColumns("defaultdb", null, "operations_case", "request_token");
        when(columns.getInt("COLUMN_SIZE")).thenReturn(100);
        assertThrows(IllegalStateException.class, () -> UpgradeAivenControls.upgrade(c));
        verify(c.createStatement(), never()).executeUpdate(anyString());
        verify(c.createStatement()).execute(contains("RELEASE_LOCK"));
    }
    private Connection connection(boolean complete, boolean compositeToken) throws Exception {
        var c = mock(Connection.class); var meta = mock(DatabaseMetaData.class); var statement = mock(Statement.class);
        when(c.getMetaData()).thenReturn(meta); when(c.getCatalog()).thenReturn("defaultdb"); when(c.createStatement()).thenReturn(statement);
        when(meta.getDatabaseProductName()).thenReturn("MySQL"); when(meta.getDatabaseMajorVersion()).thenReturn(8);
        var lock = mock(ResultSet.class); when(lock.next()).thenReturn(true); when(lock.getInt(1)).thenReturn(1);
        when(statement.executeQuery(contains("GET_LOCK"))).thenReturn(lock);
        var ddl = AivenSchema.statements();
        var tables = complete ? ddl.stream().filter(s -> s.startsWith("create table ")).map(s -> s.split(" ")[2]).toList() : List.of("shipment_order");
        var tableRows = rows("TABLE_NAME", tables);
        when(meta.getTables(eq("defaultdb"), isNull(), eq("%"), any(String[].class))).thenReturn(tableRows);
        var columns = mock(ResultSet.class); when(columns.next()).thenReturn(true); when(columns.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
        when(columns.getInt("COLUMN_SIZE")).thenReturn(36);
        when(meta.getColumns("defaultdb", null, "operations_case", "request_token")).thenReturn(columns);
        var foreignKeys = ddl.stream().filter(s -> s.startsWith("alter table ")).map(s -> s.split(" ")[5]).toList();
        when(meta.getImportedKeys(anyString(), isNull(), anyString())).thenAnswer(inv -> rows("FK_NAME", foreignKeys));
        var names = ddl.stream().filter(s -> s.startsWith("create index ")).map(s -> s.split(" ")[2]).toList();
        when(meta.getIndexInfo(anyString(), isNull(), anyString(), eq(false), eq(false))).thenAnswer(inv -> rows("INDEX_NAME", names));
        when(meta.getIndexInfo(anyString(), isNull(), eq("operations_case"), eq(true), eq(false))).thenAnswer(inv -> {
            var result = rows("COLUMN_NAME", compositeToken ? List.of("request_token", "kind") : List.of("request_token"));
            when(result.getString("INDEX_NAME")).thenReturn("unique_token_test"); return result;
        });
        return c;
    }
    private ResultSet rows(String column, List<String> values) throws SQLException {
        var result = mock(ResultSet.class); var index = new java.util.concurrent.atomic.AtomicInteger(-1);
        when(result.next()).thenAnswer(inv -> index.incrementAndGet() < values.size());
        when(result.getString(column)).thenAnswer(inv -> values.get(index.get())); return result;
    }
}
