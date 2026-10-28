package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UpgradeAivenOrderSubmissionsTest {
    Connection connection; DatabaseMetaData metadata; Statement statement;
    AtomicBoolean created=new AtomicBoolean();
    void database() throws Exception {
        connection=mock(Connection.class);metadata=mock(DatabaseMetaData.class);statement=mock(Statement.class);
        when(connection.getMetaData()).thenReturn(metadata);when(connection.getCatalog()).thenReturn("test_only");
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");when(metadata.getDatabaseMajorVersion()).thenReturn(8);
        when(connection.createStatement()).thenReturn(statement);
        when(metadata.getTables(anyString(),isNull(),anyString(),any())).thenAnswer(call->{
            String table=call.getArgument(2);var rows=mock(ResultSet.class);
            when(rows.next()).thenReturn(!table.equals(UpgradeAivenOrderSubmissions.TABLE)||created.get(),false);
            when(rows.getString("TABLE_NAME")).thenReturn(table);return rows;
        });
        when(statement.executeQuery(startsWith("SELECT GET_LOCK"))).thenAnswer(call->{var rows=mock(ResultSet.class);when(rows.next()).thenReturn(true);when(rows.getInt(1)).thenReturn(1);return rows;});
        when(metadata.getColumns(anyString(),isNull(),eq(UpgradeAivenOrderSubmissions.TABLE),anyString())).thenAnswer(call->{
            var column=UpgradeAivenOrderSubmissions.COLUMNS.stream().filter(c->c.name().equals(call.getArgument(3))).findFirst().orElseThrow();
            var rows=mock(ResultSet.class);when(rows.next()).thenReturn(true,false);
            when(rows.getString("TABLE_NAME")).thenReturn(column.table());when(rows.getString("COLUMN_NAME")).thenReturn(column.name());
            when(rows.getInt("DATA_TYPE")).thenReturn(column.type());when(rows.getInt("COLUMN_SIZE")).thenReturn(column.size());
            when(rows.getInt("NULLABLE")).thenReturn(column.nullable()?1:0);when(rows.getString("IS_AUTOINCREMENT")).thenReturn("YES");return rows;
        });
        when(metadata.getPrimaryKeys(anyString(),isNull(),anyString())).thenAnswer(call->{var rows=mock(ResultSet.class);when(rows.next()).thenReturn(true,false);when(rows.getString("COLUMN_NAME")).thenReturn("id");return rows;});
        when(metadata.getIndexInfo(anyString(),isNull(),anyString(),eq(true),eq(false))).thenAnswer(call->{var rows=mock(ResultSet.class);when(rows.next()).thenReturn(true,false);when(rows.getString("INDEX_NAME")).thenReturn("uk_order_form_token");when(rows.getString("COLUMN_NAME")).thenReturn("token");return rows;});
        when(metadata.getImportedKeys(anyString(),isNull(),anyString())).thenAnswer(call->{var rows=mock(ResultSet.class);when(rows.next()).thenReturn(true,false);when(rows.getString("FKCOLUMN_NAME")).thenReturn("user_id");when(rows.getString("PKTABLE_NAME")).thenReturn("app_user");when(rows.getString("PKCOLUMN_NAME")).thenReturn("id");when(rows.getInt("DELETE_RULE")).thenReturn(DatabaseMetaData.importedKeyRestrict);return rows;});
        when(statement.executeUpdate(UpgradeAivenOrderSubmissions.DDL)).thenAnswer(call->{created.set(true);return 0;});
    }
    @Test void createsOnlyOneAdditiveTableAndSecondRunDoesNotWriteAgain() throws Exception {
        database();UpgradeAivenOrderSubmissions.upgrade(connection);UpgradeAivenOrderSubmissions.upgrade(connection);
        assertTrue(UpgradeAivenOrderSubmissions.ready(connection));verify(statement,times(1)).executeUpdate(anyString());
        verify(statement,never()).executeUpdate(matches("(?i).*(DROP|DELETE|UPDATE|TRUNCATE|ALTER).*"));
        verify(statement,times(2)).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
    }
    @Test void wrongEngineAndLockTimeoutNeverWrite() throws Exception {
        database();when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThrows(IllegalArgumentException.class,()->UpgradeAivenOrderSubmissions.upgrade(connection));verify(statement,never()).executeUpdate(anyString());
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");var lock=mock(ResultSet.class);when(lock.next()).thenReturn(true);when(lock.getInt(1)).thenReturn(0);
        when(statement.executeQuery(startsWith("SELECT GET_LOCK"))).thenReturn(lock);
        assertThrows(IllegalStateException.class,()->UpgradeAivenOrderSubmissions.upgrade(connection));verify(statement,never()).executeUpdate(anyString());
    }
    @Test void incompatibleExistingTableIsNotChangedOrDeletedAndLockIsReleased() throws Exception {
        database();created.set(true);var missing=mock(ResultSet.class);when(missing.next()).thenReturn(false);
        when(metadata.getColumns(anyString(),isNull(),eq(UpgradeAivenOrderSubmissions.TABLE),eq("token"))).thenReturn(missing);
        assertThrows(IllegalStateException.class,()->UpgradeAivenOrderSubmissions.upgrade(connection));verify(statement,never()).executeUpdate(anyString());
        verify(statement).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
    }
    @Test void missingUniqueConstraintFailsClosed() throws Exception {
        database();created.set(true);var missing=mock(ResultSet.class);when(missing.next()).thenReturn(false);
        when(metadata.getIndexInfo(anyString(),isNull(),anyString(),eq(true),eq(false))).thenReturn(missing);
        assertThrows(IllegalStateException.class,()->UpgradeAivenOrderSubmissions.upgrade(connection));verify(statement,never()).executeUpdate(anyString());
    }
}
