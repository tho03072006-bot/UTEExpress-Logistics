package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UpgradeAivenOrderDistanceTest {
    Connection connection; DatabaseMetaData metadata; Statement statement;
    Set<String> added = new HashSet<>();

    void database() throws Exception {
        connection = mock(Connection.class); metadata = mock(DatabaseMetaData.class); statement = mock(Statement.class);
        when(connection.getMetaData()).thenReturn(metadata); when(connection.getCatalog()).thenReturn("test_only");
        when(metadata.getDatabaseProductName()).thenReturn("MySQL"); when(metadata.getDatabaseMajorVersion()).thenReturn(8);
        when(connection.createStatement()).thenReturn(statement);
        when(metadata.getTables(anyString(), isNull(), anyString(), any())).thenAnswer(call -> {
            var row = mock(ResultSet.class); when(row.next()).thenReturn(true, false); when(row.getString("TABLE_NAME")).thenReturn(call.getArgument(2)); return row; });
        when(statement.executeQuery(startsWith("SELECT GET_LOCK"))).thenAnswer(call -> {
            var row = mock(ResultSet.class); when(row.next()).thenReturn(true); when(row.getInt(1)).thenReturn(1); return row; });
        when(metadata.getColumns(anyString(), isNull(), anyString(), anyString())).thenAnswer(call -> {
            var row = mock(ResultSet.class); String name = call.getArgument(3); boolean exists = added.contains(name);
            when(row.next()).thenReturn(exists, false);
            if (exists) {
                var column = UpgradeAivenOrderDistance.COLUMNS.stream().filter(c -> c.name().equals(name)).findFirst().orElseThrow();
                when(row.getString("TABLE_NAME")).thenReturn(column.table()); when(row.getString("COLUMN_NAME")).thenReturn(column.name());
                when(row.getInt("DATA_TYPE")).thenReturn(column.type()); when(row.getInt("COLUMN_SIZE")).thenReturn(column.size());
                when(row.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNullable);
            }
            return row;
        });
        when(statement.executeUpdate(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            if (!sql.startsWith("ALTER TABLE shipment_order ADD COLUMN ")) fail("Unexpected mutation: " + sql);
            added.add(sql.split(" ")[5]); return 0;
        });
    }

    @Test void addsOnlyTwoNullableColumnsAndCanRunTwice() throws Exception {
        database();
        UpgradeAivenOrderDistance.upgrade(connection); UpgradeAivenOrderDistance.upgrade(connection);
        assertEquals(Set.of("distance_km", "distance_fee"), added);
        verify(statement, times(2)).executeUpdate(anyString());
        verify(statement).executeUpdate("ALTER TABLE shipment_order ADD COLUMN distance_km DECIMAL(7,2) NULL");
        verify(statement).executeUpdate("ALTER TABLE shipment_order ADD COLUMN distance_fee DECIMAL(12,0) NULL");
        verify(statement, never()).executeUpdate(matches("(?i).*(DROP|DELETE|UPDATE |TRUNCATE|NOT NULL).*"));
    }
    @Test void wrongDatabaseFailsBeforeAnyMutation() throws Exception {
        database(); when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThrows(IllegalArgumentException.class, () -> UpgradeAivenOrderDistance.upgrade(connection));
        verify(statement, never()).executeUpdate(anyString());
    }
    @Test void incompatibleExistingColumnIsNotChanged() throws Exception {
        database(); var bad = mock(ResultSet.class); when(bad.next()).thenReturn(true);
        when(bad.getString("TABLE_NAME")).thenReturn("shipment_order"); when(bad.getString("COLUMN_NAME")).thenReturn("distance_km");
        when(bad.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
        when(metadata.getColumns(anyString(), isNull(), eq("shipment_order"), eq("distance_km"))).thenReturn(bad);
        assertThrows(IllegalStateException.class, () -> UpgradeAivenOrderDistance.upgrade(connection));
        verify(statement, never()).executeUpdate(anyString());
        verify(statement).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
    }
}
