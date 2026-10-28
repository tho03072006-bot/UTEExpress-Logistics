package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UpgradeAivenAccountsTest {
    @Test void addsOnlyMissingColumnsWithDefaultsAndNeverDropsData() throws Exception {
        var c = connection(false); UpgradeAivenAccounts.upgrade(c);
        var sql = c.createStatement();
        verify(sql).executeUpdate("ALTER TABLE app_user ADD COLUMN revision BIGINT NOT NULL DEFAULT 0");
        verify(sql).executeUpdate("ALTER TABLE app_user ADD COLUMN locked_by_admin BIT NOT NULL DEFAULT 0");
        verify(sql).executeUpdate("ALTER TABLE app_user ADD COLUMN access_version BIGINT NOT NULL DEFAULT 0");
        verify(sql, never()).executeUpdate(contains("DROP")); verify(sql, never()).executeUpdate(contains("DELETE"));
        verify(sql).execute(contains("RELEASE_LOCK"));
    }
    @Test void existingValidSchemaMakesRepeatRunReadOnly() throws Exception {
        var c = connection(true); UpgradeAivenAccounts.upgrade(c); UpgradeAivenAccounts.upgrade(c);
        verify(c.createStatement(), never()).executeUpdate(anyString());
        verify(c.createStatement(), times(2)).execute(contains("RELEASE_LOCK"));
    }
    @Test void wrongExistingTypeIsNotSilentlyRewrittenAndLockIsReleased() throws Exception {
        var c = connection(true); var bad = mock(ResultSet.class);
        when(bad.next()).thenReturn(true); when(bad.getInt("DATA_TYPE")).thenReturn(Types.INTEGER);
        when(c.getMetaData().getColumns("defaultdb", null, "app_user", "revision")).thenReturn(bad);
        assertThrows(IllegalStateException.class, () -> UpgradeAivenAccounts.upgrade(c));
        verify(c.createStatement(), never()).executeUpdate(anyString()); verify(c.createStatement()).execute(contains("RELEASE_LOCK"));
    }
    @Test void missingTableAndWrongEngineAreRejectedBeforeMigration() throws Exception {
        var c = connection(false); when(c.getMetaData().getDatabaseProductName()).thenReturn("SQL Server");
        assertThrows(IllegalArgumentException.class, () -> UpgradeAivenAccounts.upgrade(c));
        when(c.getMetaData().getDatabaseProductName()).thenReturn("MySQL");
        var table = c.getMetaData().getTables("defaultdb", null, "app_user", new String[]{"TABLE"});
        when(table.next()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> UpgradeAivenAccounts.upgrade(c));
        verify(c.createStatement(), never()).executeUpdate(anyString());
    }
    @Test void lockTimeoutDoesNotAlterSchema() throws Exception {
        var c = connection(false); when(c.createStatement().executeQuery(anyString()).getInt(1)).thenReturn(0);
        assertThrows(IllegalStateException.class, () -> UpgradeAivenAccounts.upgrade(c));
        verify(c.createStatement(), never()).executeUpdate(anyString());
    }
    @Test void freshSchemaAlsoContainsDefaultsForManualInitialAdminAndSeedTools() {
        var sql = AivenSchema.statements().stream().filter(s -> s.startsWith("create table app_user ")).findFirst().orElseThrow();
        assertTrue(sql.contains("revision bigint default 0 not null"));
        assertTrue(sql.contains("locked_by_admin bit default 0 not null"));
        assertTrue(sql.contains("access_version bigint default 0 not null"));
    }
    private Connection connection(boolean hasColumns) throws Exception {
        var c = mock(Connection.class); var metadata = mock(DatabaseMetaData.class); var statement = mock(Statement.class);
        var table = mock(ResultSet.class); var lock = mock(ResultSet.class);
        when(c.getMetaData()).thenReturn(metadata); when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(metadata.getDatabaseMajorVersion()).thenReturn(8); when(c.getCatalog()).thenReturn("defaultdb");
        when(metadata.getTables(eq("defaultdb"), isNull(), eq("app_user"), any(String[].class))).thenReturn(table);
        when(table.next()).thenReturn(true); when(c.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(lock); when(lock.next()).thenReturn(true); when(lock.getInt(1)).thenReturn(1);
        for (var name : new String[]{"revision", "locked_by_admin", "access_version"}) {
            var column = mock(ResultSet.class); when(column.next()).thenReturn(hasColumns);
            when(column.getInt("DATA_TYPE")).thenReturn("locked_by_admin".equals(name) ? Types.BIT : Types.BIGINT);
            when(column.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNoNulls);
            when(metadata.getColumns("defaultdb", null, "app_user", name)).thenReturn(column);
        }
        return c;
    }
}
