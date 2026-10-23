package vn.edu.hcmute.uteexpress.tool.database;

import java.sql.Types;
import java.util.*;

/** DDL bổ sung có mô tả cột, khóa để --check kiểm tra đúng cấu trúc thay vì chỉ đếm bảng. */
final class BranchSchema {
    record Column(String name, String definition, int type, int size, int scale, boolean nullable, boolean generated) { }
    record ForeignKey(String name, String column, String targetTable, String targetColumn, boolean cascadeDelete) {
        String deleteAction() { return cascadeDelete ? "CASCADE" : "RESTRICT"; }
    }
    record UniqueKey(String name, List<String> columns) { }
    record Table(String name, List<Column> columns, List<String> primaryKey, List<UniqueKey> uniqueKeys,
                 List<ForeignKey> foreignKeys, List<String> extras) {
        String ddl() {
            var parts = new ArrayList<String>();
            columns.forEach(column -> parts.add(column.name() + " " + column.definition()));
            parts.add("PRIMARY KEY (" + String.join(", ", primaryKey) + ")");
            uniqueKeys.forEach(key -> parts.add("CONSTRAINT " + key.name() + " UNIQUE (" + String.join(", ", key.columns()) + ")"));
            foreignKeys.forEach(key -> parts.add("CONSTRAINT " + key.name() + " FOREIGN KEY (" + key.column()
                    + ") REFERENCES " + key.targetTable() + "(" + key.targetColumn() + ") ON DELETE " + key.deleteAction() + " ON UPDATE RESTRICT"));
            parts.addAll(extras);
            return "CREATE TABLE IF NOT EXISTS " + name + " (\n    " + String.join(",\n    ", parts)
                    + "\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci";
        }
    }
    private BranchSchema() { }
    static final String POLICY_DDL = "ALTER TABLE operations_policy ADD COLUMN max_active_orders INT NOT NULL DEFAULT 15";
    static final Column POLICY_COLUMN = integer("max_active_orders", " DEFAULT 15");
    static final List<Table> TABLES = List.of(
        table("branch", List.of(id(), text("code", 10), text("name", 100), text("province_code", 10),
                text("hub_address", 255), text("hub_area_code", 40), bool("active", true), version(), time("created_at", false), time("updated_at", false)),
                List.of(unique("uk_branch_code", "code")), List.of()),
        table("province_area", List.of(id(), text("province_code", 10), text("area_code", 40), text("area_name", 100),
                reference("branch_id", false), decimal("lat", 9, 6), decimal("lng", 9, 6)),
                List.of(unique("uk_area_code", "area_code")), List.of(fk("fk_area_branch", "branch_id", "branch")),
                "INDEX idx_area_branch (branch_id)", "CONSTRAINT ck_area_coordinates CHECK (lat BETWEEN -90 AND 90 AND lng BETWEEN -180 AND 180)"),
        new Table("area_neighbor", List.of(text("area_code", 40), text("neighbor_code", 40)), List.of("area_code", "neighbor_code"),
                List.of(), List.of(areaFk("fk_neighbor_area", "area_code"), areaFk("fk_neighbor_other", "neighbor_code")),
                List.of("INDEX idx_neighbor_reverse (neighbor_code)", "CONSTRAINT ck_area_neighbor_distinct CHECK (area_code <> neighbor_code)")),
        table("staff_branch", List.of(id(), reference("user_id", false), reference("branch_id", false), reference("assigned_by", false), time("assigned_at", false)),
                List.of(unique("uk_staff_branch_user", "user_id")),
                List.of(cascadeFk("fk_staff_branch_user", "user_id", "app_user"), fk("fk_staff_branch_branch", "branch_id", "branch"),
                        fk("fk_staff_branch_actor", "assigned_by", "app_user")), "INDEX idx_staff_branch (branch_id)"),
        table("shipper_zone", List.of(id(), reference("shipper_id", false), text("area_code", 40), bool("is_primary", false)),
                List.of(unique("uk_shipper_zone", "shipper_id", "area_code")),
                List.of(cascadeFk("fk_zone_shipper", "shipper_id", "app_user"), areaFk("fk_zone_area", "area_code"))),
        table("order_route", List.of(id(), reference("order_id", false), reference("origin_branch_id", false), reference("dest_branch_id", false),
                text("pickup_area_code", 40), text("delivery_area_code", 40), enumeration("stage", "PICKUP", "ORIGIN_HUB", "LINEHAUL", "DEST_HUB", "LAST_MILE"),
                reference("pickup_shipper_id", true), reference("delivery_shipper_id", true), version()),
                List.of(unique("uk_order_route_order", "order_id")),
                List.of(fk("fk_route_order", "order_id", "shipment_order"), fk("fk_route_origin", "origin_branch_id", "branch"),
                        fk("fk_route_dest", "dest_branch_id", "branch"), areaFk("fk_route_pickup_area", "pickup_area_code"),
                        areaFk("fk_route_delivery_area", "delivery_area_code"), fk("fk_route_pickup_shipper", "pickup_shipper_id", "app_user"),
                        fk("fk_route_delivery_shipper", "delivery_shipper_id", "app_user")),
                "INDEX idx_route_origin_stage (origin_branch_id, stage)", "INDEX idx_route_dest_stage (dest_branch_id, stage)"),
        table("linehaul_trip", List.of(id(), text("code", 40), reference("from_branch_id", false), reference("to_branch_id", false),
                enumeration("status", "LOADING", "DEPARTED", "ARRIVED"), text("vehicle_plate", 20), text("driver_name", 100), text("driver_phone", 20),
                text("seal_code", 50, true), integer("item_count", " DEFAULT 0"), reference("departed_by", true), time("departed_at", true),
                reference("arrived_by", true), time("arrived_at", true), text("arrival_note", 500, true), version()),
                List.of(unique("uk_linehaul_code", "code")),
                List.of(fk("fk_trip_from", "from_branch_id", "branch"), fk("fk_trip_to", "to_branch_id", "branch"),
                        fk("fk_trip_departed_by", "departed_by", "app_user"), fk("fk_trip_arrived_by", "arrived_by", "app_user")),
                "INDEX idx_trip_from_status (from_branch_id, status)", "INDEX idx_trip_to_status (to_branch_id, status)",
                "CONSTRAINT ck_trip_route_count CHECK (from_branch_id <> to_branch_id AND item_count >= 0)"),
        table("linehaul_item", List.of(id(), reference("trip_id", false), reference("order_id", false), bool("received", false)),
                List.of(unique("uk_linehaul_item", "trip_id", "order_id")),
                List.of(fk("fk_item_trip", "trip_id", "linehaul_trip"), fk("fk_item_order", "order_id", "shipment_order")), "INDEX idx_linehaul_item_order (order_id)"),
        table("route_tariff", List.of(id(), reference("origin_branch_id", false), reference("dest_branch_id", false),
                enumeration("service_type", "STANDARD", "EXPRESS", "SUPER_EXPRESS"), decimal("surcharge", 12, 0), text("lead_time_text", 100), version()),
                List.of(unique("uk_route_tariff", "origin_branch_id", "dest_branch_id", "service_type")),
                List.of(fk("fk_tariff_origin", "origin_branch_id", "branch"), fk("fk_tariff_dest", "dest_branch_id", "branch")),
                "CONSTRAINT ck_route_tariff CHECK (surcharge >= 0 AND (origin_branch_id = dest_branch_id OR service_type <> 'SUPER_EXPRESS'))"),
        table("saved_address_area", List.of(id(), reference("saved_address_id", false), text("province_code", 10), text("area_code", 40)),
                List.of(unique("uk_saved_address_area", "saved_address_id")),
                List.of(cascadeFk("fk_saved_area_address", "saved_address_id", "saved_address"), areaFk("fk_saved_area_area", "area_code"))),
        table("draft_order_area", List.of(id(), reference("draft_order_id", false), text("pickup_province_code", 10), text("pickup_area_code", 40),
                text("delivery_province_code", 10), text("delivery_area_code", 40)),
                List.of(unique("uk_draft_order_area", "draft_order_id")),
                List.of(cascadeFk("fk_draft_area_order", "draft_order_id", "draft_order"), areaFk("fk_draft_pickup_area", "pickup_area_code"),
                        areaFk("fk_draft_delivery_area", "delivery_area_code")))
    );

