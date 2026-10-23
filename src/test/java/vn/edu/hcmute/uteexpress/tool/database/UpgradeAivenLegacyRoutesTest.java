package vn.edu.hcmute.uteexpress.tool.database;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** B10: ánh xạ đơn cũ sang tuyến chi nhánh. Không kết nối Aiven: dữ liệu cố định và JDBC giả. */
class UpgradeAivenLegacyRoutesTest {
    static final UpgradeAivenLegacyRoutes.Catalog CATALOG = new UpgradeAivenLegacyRoutes.Catalog(Map.of(
            "SGN", new UpgradeAivenLegacyRoutes.BranchInfo(1, "SGN", "SGN-THUDUC", 10.849, 106.753),
            "HAN", new UpgradeAivenLegacyRoutes.BranchInfo(2, "HAN", "HAN-CAUGIAY", 21.036, 105.790),
            "DAD", new UpgradeAivenLegacyRoutes.BranchInfo(3, "DAD", "DAD-HAICHAU", 16.047, 108.221)), List.of(
            new UpgradeAivenLegacyRoutes.AreaInfo("SGN-Q1", "Khu vực 1", "SGN"),
            new UpgradeAivenLegacyRoutes.AreaInfo("SGN-Q10", "Khu vực 10", "SGN"),
            new UpgradeAivenLegacyRoutes.AreaInfo("SGN-THUDUC", "Thủ Đức", "SGN"),
            new UpgradeAivenLegacyRoutes.AreaInfo("SGN-BINHTHANH", "Bình Thạnh", "SGN"),
            new UpgradeAivenLegacyRoutes.AreaInfo("HAN-CAUGIAY", "Cầu Giấy", "HAN"),
            new UpgradeAivenLegacyRoutes.AreaInfo("HAN-BADINH", "Ba Đình", "HAN"),
            new UpgradeAivenLegacyRoutes.AreaInfo("DAD-HAICHAU", "Hải Châu", "DAD")));

    static UpgradeAivenLegacyRoutes.Plan plan(String status, String from, String to) {
        return UpgradeAivenLegacyRoutes.plan(new UpgradeAivenLegacyRoutes.LegacyOrder(7, "UTE7", status, from, to, 5L), CATALOG);
    }

    @Test void addressKeywordsMapToBranchAndArea() {
        var sgn = UpgradeAivenLegacyRoutes.locate("268 Lý Thường Kiệt, Quận 10, TP.HCM", CATALOG);
        assertEquals("SGN", sgn.branchCode()); assertEquals("SGN-Q10", sgn.areaCode());
        assertEquals("SGN-Q1", UpgradeAivenLegacyRoutes.locate("12 Nguyễn Huệ, Q.1, Hồ Chí Minh", CATALOG).areaCode(), "Quận 1 không nhầm Quận 10, Nguyễn Huệ không nhầm Huế");
        assertEquals("SGN-THUDUC", UpgradeAivenLegacyRoutes.locate("1 Võ Văn Ngân, Thủ Đức", CATALOG).areaCode());
        var han = UpgradeAivenLegacyRoutes.locate("5 Kim Mã, Ba Đình, Hà Nội", CATALOG);
        assertEquals("HAN", han.branchCode()); assertEquals("HAN-BADINH", han.areaCode());
        var guessed = UpgradeAivenLegacyRoutes.locate("99 đường không rõ quận, Đà Nẵng", CATALOG);
        assertEquals("DAD-HAICHAU", guessed.areaCode(), "Không khớp quận thì dùng khu vực kho"); assertTrue(guessed.areaGuessed());
        var outside = UpgradeAivenLegacyRoutes.locate("10 Nguyễn Huệ, Ninh Kiều, Cần Thơ", CATALOG);
        assertFalse(outside.inRegion()); assertEquals("Cần Thơ", outside.outsideName());
    }

