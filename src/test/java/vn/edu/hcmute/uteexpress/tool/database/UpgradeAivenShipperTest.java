package vn.edu.hcmute.uteexpress.tool.database;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeAivenShipperTest {
    @Test void migrationIsAdditiveAndContainsOnlyNewTables() throws Exception {
        var statements = UpgradeAivenShipper.statements();
        assertEquals(3, statements.size());
        for (String table : List.of("order_status_history", "shipper_notification", "order_complaint"))
            assertTrue(statements.stream().anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS " + table + " (")));
        String ddl = String.join("\n", statements).toLowerCase();
        assertFalse(ddl.matches("(?s).*\\b(drop|truncate|delete|update|alter)\\b.*"));
        assertTrue(ddl.contains("utf8mb4")); assertTrue(ddl.contains("foreign key"));
        assertTrue(ddl.contains("unique (order_id, recipient_id)"));
        assertTrue(ddl.contains("unique (order_id)"));
    }
}
