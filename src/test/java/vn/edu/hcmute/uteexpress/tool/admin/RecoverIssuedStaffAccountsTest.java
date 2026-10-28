package vn.edu.hcmute.uteexpress.tool.admin;

import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class RecoverIssuedStaffAccountsTest {
    private Connection connection;
    private PreparedStatement lookup, userUpdate, staffUpdate, clearEmail, audit;
    private ResultSet admin, manager;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private List<RecoverIssuedStaffAccounts.Credential> credentials;
    @BeforeEach void setup() throws Exception {
        credentials = List.of(RecoverIssuedStaffAccounts.generate("admin", AppUser.Role.ADMIN),
                RecoverIssuedStaffAccounts.generate("manager1", AppUser.Role.MANAGER));
        connection = mock(Connection.class); var meta = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(meta); when(meta.getDatabaseProductName()).thenReturn("MySQL");
        when(meta.getDatabaseMajorVersion()).thenReturn(8); when(connection.getAutoCommit()).thenReturn(true);
        var lock = mock(Statement.class); var lockRows = mock(ResultSet.class);
        when(connection.createStatement()).thenReturn(lock); when(lock.executeQuery(anyString())).thenReturn(lockRows);
        lookup = mock(PreparedStatement.class); userUpdate = mock(PreparedStatement.class); staffUpdate = mock(PreparedStatement.class);
        clearEmail = mock(PreparedStatement.class); audit = mock(PreparedStatement.class);
        when(connection.prepareStatement(startsWith("SELECT"))).thenReturn(lookup);
        when(connection.prepareStatement(startsWith("UPDATE app_user"))).thenReturn(userUpdate);
        when(connection.prepareStatement(startsWith("UPDATE staff_account"))).thenReturn(staffUpdate);
        when(connection.prepareStatement(startsWith("DELETE FROM email_change_request"))).thenReturn(clearEmail);
        when(connection.prepareStatement(startsWith("INSERT INTO audit_event"))).thenReturn(audit);
        when(userUpdate.executeUpdate()).thenReturn(1); when(staffUpdate.executeUpdate()).thenReturn(1); when(audit.executeUpdate()).thenReturn(1);
        admin = account("admin", "ADMIN", 1); manager = account("manager1", "MANAGER", 2);
        when(lookup.executeQuery()).thenReturn(admin, manager);
    }
    private ResultSet account(String name, String role, long id) throws Exception {
        var result = mock(ResultSet.class); when(result.next()).thenReturn(true);
        when(result.getString("username")).thenReturn(name); when(result.getString("role")).thenReturn(role);
        when(result.getString("password")).thenReturn(encoder.encode("OldPassword@2020"));
        when(result.getBoolean("enabled")).thenReturn(true); when(result.getBoolean("active")).thenReturn(true);
        when(result.getLong("user_id")).thenReturn(id); when(result.getLong("staff_id")).thenReturn(100 + id);
        when(result.getLong("user_revision")).thenReturn(7L); when(result.getLong("staff_revision")).thenReturn(3L); return result;
    }
    @Test void generatesDifferentStrongPasswordsAndRedactsObjectString() {
        assertNotEquals(credentials.get(0).password(), credentials.get(1).password());
        for (var credential : credentials) {
            assertEquals(24, credential.password().length()); assertTrue(encoder.matches(credential.password(), credential.hash()));
            assertFalse(credential.toString().contains(credential.password())); assertFalse(credential.toString().contains(credential.hash()));
        }
    }
    @Test void recoversExactlyTwoExistingUsersAndWritesPrivateFileBeforeCommit() throws Exception {
        var fileWritten = new AtomicBoolean();
        doAnswer(inv -> { assertTrue(fileWritten.get()); return null; }).when(connection).commit();
        RecoverIssuedStaffAccounts.recover(connection, credentials, () -> fileWritten.set(true));
        verify(connection).commit(); verify(connection, never()).rollback();
        verify(userUpdate).setLong(2, 1L); verify(userUpdate).setLong(2, 2L);
        verify(staffUpdate).setLong(2, 101L); verify(staffUpdate).setLong(2, 102L);
        var hashes = ArgumentCaptor.forClass(String.class); verify(userUpdate, times(2)).setString(eq(1), hashes.capture());
        assertTrue(encoder.matches(credentials.get(0).password(), hashes.getAllValues().get(0)));
        assertTrue(encoder.matches(credentials.get(1).password(), hashes.getAllValues().get(1)));
        verify(clearEmail, times(2)).executeUpdate(); verify(audit, times(2)).executeUpdate();
        verify(connection, never()).prepareStatement(contains("INSERT INTO app_user"));
        verify(connection, never()).prepareStatement(contains("SET role"));
    }
    @Test void missingManagerRollsBackBeforeChangingAdmin() throws Exception {
        when(manager.next()).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> RecoverIssuedStaffAccounts.recover(connection, credentials, () -> fail("must not write credentials")));
        verify(connection).rollback(); verifyNoInteractions(userUpdate, staffUpdate, audit); verify(connection, never()).commit();
    }
    @Test void rejectsWrongRoleWithoutPromotingUser() throws Exception {
        when(manager.getString("role")).thenReturn("USER");
        assertThrows(IllegalArgumentException.class, () -> RecoverIssuedStaffAccounts.recover(connection, credentials, () -> {}));
        verify(connection).rollback(); verifyNoInteractions(userUpdate, staffUpdate, audit);
    }
    @Test void doesNotUnlockAdministrativelyLockedAccount() throws Exception {
        when(admin.getBoolean("locked_by_admin")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> RecoverIssuedStaffAccounts.recover(connection, credentials, () -> {}));
        verify(connection).rollback(); verifyNoInteractions(userUpdate, staffUpdate, audit);
    }
    @Test void failedStaffUpdateRollsBackBothAccounts() throws Exception {
        when(staffUpdate.executeUpdate()).thenReturn(0);
        assertThrows(SQLException.class, () -> RecoverIssuedStaffAccounts.recover(connection, credentials, () -> fail("must not write credentials")));
        verify(connection).rollback(); verify(connection, never()).commit();
    }
    @Test void failedCredentialFileWritePreventsDatabaseCommit() throws Exception {
        assertThrows(IOException.class, () -> RecoverIssuedStaffAccounts.recover(connection, credentials, () -> { throw new IOException("simulated"); }));
        verify(connection).rollback(); verify(connection, never()).commit();
    }
    @Test void failedAuditInsertPreventsUnauditedRecovery() throws Exception {
        when(audit.executeUpdate()).thenThrow(new SQLException("simulated"));
        assertThrows(SQLException.class, () -> RecoverIssuedStaffAccounts.recover(connection, credentials, () -> {}));
        verify(connection).rollback(); verify(connection, never()).commit();
    }
    @Test void refusesWeakPasswordBeforeAccessingDatabase() {
        var weak = new RecoverIssuedStaffAccounts.Credential("admin", AppUser.Role.ADMIN, "weak", encoder.encode("weak"));
        assertThrows(IllegalArgumentException.class, () -> RecoverIssuedStaffAccounts.recover(connection, List.of(weak, credentials.get(1)), () -> {}));
        verifyNoInteractions(connection);
    }
    @Test void refusesExistingTransactionInsteadOfCommittingSomeoneElsesWork() throws Exception {
        when(connection.getAutoCommit()).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> RecoverIssuedStaffAccounts.recover(connection, credentials, () -> {}));
        verify(connection, never()).setAutoCommit(false); verify(connection, never()).commit();
    }
}
