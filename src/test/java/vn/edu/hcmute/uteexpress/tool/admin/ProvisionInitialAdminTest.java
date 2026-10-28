package vn.edu.hcmute.uteexpress.tool.admin;

import java.sql.*;
import org.junit.jupiter.api.*;
import vn.edu.hcmute.uteexpress.dto.admin.StaffAccountCreateRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ProvisionInitialAdminTest {
    private Connection connection;
    private Statement statement;
    private ResultSet count;
    @BeforeEach void setUp() throws Exception {
        connection = mock(Connection.class);
        statement = mock(Statement.class);
        count = mock(ResultSet.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(count);
        when(count.next()).thenReturn(true);
        when(count.getInt(1)).thenReturn(1);
    }
    @Test void refusesToOverwriteExistingStaffAndRollsBack() throws Exception {
        when(count.getLong(1)).thenReturn(1L);
        assertThrows(IllegalArgumentException.class,
                () -> ProvisionInitialAdmin.createInitialAdmin(connection, form(), "hash"));
        verify(connection).rollback();
        verify(connection, never()).prepareStatement(anyString());
        verify(connection, never()).commit();
    }
    @Test void rollsBackUserCreationWhenStaffInsertFails() throws Exception {
        when(count.getLong(1)).thenReturn(0L);
        PreparedStatement check = mock(PreparedStatement.class);
        PreparedStatement insertUser = mock(PreparedStatement.class);
        PreparedStatement insertStaff = mock(PreparedStatement.class);
        ResultSet userId = mock(ResultSet.class);
        when(connection.prepareStatement(startsWith("SELECT"))).thenReturn(check);
        when(check.executeQuery()).thenReturn(count);
        when(connection.prepareStatement(startsWith("INSERT INTO app_user"), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(insertUser);
        when(insertUser.getGeneratedKeys()).thenReturn(userId);
        when(userId.next()).thenReturn(true);
        when(userId.getLong(1)).thenReturn(10L);
        when(connection.prepareStatement(startsWith("INSERT INTO staff_account"))).thenReturn(insertStaff);
        when(insertStaff.executeUpdate()).thenThrow(new SQLException("simulated"));
        assertThrows(SQLException.class,
                () -> ProvisionInitialAdmin.createInitialAdmin(connection, form(), "hash"));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
    @Test void refusesExistingCustomerIdentityWithoutUpdatingIt() throws Exception {
        when(count.getLong(1)).thenReturn(0L, 1L);
        PreparedStatement check = mock(PreparedStatement.class);
        when(connection.prepareStatement(startsWith("SELECT"))).thenReturn(check);
        when(check.executeQuery()).thenReturn(count);
        assertThrows(IllegalArgumentException.class,
                () -> ProvisionInitialAdmin.createInitialAdmin(connection, form(), "hash"));
        verify(connection).rollback();
        verify(connection, never()).prepareStatement(startsWith("INSERT"));
        verify(connection, never()).prepareStatement(startsWith("UPDATE"));
    }
    private StaffAccountCreateRequest form() {
        var form = new StaffAccountCreateRequest();
        form.setUsername("admin01"); form.setEmail("admin@example.com"); form.setFullName("Quản trị viên");
        return form;
    }
}
