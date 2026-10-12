package vn.edu.hcmute.uteexpress.tool.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ResetDemoAccountsTest {
    @TempDir Path temp;
    private Connection connection;
    private Statement locks;
    private PreparedStatement updatePassword;
    private PreparedStatement updateStaff;
    private PreparedStatement insertStaff;
    private List<Map<String, Object>> users;
    private List<Map<String, Object>> staff;

    @BeforeEach void setUp() throws Exception {
        connection = mock(Connection.class);
        locks = mock(Statement.class);
        updatePassword = mock(PreparedStatement.class);
        updateStaff = mock(PreparedStatement.class);
        insertStaff = mock(PreparedStatement.class);
        DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL");
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.createStatement()).thenReturn(locks);
        when(connection.prepareStatement(startsWith("UPDATE app_user"))).thenReturn(updatePassword);
        when(connection.prepareStatement(startsWith("UPDATE staff_account"))).thenReturn(updateStaff);
        when(connection.prepareStatement(startsWith("INSERT INTO staff_account"))).thenReturn(insertStaff);
        when(updatePassword.executeUpdate()).thenReturn(1);
        when(updateStaff.executeUpdate()).thenReturn(1);
        when(insertStaff.executeUpdate()).thenReturn(1);
        users = new ArrayList<>(List.of(user(1, "USER", true), user(2, "SHIPPER", true),
                user(3, "MANAGER", true), user(4, "ADMIN", true), user(5, "USER", false)));
        staff = new ArrayList<>(List.of(Map.of("id", 10L, "app_user_id", 4L, "active", false,
                "password_change_required", true, "access_version", 8L,
                "created_at", "2026-09-01 08:00:00", "updated_at", "2026-09-02 08:00:00",
                "issued_by", "original-operator", "revision", 7L)));
        mockRows();
    }

    @Test void requiresExplicitApplyArgument() {
        assertTrue(ResetDemoAccounts.applyRequested(new String[]{"--apply"}));
        assertFalse(ResetDemoAccounts.applyRequested(new String[0]));
        assertFalse(ResetDemoAccounts.applyRequested(new String[]{"--apply", "anything"}));
        assertFalse(ResetDemoAccounts.applyRequested(null));
    }

    @Test void backsUpBeforeUpdatesAndResetsEveryRoleWithoutChangingAccessFlags() throws Exception {
        List<String> hashes = new ArrayList<>();
        doAnswer(call -> { hashes.add(call.getArgument(1)); return null; })
                .when(updatePassword).setString(eq(1), anyString());
        doAnswer(call -> {
            try (var files = Files.list(temp)) {
                Path backup = files.findFirst().orElseThrow();
                JsonNode root = new ObjectMapper().readTree(backup.toFile());
                assertEquals(5, root.get("users").size());
                assertEquals("old-hash-1", root.get("users").get(0).get("password").asText());
                assertFalse(root.get("users").get(4).get("enabled").asBoolean());
                assertFalse(root.get("staff").get(0).get("active").asBoolean());
                assertEquals(8, root.get("staff").get(0).get("accessVersion").asLong());
                assertEquals(7, root.get("staff").get(0).get("revision").asLong());
            }
            return 1;
        }).when(updatePassword).executeUpdate();

        var result = ResetDemoAccounts.apply(connection, temp);
        assertEquals(5, result.accountCount());
        assertEquals(1, result.createdStaffCount());
        assertTrue(Files.exists(result.backupPath()));
        assertEquals(5, new HashSet<>(hashes).size());
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(hashes.stream().allMatch(hash -> encoder.matches("123456", hash)));
        verify(updatePassword, times(5)).executeUpdate();
        verify(insertStaff).setLong(1, 3L); // Only the preexisting MANAGER is missing a staff profile.
        verify(insertStaff).executeUpdate();
        verify(updateStaff).setLong(2, 10L);
        verify(updateStaff).setLong(3, 4L);
        verify(updateStaff).executeUpdate();
        verify(connection).prepareStatement("UPDATE app_user SET password = ? WHERE id = ?");
        verify(connection).prepareStatement("UPDATE staff_account SET password_change_required = 0, "
                + "access_version = access_version + 1, revision = revision + 1, "
                + "updated_at = ? WHERE id = ? AND app_user_id = ?");
        var ordered = inOrder(connection, locks, updatePassword, insertStaff, updateStaff);
        ordered.verify(connection).setAutoCommit(false);
        ordered.verify(locks).executeQuery(contains("FROM staff_account ORDER BY id FOR UPDATE"));
        ordered.verify(locks).executeQuery(contains("FROM app_user ORDER BY id FOR UPDATE"));
        ordered.verify(updatePassword, calls(3)).executeUpdate();
        ordered.verify(insertStaff).executeUpdate();
        ordered.verify(updatePassword, calls(1)).executeUpdate();
        ordered.verify(updateStaff).executeUpdate();
        ordered.verify(updatePassword, calls(1)).executeUpdate();
        ordered.verify(connection).commit();
        ordered.verify(connection).setAutoCommit(true);
        verify(connection, never()).rollback();
    }

    @Test void backupFailureRollsBackBeforePreparingAnyMutation() throws Exception {
        Path cannotBeDirectory = Files.createFile(temp.resolve("occupied"));
        assertThrows(java.io.IOException.class,
                () -> ResetDemoAccounts.apply(connection, cannotBeDirectory));
        verify(connection).rollback();
        verify(connection, never()).prepareStatement(anyString());
        verify(connection, never()).commit();
    }

    @Test void staffInsertFailureRollsBackPasswordChangesAndKeepsBackup() throws Exception {
        when(insertStaff.executeUpdate()).thenThrow(new SQLException("simulated"));
        assertThrows(SQLException.class, () -> ResetDemoAccounts.apply(connection, temp));
        verify(updatePassword, times(3)).executeUpdate();
        verify(connection).rollback();
        verify(connection, never()).commit();
        try (var files = Files.list(temp)) { assertEquals(1, files.count()); }
    }

    @Test void rejectsPublicRoleStaffWithoutChangingOrPromotingIt() throws Exception {
        staff.set(0, new HashMap<>(staff.get(0)));
        staff.get(0).put("app_user_id", 1L);
        mockRows();
        assertThrows(IllegalArgumentException.class, () -> ResetDemoAccounts.apply(connection, temp));
        verify(connection).rollback();
        verify(connection, never()).prepareStatement(anyString());
        verify(connection, never()).commit();
    }

    @Test void doesNotEnableAutocommitIfRollbackFails() throws Exception {
        when(insertStaff.executeUpdate()).thenThrow(new SQLException("simulated"));
        doThrow(new SQLException("rollback failed")).when(connection).rollback();
        SQLException error = assertThrows(SQLException.class,
                () -> ResetDemoAccounts.apply(connection, temp));
        assertEquals(1, error.getSuppressed().length);
        verify(connection, never()).setAutoCommit(true);
        verify(connection, never()).commit();
    }

    @Test void seedHashesMatchTheDocumentedPasswordAndAreAppliedOnRerun() throws Exception {
        String seed = Files.readString(Path.of("db/mysql/01_seed_du_lieu_mau.sql"));
        var hashPattern = java.util.regex.Pattern.compile("\\$2a\\$10\\$[./A-Za-z0-9]{53}");
        var hashes = hashPattern.matcher(seed).results().map(java.util.regex.MatchResult::group).toList();
        assertEquals(10, hashes.size());
        assertEquals(10, new HashSet<>(hashes).size());
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(hashes.stream().allMatch(hash -> encoder.matches("123456", hash)));
        assertTrue(seed.contains("password  = VALUES(password)"));
    }

    private void mockRows() throws Exception {
        ResultSet staffRows = rows(staff);
        ResultSet userRows = rows(users);
        when(locks.executeQuery(contains("FROM staff_account"))).thenReturn(staffRows);
        when(locks.executeQuery(contains("FROM app_user"))).thenReturn(userRows);
    }

    private ResultSet rows(List<Map<String, Object>> values) throws Exception {
        ResultSet result = mock(ResultSet.class);
        AtomicInteger position = new AtomicInteger(-1);
        when(result.next()).thenAnswer(call -> position.incrementAndGet() < values.size());
        when(result.getLong(anyString())).thenAnswer(call ->
                ((Number) values.get(position.get()).get(call.getArgument(0))).longValue());
        when(result.getString(anyString())).thenAnswer(call ->
                (String) values.get(position.get()).get(call.getArgument(0)));
        when(result.getBoolean(anyString())).thenAnswer(call ->
                (Boolean) values.get(position.get()).get(call.getArgument(0)));
        return result;
    }

    private Map<String, Object> user(long id, String role, boolean enabled) {
        return Map.of("id", id, "username", "user" + id, "role", role,
                "enabled", enabled, "password", "old-hash-" + id);
    }
}