    private static Table table(String name, List<Column> columns, List<UniqueKey> unique, List<ForeignKey> foreign, String... extras) {
        return new Table(name, columns, List.of("id"), unique, foreign, List.of(extras));
    }
    private static Column id() { return new Column("id", "BIGINT NOT NULL AUTO_INCREMENT", Types.BIGINT, 0, 0, false, true); }
    private static Column version() { return new Column("revision", "BIGINT NOT NULL DEFAULT 0", Types.BIGINT, 0, 0, false, false); }
    private static Column reference(String name, boolean nullable) { return new Column(name, "BIGINT " + (nullable ? "NULL" : "NOT NULL"), Types.BIGINT, 0, 0, nullable, false); }
    private static Column text(String name, int size) { return text(name, size, false); }
    private static Column text(String name, int size, boolean nullable) { return new Column(name, "VARCHAR(" + size + ") " + (nullable ? "NULL" : "NOT NULL"), Types.VARCHAR, size, 0, nullable, false); }
    private static Column integer(String name, String suffix) { return new Column(name, "INT NOT NULL" + suffix, Types.INTEGER, 0, 0, false, false); }
    private static Column decimal(String name, int precision, int scale) { return new Column(name, "DECIMAL(" + precision + "," + scale + ") NOT NULL", Types.DECIMAL, precision, scale, false, false); }
    private static Column bool(String name, boolean initial) { return new Column(name, "BIT NOT NULL DEFAULT " + (initial ? "1" : "0"), Types.BIT, 0, 0, false, false); }
    private static Column time(String name, boolean nullable) { return new Column(name, "DATETIME(6) " + (nullable ? "NULL" : "NOT NULL"), Types.TIMESTAMP, 0, 6, nullable, false); }
    private static Column enumeration(String name, String... values) { return new Column(name, "ENUM('" + String.join("','", values) + "') NOT NULL", Types.VARCHAR, 0, 0, false, false); }
    private static ForeignKey fk(String name, String column, String table) { return new ForeignKey(name, column, table, "id", false); }
    private static ForeignKey cascadeFk(String name, String column, String table) { return new ForeignKey(name, column, table, "id", true); }
    private static ForeignKey areaFk(String name, String column) { return new ForeignKey(name, column, "province_area", "area_code", false); }
    private static UniqueKey unique(String name, String... columns) { return new UniqueKey(name, List.of(columns)); }
}