    @Test void stageAndShipperFollowTheRules() {
        var waiting = plan("PENDING_PICKUP", "1 Võ Văn Ngân, Thủ Đức, TP.HCM", "Bình Thạnh, TP.HCM");
        assertTrue(waiting.insert()); assertEquals("PICKUP", waiting.stage()); assertEquals("SGN", waiting.originCode()); assertEquals("SGN", waiting.destCode());
        assertEquals("LAST_MILE", plan("IN_TRANSIT", "Quận 1, TP.HCM", "Quận 10, TP.HCM").stage());
        assertEquals("LAST_MILE", plan("FAILED", "Quận 1, TP.HCM", "Quận 10, TP.HCM").stage());
        var finishedInter = plan("DELIVERED", "Quận 1, TP.HCM", "Cầu Giấy, Hà Nội");
        assertTrue(finishedInter.insert()); assertEquals("HAN", finishedInter.destCode()); assertEquals("LAST_MILE", finishedInter.stage());
    }

    @Test void runningInterprovincialAndRunningOutsideAreListedForManualHandling() {
        var inter = plan("PICKED_UP", "Quận 1, TP.HCM", "Hải Châu, Đà Nẵng");
        assertFalse(inter.insert()); assertTrue(inter.note().contains("liên tỉnh SGN → DAD đang chạy"));
        var outside = plan("IN_TRANSIT", "Quận 1, TP.HCM", "Ninh Kiều, Cần Thơ");
        assertFalse(outside.insert()); assertTrue(outside.note().contains("ngoài 3 tỉnh"));
        assertFalse(plan("PENDING_PICKUP", "Quận 1, TP.HCM", "Nha Trang, Khánh Hòa").insert());
    }

    @Test void finishedOutsideOrdersGetNearestBranch() {
        assertEquals("SGN", plan("DELIVERED", "Quận 1, TP.HCM", "Ninh Kiều, Cần Thơ").destCode());
        assertEquals("DAD", plan("RETURNED", "Quận 1, TP.HCM", "Lê Lợi, Huế").destCode());
        assertEquals("HAN", plan("CANCELLED", "Cầu Giấy, Hà Nội", "Lê Chân, Hải Phòng").destCode());
        var unknown = plan("DELIVERED", "Quận 1, TP.HCM", "địa chỉ không rõ tỉnh");
        assertTrue(unknown.insert()); assertEquals("SGN", unknown.destCode()); assertEquals("SGN-THUDUC", unknown.deliveryArea());
        assertTrue(unknown.note().contains("không nhận diện được tỉnh"));
    }

