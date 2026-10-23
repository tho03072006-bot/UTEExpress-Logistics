package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.*;
import java.util.UUID;
import java.util.stream.Collectors;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Thực thi SQL trên H2 riêng trong bộ nhớ, không Spring context và không kết nối Aiven. */
class BranchSchemaSqlTest {
    @Test void migrationSchemaMatchesAllHibernateMappingsWithoutASpringContext() throws Exception {
        String url = "jdbc:h2:mem:mappings_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var newTables = BranchSchema.TABLES.stream().map(BranchSchema.Table::name).collect(Collectors.toSet());
        try (var connection = DriverManager.getConnection(url, "sa", ""); var statement = connection.createStatement()) {
            var schema = AivenSchema.statements();
            for (String sql : schema) {
                if (!sql.startsWith("create table ") && !sql.startsWith("alter table ")) continue;
                if (!newTables.contains(sql.split(" ")[2])) statement.execute(h2(sql));
            }
            for (var table : BranchSchema.TABLES) statement.execute(h2(table.ddl()));
            // operations_policy sinh từ entity nên đã có drop_off_discount; order_route lấy từ BranchSchema nên thêm cột của migration tự mang ra chi nhánh.
            statement.execute(h2(UpgradeAivenDropOff.CHANGES.get(0).ddl()));
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.connection.url", url)
                    .applySetting("hibernate.connection.username", "sa")
                    .applySetting("hibernate.connection.password", "")
                    .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                    .applySetting("hibernate.hbm2ddl.auto", "validate")
                    .applySetting("hibernate.connection.pool_size", "1").build();
            try {
                var sources = new MetadataSources(registry);
                AivenSchema.entityTypes().forEach(sources::addAnnotatedClass);
                var metadata = sources.getMetadataBuilder().applyPhysicalNamingStrategy(new CamelCaseToUnderscoresNamingStrategy()).build();
                try (var factory = metadata.buildSessionFactory()) { assertTrue(factory.isOpen()); }
            } finally { StandardServiceRegistryBuilder.destroy(registry); }
        }
    }
    @Test void allTablesAndSeedsRunTwiceWithoutChangingExistingRowsOrConfiguredTariffs() throws Exception {
        try (var connection = database()) {
            var catalog = BranchSeedData.load();
            seed(connection);
            assertEquals(3, count(connection, "branch"));
            assertEquals(59, count(connection, "province_area"));
            assertEquals(270, count(connection, "area_neighbor"));
            assertEquals(21, count(connection, "route_tariff"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE route_tariff SET surcharge = 99999 WHERE service_type = 'STANDARD'");
                statement.executeUpdate("UPDATE branch SET hub_address = 'Kho đã cấu hình' WHERE code = 'SGN'");
            }
            seed(connection);
            assertEquals(3, count(connection, "branch")); assertEquals(59, count(connection, "province_area"));
            assertEquals(270, count(connection, "area_neighbor")); assertEquals(21, count(connection, "route_tariff"));
            assertEquals(1, count(connection, "shipment_order")); assertEquals(1, count(connection, "app_user"));
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT surcharge FROM route_tariff WHERE service_type = 'STANDARD'")) {
                while (rows.next()) assertEquals(99999, rows.getInt(1));
            }
            try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT hub_address FROM branch WHERE code = 'SGN'")) {
                assertTrue(rows.next()); assertEquals("Kho đã cấu hình", rows.getString(1));
            }
            assertEquals(353, UpgradeAivenBranches.seedStatements(catalog).size());
        }
    }

    @Test void addedPolicyColumnBackfillsFifteenAndKeepsTheExistingRow() throws Exception {
        try (var connection = database(); var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT id, max_active_orders FROM operations_policy")) {
            assertTrue(rows.next()); assertEquals(7, rows.getLong("id")); assertEquals(15, rows.getInt("max_active_orders")); assertFalse(rows.next());
        }
    }

    @Test void foreignKeysAndRouteChecksRejectMissingParentsSelfNeighborsAndInvalidTariffs() throws Exception {
        try (var connection = database(); var statement = connection.createStatement()) {
            seed(connection);
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO area_neighbor VALUES ('SGN-THUDUC', 'MISSING')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO area_neighbor VALUES ('SGN-THUDUC', 'SGN-THUDUC')"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO route_tariff (origin_branch_id,dest_branch_id,service_type,surcharge,lead_time_text,revision) "
                    + "SELECT b.id,d.id,'SUPER_EXPRESS',1000,'Thử',0 FROM branch b CROSS JOIN branch d WHERE b.code='SGN' AND d.code='HAN'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("UPDATE route_tariff SET surcharge=-1 WHERE service_type='STANDARD'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM branch WHERE code='SGN'"));
        }
    }

    @Test void uniquenessRejectsDuplicateStaffAssignmentAndDuplicateOrderRoute() throws Exception {
        try (var connection = database(); var statement = connection.createStatement()) {
            seed(connection);
            statement.executeUpdate("INSERT INTO staff_branch (user_id,branch_id,assigned_by,assigned_at) SELECT 7,id,7,NOW(6) FROM branch WHERE code='SGN'");
            assertThrows(SQLException.class, () -> statement.executeUpdate("INSERT INTO staff_branch (user_id,branch_id,assigned_by,assigned_at) SELECT 7,id,7,NOW(6) FROM branch WHERE code='HAN'"));
            String route = "INSERT INTO order_route (order_id,origin_branch_id,dest_branch_id,pickup_area_code,delivery_area_code,stage,revision) "
                    + "SELECT 7,id,id,'SGN-THUDUC','SGN-Q1','PICKUP',0 FROM branch WHERE code='SGN'";
            statement.executeUpdate(route); assertThrows(SQLException.class, () -> statement.executeUpdate(route));
        }
    }

    @Test void deletingSavedAddressCascadesOnlyItsAuxiliaryArea() throws Exception {
        try (var connection = database(); var statement = connection.createStatement()) {
            seed(connection);
            statement.executeUpdate("INSERT INTO saved_address VALUES (7), (8)");
            statement.executeUpdate("INSERT INTO saved_address_area (saved_address_id, province_code, area_code) VALUES (7,'79','SGN-THUDUC'), (8,'01','HAN-CAUGIAY')");
            assertEquals(1, statement.executeUpdate("DELETE FROM saved_address WHERE id=7"));
            assertEquals(1, count(connection, "saved_address")); assertEquals(1, count(connection, "saved_address_area"));
            assertEquals(59, count(connection, "province_area"));
            try (var rows = statement.executeQuery("SELECT saved_address_id FROM saved_address_area")) { assertTrue(rows.next()); assertEquals(8, rows.getLong(1)); }
        }
    }

    @Test void deletingDraftOrderCascadesBothEndsAndPreservesOtherDrafts() throws Exception {
        try (var connection = database(); var statement = connection.createStatement()) {
            seed(connection);
            statement.executeUpdate("INSERT INTO draft_order VALUES (7), (8)");
            statement.executeUpdate("INSERT INTO draft_order_area (draft_order_id,pickup_province_code,pickup_area_code,delivery_province_code,delivery_area_code) "
                    + "VALUES (7,'79','SGN-THUDUC','01','HAN-CAUGIAY'), (8,'48','DAD-HAICHAU','79','SGN-THUDUC')");
            assertEquals(1, statement.executeUpdate("DELETE FROM draft_order WHERE id=7"));
            assertEquals(1, count(connection, "draft_order")); assertEquals(1, count(connection, "draft_order_area"));
            assertEquals(59, count(connection, "province_area"));
            try (var rows = statement.executeQuery("SELECT draft_order_id FROM draft_order_area")) { assertTrue(rows.next()); assertEquals(8, rows.getLong(1)); }
        }
    }

    @Test void deletingStaffWithoutHistoryCascadesAssignmentAndZonesButKeepsTheAssigningActor() throws Exception {
        try (var connection = database(); var statement = connection.createStatement()) {
            seed(connection);
            statement.executeUpdate("INSERT INTO app_user VALUES (8)");
            statement.executeUpdate("INSERT INTO staff_branch (user_id,branch_id,assigned_by,assigned_at) SELECT 8,id,7,NOW(6) FROM branch WHERE code='SGN'");
            statement.executeUpdate("INSERT INTO shipper_zone (shipper_id,area_code,is_primary) VALUES (8,'SGN-THUDUC',1),(8,'SGN-Q1',0)");
            assertEquals(1, statement.executeUpdate("DELETE FROM app_user WHERE id=8"));
            assertEquals(0, count(connection, "staff_branch")); assertEquals(0, count(connection, "shipper_zone"));
            assertEquals(1, count(connection, "app_user")); assertEquals(3, count(connection, "branch"));
        }
    }

    @Test void deletingOrderWithRouteAndHistoricalActorsRemainsRestricted() throws Exception {
        try (var connection = database(); var statement = connection.createStatement()) {
            seed(connection);
            statement.executeUpdate("INSERT INTO app_user VALUES (8),(9)");
            statement.executeUpdate("INSERT INTO staff_branch (user_id,branch_id,assigned_by,assigned_at) SELECT 8,id,7,NOW(6) FROM branch WHERE code='SGN'");
            statement.executeUpdate("INSERT INTO order_route (order_id,origin_branch_id,dest_branch_id,pickup_area_code,delivery_area_code,stage,pickup_shipper_id,delivery_shipper_id,revision) "
                    + "SELECT 7,b.id,d.id,'SGN-THUDUC','HAN-CAUGIAY','DEST_HUB',8,9,0 FROM branch b CROSS JOIN branch d WHERE b.code='SGN' AND d.code='HAN'");
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM shipment_order WHERE id=7"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM app_user WHERE id=7"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM app_user WHERE id=8"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM app_user WHERE id=9"));
            assertEquals(1, count(connection, "shipment_order")); assertEquals(1, count(connection, "order_route"));
            assertEquals(1, count(connection, "staff_branch")); assertEquals(3, count(connection, "app_user"));
        }
    }

    @Test void linehaulItemsAndDepartureArrivalActorsRemainRestricted() throws Exception {
        try (var connection = database(); var statement = connection.createStatement()) {
            seed(connection);
            statement.executeUpdate("INSERT INTO app_user VALUES (8)");
            statement.executeUpdate("INSERT INTO linehaul_trip (id,code,from_branch_id,to_branch_id,status,vehicle_plate,driver_name,driver_phone,departed_by,arrived_by) "
                    + "SELECT 7,'TEST-TRIP',b.id,d.id,'ARRIVED','TEST','Tài xế thử','0901234567',7,8 FROM branch b CROSS JOIN branch d WHERE b.code='SGN' AND d.code='HAN'");
            statement.executeUpdate("INSERT INTO linehaul_item (trip_id,order_id,received) VALUES (7,7,1)");
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM app_user WHERE id=7"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM app_user WHERE id=8"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM linehaul_trip WHERE id=7"));
            assertThrows(SQLException.class, () -> statement.executeUpdate("DELETE FROM shipment_order WHERE id=7"));
            assertEquals(1, count(connection, "linehaul_trip")); assertEquals(1, count(connection, "linehaul_item"));
        }
    }

    private Connection database() throws Exception {
        var connection = DriverManager.getConnection("jdbc:h2:mem:branches_" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (var statement = connection.createStatement()) {
            for (String table : new String[]{"app_user", "shipment_order", "saved_address", "draft_order", "operations_policy"})
                statement.execute("CREATE TABLE " + table + " (id BIGINT PRIMARY KEY)");
            statement.executeUpdate("INSERT INTO app_user VALUES(7)"); statement.executeUpdate("INSERT INTO shipment_order VALUES(7)");
            statement.executeUpdate("INSERT INTO operations_policy VALUES(7)");
            for (var table : BranchSchema.TABLES) statement.execute(table.ddl().replace("ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci", ""));
            statement.execute(BranchSchema.POLICY_DDL);
        }
        return connection;
    }
    private void seed(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            for (String sql : UpgradeAivenBranches.seedStatements(BranchSeedData.load())) statement.executeUpdate(sql);
        }
    }
    private int count(Connection connection, String table) throws SQLException {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM " + table)) { assertTrue(rows.next()); return rows.getInt(1); }
    }
    private String h2(String sql) {
        return sql.replaceAll("(?i) character set utf8mb4", "")
                .replaceAll("(?i) default charset=utf8mb4 collate=utf8mb4_0900_ai_ci", "")
                .replaceAll("(?i) engine=InnoDB", "");
    }
}
