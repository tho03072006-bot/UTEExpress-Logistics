package vn.edu.hcmute.uteexpress.tool.database;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpgradeAivenShipperTest {
    @Test void migrationIsAdditiveAndContainsOnlyNewTables() throws Exception {
        var statements = UpgradeAivenShipper.statements();
        assertEquals(5, statements.size());
        for (String table : List.of("order_status_history", "shipper_notification", "order_complaint",
                "cod_settlement", "cod_settlement_item"))
            assertTrue(statements.stream().anyMatch(sql -> sql.startsWith("CREATE TABLE IF NOT EXISTS " + table + " (")));
        String ddl = String.join("\n", statements).toLowerCase();
        assertFalse(ddl.matches("(?s).*\\b(drop|truncate|delete|update|alter)\\b.*"));
        assertTrue(ddl.contains("utf8mb4")); assertTrue(ddl.contains("foreign key"));
        assertTrue(ddl.contains("unique (order_id, recipient_id)"));
        assertTrue(ddl.contains("unique (order_id)"));
    }

    @Test void codTablesAreCreatedAfterTheTablesTheyReference() throws Exception {
        var statements = UpgradeAivenShipper.statements();
        int settlement = indexOf(statements, "cod_settlement (");
        int item = indexOf(statements, "cod_settlement_item (");
        assertTrue(settlement >= 0 && item > settlement, "cod_settlement phải được tạo trước cod_settlement_item");
        String itemDdl = statements.get(item).toLowerCase();
        assertTrue(itemDdl.contains("constraint uk_cod_item_order unique (order_id)"), "mỗi vận đơn chỉ nằm trong một phiếu");
        assertTrue(itemDdl.contains("references cod_settlement(id)") && itemDdl.contains("references shipment_order(id)"));
    }

    private static int indexOf(List<String> statements, String table) {
        for (int i = 0; i < statements.size(); i++)
            if (statements.get(i).startsWith("CREATE TABLE IF NOT EXISTS " + table)) return i;
        return -1;
    }
}
