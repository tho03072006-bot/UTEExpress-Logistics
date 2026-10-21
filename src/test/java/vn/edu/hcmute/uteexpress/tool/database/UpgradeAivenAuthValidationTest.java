package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UpgradeAivenAuthValidationTest {
    Connection connection;DatabaseMetaData metadata;Statement statement;
    Set<String> added=new HashSet<>();AtomicBoolean index=new AtomicBoolean();
    void database()throws Exception{
        connection=mock(Connection.class);metadata=mock(DatabaseMetaData.class);statement=mock(Statement.class);
        when(connection.getMetaData()).thenReturn(metadata);when(connection.getCatalog()).thenReturn("test_only");
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");when(metadata.getDatabaseMajorVersion()).thenReturn(8);
        when(connection.createStatement()).thenReturn(statement);
        when(metadata.getTables(anyString(),isNull(),anyString(),any())).thenAnswer(call->{var row=mock(ResultSet.class);when(row.next()).thenReturn(true,false);when(row.getString("TABLE_NAME")).thenReturn(call.getArgument(2));return row;});
        when(statement.executeQuery(startsWith("SELECT GET_LOCK"))).thenAnswer(call->{var row=mock(ResultSet.class);when(row.next()).thenReturn(true);when(row.getInt(1)).thenReturn(1);return row;});
        when(metadata.getColumns(anyString(),isNull(),anyString(),anyString())).thenAnswer(call->{
            var row=mock(ResultSet.class);String name=call.getArgument(3);boolean exists=added.contains(name);when(row.next()).thenReturn(exists,false);
            if(exists){var column=UpgradeAivenAuthValidation.COLUMNS.stream().filter(c->c.name().equals(name)).findFirst().orElseThrow();when(row.getString("TABLE_NAME")).thenReturn(column.table());when(row.getString("COLUMN_NAME")).thenReturn(column.name());when(row.getInt("DATA_TYPE")).thenReturn(column.type());when(row.getInt("COLUMN_SIZE")).thenReturn(column.size());when(row.getInt("NULLABLE")).thenReturn(column.nullable()?1:0);}return row;});
        when(metadata.getIndexInfo(anyString(),isNull(),eq("shipment_order"),eq(true),eq(false))).thenAnswer(call->{var row=mock(ResultSet.class);when(row.next()).thenReturn(index.get(),false);if(index.get()){when(row.getString("INDEX_NAME")).thenReturn("uk_shipment_creation_token");when(row.getString("COLUMN_NAME")).thenReturn("creation_token");}return row;});
        when(statement.executeUpdate(anyString())).thenAnswer(call->{String sql=call.getArgument(0);if(sql.contains(" ADD COLUMN "))added.add(sql.split(" ")[5]);else if(sql.contains(" UNIQUE "))index.set(true);else fail("Unexpected mutation");return 0;});
    }
    @Test void upgradeAddsOnlySixColumnsAndOneUniqueIndexAndCanRunTwice()throws Exception{
        database();UpgradeAivenAuthValidation.upgrade(connection);UpgradeAivenAuthValidation.upgrade(connection);
        assertEquals(6,added.size());assertTrue(index.get());verify(statement,times(7)).executeUpdate(anyString());
        verify(statement,never()).executeUpdate(matches("(?i).*(DROP|DELETE|UPDATE|TRUNCATE).*"));
    }
    @Test void wrongDatabaseEngineFailsBeforeAnyMutation()throws Exception{
        database();when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThrows(IllegalArgumentException.class,()->UpgradeAivenAuthValidation.upgrade(connection));verify(statement,never()).executeUpdate(anyString());
    }
    @Test void incompatibleExistingColumnIsNotChangedOrDeleted()throws Exception{
        database();var bad=mock(ResultSet.class);when(bad.next()).thenReturn(true);when(bad.getString("TABLE_NAME")).thenReturn("app_user");when(bad.getString("COLUMN_NAME")).thenReturn("otp_attempts");when(bad.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
        when(metadata.getColumns(anyString(),isNull(),eq("app_user"),eq("otp_attempts"))).thenReturn(bad);
        assertThrows(IllegalStateException.class,()->UpgradeAivenAuthValidation.upgrade(connection));verify(statement,never()).executeUpdate(anyString());
        verify(statement).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
    }
}