    @Test void applyOnlyInsertsWhereNotExistsAndRerunAddsNothing() throws Exception {
        var routed = new HashSet<Long>();
        var connection = fakeDatabase(routed, List.of(
                new Object[]{11L, "UTE11", "DELIVERED", "Quận 1, TP.HCM", "Quận 10, TP.HCM", 5L},
                new Object[]{12L, "UTE12", "PICKED_UP", "Quận 1, TP.HCM", "Cầu Giấy, Hà Nội", 5L},
                new Object[]{13L, "UTE13", "PENDING_PICKUP", "Thủ Đức, TP.HCM", "Bình Thạnh, TP.HCM", null}));
        assertEquals(2, UpgradeAivenLegacyRoutes.upgrade(connection));
        assertEquals(Set.of(11L, 13L), routed, "Liên tỉnh đang chạy không được thêm");
        assertEquals(0, UpgradeAivenLegacyRoutes.upgrade(connection), "Chạy lại không thêm trùng");
        var output = new ByteArrayOutputStream();
        assertEquals(0, UpgradeAivenLegacyRoutes.check(connection, new PrintStream(output, true, StandardCharsets.UTF_8)));
        String report = output.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("UTE12 | PICKED_UP") && report.contains("XỬ LÝ TAY"));
        assertFalse(UpgradeAivenLegacyRoutes.INSERT_SQL.matches("(?is).*\\b(UPDATE|DELETE|DROP|TRUNCATE)\\b.*"));
        assertTrue(UpgradeAivenLegacyRoutes.INSERT_SQL.contains("WHERE NOT EXISTS"));
    }

    @Test void wrongDatabaseFailsBeforeAnyWrite() throws Exception {
        var connection = fakeDatabase(new HashSet<>(), List.of());
        when(connection.getMetaData().getDatabaseProductName()).thenReturn("PostgreSQL");
        assertThrows(IllegalArgumentException.class, () -> UpgradeAivenLegacyRoutes.upgrade(connection));
        verify(connection, never()).prepareStatement(UpgradeAivenLegacyRoutes.INSERT_SQL);
    }

    /** JDBC giả: bảng đơn cũ, chi nhánh, khu vực; câu INSERT ghi nhận order_id vào tập "đã có tuyến". */
    private static Connection fakeDatabase(Set<Long> routed, List<Object[]> legacy) throws Exception {
        var connection = mock(Connection.class); var metadata = mock(DatabaseMetaData.class); var statement = mock(Statement.class);
        when(connection.getMetaData()).thenReturn(metadata); when(connection.getCatalog()).thenReturn("test_only");
        when(connection.getAutoCommit()).thenReturn(true);
        when(metadata.getDatabaseProductName()).thenReturn("MySQL"); when(metadata.getDatabaseMajorVersion()).thenReturn(8);
        when(metadata.getTables(anyString(), isNull(), anyString(), any())).thenAnswer(call -> {
            var row = mock(ResultSet.class); when(row.next()).thenReturn(true, false); when(row.getString("TABLE_NAME")).thenReturn(call.getArgument(2)); return row; });
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(startsWith("SELECT GET_LOCK"))).thenAnswer(call -> {
            var row = mock(ResultSet.class); when(row.next()).thenReturn(true); when(row.getInt(1)).thenReturn(1); return row; });
        when(connection.prepareStatement(anyString())).thenAnswer(call -> {
            String sql = call.getArgument(0);
            var prepared = mock(PreparedStatement.class);
            if (sql.equals(UpgradeAivenLegacyRoutes.BRANCH_QUERY)) when(prepared.executeQuery()).thenAnswer(q -> rows(List.of(
                    new Object[]{1L, "SGN", "SGN-THUDUC", 10.849, 106.753}, new Object[]{2L, "HAN", "HAN-CAUGIAY", 21.036, 105.790},
                    new Object[]{3L, "DAD", "DAD-HAICHAU", 16.047, 108.221})));
            else if (sql.equals(UpgradeAivenLegacyRoutes.AREA_QUERY)) when(prepared.executeQuery()).thenAnswer(q -> rows(CATALOG.areas().stream()
                    .map(a -> new Object[]{a.code(), a.name(), a.branchCode()}).toList()));
            else if (sql.equals(UpgradeAivenLegacyRoutes.LEGACY_QUERY)) when(prepared.executeQuery()).thenAnswer(q ->
                    rows(legacy.stream().filter(r -> !routed.contains((Long) r[0])).toList()));
            else if (sql.equals(UpgradeAivenLegacyRoutes.INSERT_SQL)) {
                var orderId = new long[1];
                doAnswer(a -> { if ((int) a.getArgument(0) == 1) orderId[0] = a.getArgument(1); return null; }).when(prepared).setLong(anyInt(), anyLong());
                when(prepared.executeUpdate()).thenAnswer(u -> routed.add(orderId[0]) ? 1 : 0);
            } else fail("Câu SQL ngoài dự kiến: " + sql);
            return prepared;
        });
        return connection;
    }

    private static ResultSet rows(List<Object[]> data) throws SQLException {
        var rows = mock(ResultSet.class); var cursor = new int[]{-1}; var lastNull = new boolean[1];
        when(rows.next()).thenAnswer(a -> ++cursor[0] < data.size());
        when(rows.getString(anyInt())).thenAnswer(a -> { Object v = data.get(cursor[0])[(int) a.getArgument(0) - 1]; lastNull[0] = v == null; return v == null ? null : v.toString(); });
        when(rows.getLong(anyInt())).thenAnswer(a -> { Object v = data.get(cursor[0])[(int) a.getArgument(0) - 1]; lastNull[0] = v == null; return v == null ? 0L : ((Number) v).longValue(); });
        when(rows.getDouble(anyInt())).thenAnswer(a -> { Object v = data.get(cursor[0])[(int) a.getArgument(0) - 1]; lastNull[0] = v == null; return v == null ? 0d : ((Number) v).doubleValue(); });
        when(rows.wasNull()).thenAnswer(a -> lastNull[0]);
        return rows;
    }
}
