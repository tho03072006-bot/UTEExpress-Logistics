package vn.edu.hcmute.uteexpress.tool.database;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Chỉ dùng JDBC giả lập để kiểm tra đường ghi; không chạy --apply lên Aiven. */
class RenameAivenBranchAccountsTest {
    @Test void printPlanDoesNotConnectAndListsEveryRename() throws Exception {
        var bytes = new ByteArrayOutputStream();
        PrintStream previous = System.out;
        try (var settings = mockStatic(AivenConnectionSettings.class); var output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            try { System.setOut(output); RenameAivenBranchAccounts.main(new String[]{"--print-plan"}); }
            finally { System.setOut(previous); }
            settings.verifyNoInteractions();
        }
        String plan = bytes.toString(StandardCharsets.UTF_8);
        for (var rename : RenameAivenBranchAccounts.RENAMES) assertTrue(plan.contains(rename.from() + " -> " + rename.to()));
    }

    @Test void checkOnlyReadsAndCountsPendingAccounts() throws Exception {
        var database = new Fixture();
        assertEquals(4, RenameAivenBranchAccounts.check(database.connection, new PrintStream(OutputStream.nullOutputStream())));
        verify(database.connection).setReadOnly(true);
        assertEquals(0, database.updates);
    }

    @Test void applyRenamesOnceKeepsRoleAndBranchAndSecondRunWritesNothing() throws Exception {
        var database = new Fixture();
        assertEquals(4, RenameAivenBranchAccounts.apply(database.connection));
        assertEquals(0, RenameAivenBranchAccounts.apply(database.connection));
        assertEquals(4, database.updates);
        assertEquals(Set.of("admin", "manager_sgn", "manager_han", "shipper_sgn1", "shipper_sgn2", "shipper_han1", "shipper_han2"), database.accounts.keySet());
        assertEquals(List.of("SHIPPER", "HAN"), database.accounts.get("shipper_han1"));
        assertEquals(0, RenameAivenBranchAccounts.check(database.connection, new PrintStream(OutputStream.nullOutputStream())));
        verify(database.statement, times(2)).execute("SELECT RELEASE_LOCK('uteexpress-schema-initialize')");
    }

    @Test void takenNewNameOrWrongBranchStopsBeforeAnyWrite() throws Exception {
        var taken = new Fixture(); taken.accounts.put("shipper_sgn2", Arrays.asList("USER", null));
        assertThrows(IllegalStateException.class, () -> RenameAivenBranchAccounts.apply(taken.connection));
        assertEquals(0, taken.updates);
        var wrongBranch = new Fixture(); wrongBranch.accounts.put("shipper3", List.of("SHIPPER", "DAD"));
        assertThrows(IllegalStateException.class, () -> RenameAivenBranchAccounts.apply(wrongBranch.connection));
        assertEquals(0, wrongBranch.updates);
    }

    @Test void lockTimeoutChangesNothing() throws Exception {
        var database = new Fixture(); database.lockAvailable = false;
        assertThrows(IllegalStateException.class, () -> RenameAivenBranchAccounts.apply(database.connection));
        assertEquals(0, database.updates);
        verify(database.statement, never()).execute(contains("RELEASE_LOCK"));
    }

    /** CSDL giả: tên đăng nhập -> [vai trò, mã chi nhánh]. */
    static final class Fixture {
        final Connection connection = mock(Connection.class);
        final DatabaseMetaData metadata = mock(DatabaseMetaData.class);
        final Statement statement = mock(Statement.class);
        final Map<String, List<String>> accounts = new HashMap<>();
        boolean lockAvailable = true; int updates;

        Fixture() throws Exception {
            accounts.put("admin", Arrays.asList("ADMIN", null));
            accounts.put("manager1", List.of("MANAGER", "SGN")); accounts.put("manager_han", List.of("MANAGER", "HAN"));
            accounts.put("shipper1", List.of("SHIPPER", "SGN")); accounts.put("shipper2", List.of("SHIPPER", "SGN"));
            accounts.put("shipper3", List.of("SHIPPER", "HAN")); accounts.put("shipper_han2", List.of("SHIPPER", "HAN"));
            when(connection.getMetaData()).thenReturn(metadata);
            when(connection.getAutoCommit()).thenReturn(true);
            when(metadata.getDatabaseProductName()).thenReturn("MySQL");
            when(metadata.getDatabaseMajorVersion()).thenReturn(8);
            when(metadata.getTables(any(), any(), anyString(), any())).thenAnswer(call -> {
                var rows = mock(ResultSet.class); var seen = new boolean[1];
                when(rows.next()).thenAnswer(next -> !seen[0] && (seen[0] = true));
                when(rows.getString("TABLE_NAME")).thenReturn(call.getArgument(2));
                return rows;
            });
            when(connection.createStatement()).thenReturn(statement);
            when(statement.executeQuery(startsWith("SELECT GET_LOCK"))).thenAnswer(call -> {
                var rows = mock(ResultSet.class);
                when(rows.next()).thenReturn(true); when(rows.getInt(1)).thenReturn(lockAvailable ? 1 : 0);
                return rows;
            });
            when(connection.prepareStatement(startsWith("SELECT u.role"))).thenAnswer(call -> {
                var prepared = mock(PreparedStatement.class); var name = new String[1];
                doAnswer(set -> { name[0] = set.getArgument(1); return null; }).when(prepared).setString(eq(1), anyString());
                when(prepared.executeQuery()).thenAnswer(query -> {
                    var found = accounts.get(name[0]); var rows = mock(ResultSet.class);
                    when(rows.next()).thenReturn(found != null);
                    if (found != null) { when(rows.getString(1)).thenReturn(found.get(0)); when(rows.getString(2)).thenReturn(found.get(1)); }
                    return rows;
                });
                return prepared;
            });
            when(connection.prepareStatement(startsWith("UPDATE app_user"))).thenAnswer(call -> {
                var prepared = mock(PreparedStatement.class); var values = new String[3];
                doAnswer(set -> { values[set.<Integer>getArgument(0) - 1] = set.getArgument(1); return null; }).when(prepared).setString(anyInt(), anyString());
                when(prepared.executeUpdate()).thenAnswer(update -> {
                    var found = accounts.get(values[1]);
                    if (found == null || !found.get(0).equals(values[2])) return 0;
                    accounts.remove(values[1]); accounts.put(values[0], found); updates++;
                    return 1;
                });
                return prepared;
            });
        }
    }
}
