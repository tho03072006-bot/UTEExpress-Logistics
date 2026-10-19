package vn.edu.hcmute.uteexpress.tool.database;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.sql.*;
import org.junit.jupiter.api.*;

class UpgradeAivenCloudFilesTest {
    Connection connection; DatabaseMetaData meta; Statement statement;
    @BeforeEach void setup() throws Exception {
        connection=mock(Connection.class); meta=mock(DatabaseMetaData.class); statement=mock(Statement.class);
        when(connection.getMetaData()).thenReturn(meta); when(meta.getDatabaseProductName()).thenReturn("MySQL");
        when(meta.getDatabaseMajorVersion()).thenReturn(8); when(connection.getCatalog()).thenReturn("defaultdb");
        when(connection.createStatement()).thenReturn(statement);
        var lock=mock(ResultSet.class); when(lock.next()).thenReturn(true); when(lock.getInt(1)).thenReturn(1);
        when(statement.executeQuery(anyString())).thenReturn(lock);
        when(meta.getTables(any(),isNull(),any(),any())).thenAnswer(call -> { var rs=mock(ResultSet.class); when(rs.next()).thenReturn(true); return rs; });
        when(meta.getColumns(any(),isNull(),any(),any())).thenAnswer(call -> mock(ResultSet.class));
    }
    @Test void checkNeverMutatesSchemaOrData() throws Exception {
        UpgradeAivenCloudFiles.upgrade(connection,false);
        verify(statement,never()).executeUpdate(anyString());
        verify(statement).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
    }
    @Test void applyOnlyAddsTwoNullableColumns() throws Exception {
        UpgradeAivenCloudFiles.upgrade(connection,true);
        verify(statement).executeUpdate("ALTER TABLE order_complaint ADD COLUMN image_asset VARCHAR(255) NULL, ALGORITHM=INSTANT");
        verify(statement).executeUpdate("ALTER TABLE operations_case ADD COLUMN evidence_asset VARCHAR(255) NULL, ALGORITHM=INSTANT");
        verify(statement,times(2)).executeUpdate(anyString());
    }
    @Test void existingCompatibleColumnsMakeRerunIdempotent() throws Exception {
        when(meta.getColumns(any(),isNull(),any(),any())).thenAnswer(call -> {
            var rs=mock(ResultSet.class); when(rs.next()).thenReturn(true); when(rs.getInt("DATA_TYPE")).thenReturn(Types.VARCHAR);
            when(rs.getInt("COLUMN_SIZE")).thenReturn(255); when(rs.getInt("NULLABLE")).thenReturn(DatabaseMetaData.columnNullable); return rs;
        });
        UpgradeAivenCloudFiles.upgrade(connection,true); verify(statement,never()).executeUpdate(anyString());
    }
    @Test void incompatibleColumnAbortsBeforeAnyDdl() throws Exception {
        when(meta.getColumns(any(),isNull(),any(),any())).thenAnswer(call -> {var rs=mock(ResultSet.class); when(rs.next()).thenReturn(true); return rs;});
        assertThrows(IllegalStateException.class,() -> UpgradeAivenCloudFiles.upgrade(connection,true));
        verify(statement,never()).executeUpdate(anyString());
    }
}
