package vn.edu.hcmute.uteexpress.tool.database;

import java.io.PrintStream;
import java.sql.*;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Chạy tay (B10): gắn tuyến chi nhánh cho đơn cũ tạo trước khi có chi nhánh.
 * --check chỉ đọc và in bảng ánh xạ cho người duyệt; --apply chỉ INSERT ... WHERE NOT EXISTS vào order_route,
 * không UPDATE/DELETE dữ liệu có sẵn; chạy lại nhiều lần không lỗi và không thêm trùng.
 *
 * Quy tắc: địa chỉ có "TP.HCM / Hồ Chí Minh / Thủ Đức / Quận x" → SGN, "Hà Nội" → HAN, "Đà Nẵng" → DAD
 * (lấy từ khóa tỉnh xuất hiện sau cùng trong địa chỉ). Khu vực theo tên quận nếu khớp danh mục, không khớp thì khu vực kho.
 * Giai đoạn: chờ lấy → PICKUP, còn lại → LAST_MILE. Shipper lấy = shipper giao = shipper đang giữ đơn.
 * Liên tỉnh đang chạy và đơn đang chạy ngoài 3 tỉnh: không thêm, chỉ liệt kê để Manager xử lý tay.
 * Đơn ngoài 3 tỉnh đã kết thúc: gán chi nhánh gần nhất (theo tọa độ tỉnh xấp xỉ) để báo cáo không lỗi.
 */
public final class UpgradeAivenLegacyRoutes {
    private static final String LOCK_NAME = "uteexpress-schema-initialize";
    static final String LEGACY_QUERY = "SELECT o.id, o.tracking_code, o.status, o.sender_address, o.receiver_address, o.shipper_id"
            + " FROM shipment_order o WHERE NOT EXISTS (SELECT 1 FROM order_route r WHERE r.order_id = o.id) ORDER BY o.id";
    static final String BRANCH_QUERY = "SELECT b.id, b.code, b.hub_area_code, a.lat, a.lng FROM branch b"
            + " JOIN province_area a ON a.area_code = b.hub_area_code ORDER BY b.id";
    static final String AREA_QUERY = "SELECT a.area_code, a.area_name, b.code FROM province_area a JOIN branch b ON b.id = a.branch_id ORDER BY a.area_code";
    static final String INSERT_SQL = "INSERT INTO order_route (order_id, origin_branch_id, dest_branch_id, pickup_area_code, delivery_area_code,"
            + " stage, pickup_shipper_id, delivery_shipper_id, revision) SELECT ?, ?, ?, ?, ?, ?, ?, ?, 0 FROM DUAL"
            + " WHERE NOT EXISTS (SELECT 1 FROM order_route WHERE order_id = ?)";
    private static final Set<String> FINISHED = Set.of("DELIVERED", "CANCELLED", "RETURNED");

    record LegacyOrder(long id, String trackingCode, String status, String senderAddress, String receiverAddress, Long shipperId) { }
    record BranchInfo(long id, String code, String hubAreaCode, double lat, double lng) { }
    record AreaInfo(String code, String name, String branchCode) { }
    /** Kết quả nhận diện một địa chỉ: chi nhánh + khu vực, hoặc tỉnh ngoài vùng (có/không có tọa độ). */
    record Location(String branchCode, String areaCode, String outsideName, double[] outsideCoordinates, boolean areaGuessed) {
        boolean inRegion() { return branchCode != null; }
    }
    record Plan(LegacyOrder order, boolean insert, String originCode, String destCode, String pickupArea, String deliveryArea,
                String stage, String note) { }
    record Catalog(Map<String, BranchInfo> branches, List<AreaInfo> areas) { }

    private UpgradeAivenLegacyRoutes() { }

    public static void main(String[] args) {
        if (args.length != 1 || !Set.of("--print-schema", "--check", "--apply").contains(args[0])) {
            System.err.println("Dùng --print-schema (không kết nối), --check (chỉ đọc, in bảng ánh xạ) hoặc --apply (người vận hành tự chạy).");
            System.exit(1); return;
        }
        try {
            if (args[0].equals("--print-schema")) { printSchema(System.out); return; }
            try (var connection = AivenConnectionSettings.open()) {
                if (args[0].equals("--apply")) {
                    int inserted = upgrade(connection);
                    System.out.println("Đã thêm tuyến cho " + inserted + " đơn cũ. Không sửa/xóa dữ liệu có sẵn.");
                }
                int pending = check(connection, System.out);
                if (pending > 0) System.exit(2);
            }
        } catch (Exception ex) {
            System.err.println("Chưa hoàn tất kiểm tra/migration. Không in thông tin kết nối.");
            System.err.println("Exception=" + ex.getClass().getName() + (ex instanceof SQLException sql
                    ? "; SQLState=" + sql.getSQLState() + "; errorCode=" + sql.getErrorCode() : "") + "; message=" + ex.getMessage());
            System.exit(1);
        }
    }

    public static void printSchema(PrintStream output) {
        output.println("-- Migration chạy tay: gắn tuyến chi nhánh cho đơn cũ. Không đổi cấu trúc bảng, chỉ thêm dòng vào order_route.");
        output.println("-- Mỗi đơn một câu, chạy lại không thêm trùng:");
        output.println(INSERT_SQL + ";");
    }

    /** Chỉ đọc. In bảng ánh xạ; trả về số đơn còn có thể thêm tuyến (0 = đầy đủ, chỉ còn đơn phải xử lý tay). */
    public static int check(Connection connection, PrintStream output) throws SQLException {
        connection.setReadOnly(true);
        validateTarget(connection);
        var catalog = loadCatalog(connection);
        var plans = loadLegacy(connection).stream().map(order -> plan(order, catalog)).toList();
        output.println("Kiểm tra chỉ đọc: KHÔNG thêm tuyến, KHÔNG ghi dữ liệu. Đơn chưa có tuyến: " + plans.size());
        output.println("Mã vận đơn | Trạng thái | Gửi | Nhận | Quyết định");
        long pending = 0, manual = 0;
        for (var plan : plans) {
            output.println(plan.order().trackingCode() + " | " + plan.order().status() + " | "
                    + side(plan.originCode(), plan.pickupArea()) + " | " + side(plan.destCode(), plan.deliveryArea()) + " | "
                    + (plan.insert() ? "THÊM tuyến, giai đoạn " + plan.stage() : "XỬ LÝ TAY") + (plan.note() == null ? "" : " (" + plan.note() + ")"));
            if (plan.insert()) pending++; else manual++;
        }
        output.println("Còn có thể thêm tuyến: " + pending + "; phải xử lý tay: " + manual + ".");
        if (manual > 0) output.println("Đơn xử lý tay giữ nguyên, vẫn hiện cho mọi chi nhánh (quy tắc chuyển tiếp) để Manager xử lý.");
        output.println(pending == 0 ? "Dữ liệu cũ đã đầy đủ tuyến (trừ đơn phải xử lý tay)." : "Chưa đầy đủ. Dừng tại đây, người vận hành review bảng trên rồi tự chạy --apply.");
        return (int) pending;
    }

    /** Thêm tuyến cho các đơn đủ điều kiện; trả về số dòng thật sự thêm. */
    public static int upgrade(Connection connection) throws SQLException {
        if (!connection.getAutoCommit() || connection.isReadOnly()) throw new IllegalArgumentException("Migration cần kết nối ghi riêng.");
        validateTarget(connection);
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT GET_LOCK('" + LOCK_NAME + "', 10)")) {
            if (!rows.next() || rows.getInt(1) != 1) throw new IllegalStateException("Schema đang được nâng cấp bởi tiến trình khác; chưa ghi dữ liệu.");
        }
        int inserted = 0;
        try {
            var catalog = loadCatalog(connection);
            for (var order : loadLegacy(connection)) {
                var plan = plan(order, catalog);
                if (!plan.insert()) continue;
                // Mỗi câu một timeout riêng: qua mạng tới Aiven mỗi câu mất gần 1 giây.
                try (var statement = connection.prepareStatement(INSERT_SQL)) {
                    statement.setQueryTimeout(60);
                    statement.setLong(1, order.id());
                    statement.setLong(2, catalog.branches().get(plan.originCode()).id());
                    statement.setLong(3, catalog.branches().get(plan.destCode()).id());
                    statement.setString(4, plan.pickupArea());
                    statement.setString(5, plan.deliveryArea());
                    statement.setString(6, plan.stage());
                    if (order.shipperId() == null) { statement.setNull(7, Types.BIGINT); statement.setNull(8, Types.BIGINT); }
                    else { statement.setLong(7, order.shipperId()); statement.setLong(8, order.shipperId()); }
                    statement.setLong(9, order.id());
                    inserted += statement.executeUpdate();
                }
            }
        } finally {
            try (var statement = connection.createStatement()) { statement.execute("SELECT RELEASE_LOCK('" + LOCK_NAME + "')"); }
        }
        return inserted;
    }

    /** Quyết định cho một đơn cũ (thuần, không truy cập DB). */
    static Plan plan(LegacyOrder order, Catalog catalog) {
        var pickup = locate(order.senderAddress(), catalog);
        var delivery = locate(order.receiverAddress(), catalog);
        boolean finished = FINISHED.contains(order.status());
        String stage = "PENDING_PICKUP".equals(order.status()) ? "PICKUP" : "LAST_MILE";
        if (pickup.inRegion() && delivery.inRegion()) {
            boolean interprovincial = !pickup.branchCode().equals(delivery.branchCode());
            if (interprovincial && !finished)
                return manual(order, pickup, delivery, "liên tỉnh " + pickup.branchCode() + " → " + delivery.branchCode() + " đang chạy");
            return new Plan(order, true, pickup.branchCode(), delivery.branchCode(), pickup.areaCode(), delivery.areaCode(), stage,
                    guessNote(pickup, delivery));
        }
        if (!finished) return manual(order, pickup, delivery, "đang chạy ngoài 3 tỉnh phục vụ");
        var from = pickup.inRegion() ? pickup : nearest(pickup, catalog);
        var to = delivery.inRegion() ? delivery : nearest(delivery, catalog);
        return new Plan(order, true, from.branchCode(), to.branchCode(), from.areaCode(), to.areaCode(), stage,
                "đơn đã kết thúc ngoài vùng: gán chi nhánh gần nhất" + (pickup.inRegion() ? "" : " cho bên gửi (" + label(pickup) + ")")
                        + (delivery.inRegion() ? "" : " cho bên nhận (" + label(delivery) + ")"));
    }

    private static Plan manual(LegacyOrder order, Location pickup, Location delivery, String reason) {
        return new Plan(order, false, pickup.inRegion() ? pickup.branchCode() : null, delivery.inRegion() ? delivery.branchCode() : null,
                pickup.inRegion() ? pickup.areaCode() : label(pickup), delivery.inRegion() ? delivery.areaCode() : label(delivery), null, reason);
    }

    private static String guessNote(Location pickup, Location delivery) {
        if (!pickup.areaGuessed() && !delivery.areaGuessed()) return null;
        return "không khớp tên quận nên dùng khu vực kho" + (pickup.areaGuessed() ? " (bên gửi)" : "") + (delivery.areaGuessed() ? " (bên nhận)" : "");
    }

    private static String side(String branch, String area) { return branch == null ? "Ngoài vùng: " + area : branch + "/" + area; }

    private static String label(Location location) {
        return location.inRegion() ? location.branchCode() : location.outsideName() == null ? "không nhận diện được tỉnh" : location.outsideName();
    }

    /** Chi nhánh gần nhất theo tọa độ tỉnh xấp xỉ; không nhận diện được tỉnh thì tạm gán SGN và ghi rõ trong báo cáo. */
    private static Location nearest(Location outside, Catalog catalog) {
        BranchInfo best = catalog.branches().get("SGN");
        if (outside.outsideCoordinates() != null) {
            double bestKm = Double.MAX_VALUE;
            for (var branch : catalog.branches().values()) {
                double km = kilometers(outside.outsideCoordinates()[0], outside.outsideCoordinates()[1], branch.lat(), branch.lng());
                if (km < bestKm) { bestKm = km; best = branch; }
            }
        }
        if (best == null) throw new IllegalStateException("Thiếu chi nhánh SGN/HAN/DAD; hãy chạy UpgradeAivenBranches trước.");
        return new Location(best.code(), best.hubAreaCode(), null, null, true);
    }

    /** Nhận diện chi nhánh và khu vực từ địa chỉ chữ tự do. */
    static Location locate(String address, Catalog catalog) {
        String text = " " + fold(address) + " ";
        String branch = null; String outside = null; double[] coordinates = null; int best = -1;
        for (var keyword : PROVINCE_KEYWORDS) {
            var matcher = keyword.pattern().matcher(text);
            while (matcher.find()) if (matcher.end() > best) {
                best = matcher.end(); branch = keyword.branchCode(); outside = keyword.outsideName(); coordinates = keyword.coordinates();
            }
        }
        if (branch == null && outside == null) {
            // Không ghi tỉnh: thử tên khu vực trong danh mục (ví dụ "Bình Thạnh", "Hải Châu").
            var area = matchArea(text, catalog.areas(), null);
            if (area != null) return new Location(area.branchCode(), area.code(), null, null, false);
            return new Location(null, null, null, null, false);
        }
        if (branch == null) return new Location(null, null, outside, coordinates, false);
        var area = matchArea(text, catalog.areas(), branch);
        if (area != null) return new Location(branch, area.code(), null, null, false);
        var info = catalog.branches().get(branch);
        if (info == null) throw new IllegalStateException("Thiếu chi nhánh " + branch + "; hãy chạy UpgradeAivenBranches trước.");
        return new Location(branch, info.hubAreaCode(), null, null, true);
    }

    private static final Pattern DISTRICT_NUMBER = Pattern.compile("[^a-z0-9](?:quan|q)\\s*0?(\\d{1,2})(?![0-9])");

    private static AreaInfo matchArea(String text, List<AreaInfo> areas, String branch) {
        if (branch == null || branch.equals("SGN")) {
            var number = DISTRICT_NUMBER.matcher(text);
            AreaInfo found = null; int last = -1;
            while (number.find()) {
                String code = "SGN-Q" + Integer.parseInt(number.group(1));
                for (var area : areas) if (area.code().equals(code) && number.end() > last) { found = area; last = number.end(); }
            }
            if (found != null) return found;
        }
        AreaInfo best = null;
        for (var area : areas) {
            if (branch != null && !area.branchCode().equals(branch)) continue;
            String name = fold(area.name());
            if (name.startsWith("khu vuc ")) continue; // tên số quận đã xử lý ở trên
            if (text.contains(" " + name + " ") && (best == null || name.length() > fold(best.name()).length())) best = area;
        }
        return best;
    }

    static String fold(String value) {
        if (value == null) return "";
        String noMarks = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D');
        return noMarks.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    record ProvinceKeyword(Pattern pattern, String branchCode, String outsideName, double[] coordinates) { }
    private static ProvinceKeyword branch(String code, String... words) { return new ProvinceKeyword(words(words), code, null, null); }
    private static ProvinceKeyword outside(String name, double lat, double lng, String... words) {
        return new ProvinceKeyword(words(words), null, name, new double[]{lat, lng});
    }
    private static Pattern words(String... words) {
        return Pattern.compile(" (?:" + String.join("|", Arrays.stream(words).map(Pattern::quote).toList()) + ")(?= )");
    }
    /** Từ khóa tỉnh (đã bỏ dấu). Tỉnh ngoài vùng kèm tọa độ trung tâm xấp xỉ để chọn chi nhánh gần nhất. */
    static final List<ProvinceKeyword> PROVINCE_KEYWORDS = List.of(
            branch("SGN", "ho chi minh", "tp hcm", "tphcm", "hcm", "sai gon", "thu duc"),
            branch("HAN", "ha noi"),
            branch("DAD", "da nang"),
            outside("Thừa Thiên Huế", 16.46, 107.59, "hue", "thua thien"),
            outside("Quảng Nam", 15.57, 108.47, "quang nam", "hoi an", "tam ky"),
            outside("Quảng Ngãi", 15.12, 108.80, "quang ngai"),
            outside("Quảng Trị", 16.75, 107.19, "quang tri"),
            outside("Quảng Bình", 17.47, 106.62, "quang binh", "dong hoi"),
            outside("Kon Tum", 14.35, 108.00, "kon tum"),
            outside("Gia Lai", 13.98, 108.00, "gia lai", "pleiku"),
            outside("Bình Định", 13.78, 109.22, "binh dinh", "quy nhon"),
            outside("Phú Yên", 13.09, 109.30, "phu yen", "tuy hoa"),
            outside("Khánh Hòa", 12.24, 109.19, "khanh hoa", "nha trang", "cam ranh"),
            outside("Đắk Lắk", 12.67, 108.04, "dak lak", "dac lac", "buon ma thuot"),
            outside("Đắk Nông", 12.00, 107.69, "dak nong"),
            outside("Lâm Đồng", 11.94, 108.46, "lam dong", "da lat"),
            outside("Bình Thuận", 10.93, 108.10, "binh thuan", "phan thiet"),
            outside("Ninh Thuận", 11.56, 108.99, "ninh thuan", "phan rang"),
            outside("Bình Dương", 10.98, 106.65, "binh duong", "thu dau mot"),
            outside("Đồng Nai", 10.95, 106.82, "dong nai", "bien hoa"),
            outside("Bà Rịa - Vũng Tàu", 10.35, 107.08, "vung tau", "ba ria"),
            outside("Tây Ninh", 11.31, 106.10, "tay ninh"),
            outside("Bình Phước", 11.75, 106.72, "binh phuoc"),
            outside("Long An", 10.54, 106.41, "long an", "tan an"),
            outside("Tiền Giang", 10.36, 106.36, "tien giang", "my tho"),
            outside("Bến Tre", 10.24, 106.37, "ben tre"),
            outside("Vĩnh Long", 10.25, 105.97, "vinh long"),
            outside("Trà Vinh", 9.93, 106.34, "tra vinh"),
            outside("Đồng Tháp", 10.46, 105.63, "dong thap", "cao lanh"),
            outside("Cần Thơ", 10.03, 105.78, "can tho"),
            outside("Hậu Giang", 9.78, 105.47, "hau giang"),
            outside("Sóc Trăng", 9.60, 105.97, "soc trang"),
            outside("Bạc Liêu", 9.29, 105.72, "bac lieu"),
            outside("Cà Mau", 9.18, 105.15, "ca mau"),
            outside("An Giang", 10.38, 105.43, "an giang", "long xuyen", "chau doc"),
            outside("Kiên Giang", 10.01, 105.08, "kien giang", "rach gia", "phu quoc"),
            outside("Hải Phòng", 20.84, 106.69, "hai phong"),
            outside("Quảng Ninh", 20.95, 107.08, "quang ninh", "ha long"),
            outside("Bắc Ninh", 21.18, 106.07, "bac ninh"),
            outside("Hải Dương", 20.94, 106.33, "hai duong"),
            outside("Hưng Yên", 20.65, 106.05, "hung yen"),
            outside("Hà Nam", 20.54, 105.92, "ha nam"),
            outside("Nam Định", 20.42, 106.17, "nam dinh"),
            outside("Thái Bình", 20.45, 106.34, "thai binh"),
            outside("Ninh Bình", 20.25, 105.97, "ninh binh"),
            outside("Thanh Hóa", 19.81, 105.78, "thanh hoa"),
            outside("Nghệ An", 18.67, 105.68, "nghe an"),
            outside("Hà Tĩnh", 18.34, 105.90, "ha tinh"),
            outside("Thái Nguyên", 21.59, 105.85, "thai nguyen"),
            outside("Vĩnh Phúc", 21.31, 105.60, "vinh phuc"),
            outside("Phú Thọ", 21.32, 105.40, "phu tho", "viet tri"),
            outside("Bắc Giang", 21.27, 106.19, "bac giang"),
            outside("Hòa Bình", 20.81, 105.34, "hoa binh"),
            outside("Lạng Sơn", 21.85, 106.76, "lang son"),
            outside("Lào Cai", 22.48, 103.97, "lao cai", "sa pa", "sapa"),
            outside("Sơn La", 21.33, 103.91, "son la"),
            outside("Điện Biên", 21.39, 103.02, "dien bien"),
            outside("Yên Bái", 21.72, 104.91, "yen bai"),
            outside("Tuyên Quang", 21.82, 105.21, "tuyen quang"),
            outside("Cao Bằng", 22.67, 106.26, "cao bang"),
            outside("Hà Giang", 22.82, 104.98, "ha giang"),
            outside("Bắc Kạn", 22.15, 105.83, "bac kan"),
            outside("Lai Châu", 22.39, 103.46, "lai chau"));

    private static double kilometers(double lat1, double lng1, double lat2, double lng2) {
        double a1 = Math.toRadians(lat1), a2 = Math.toRadians(lat2), dLat = a2 - a1, dLng = Math.toRadians(lng2 - lng1);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(a1) * Math.cos(a2) * Math.pow(Math.sin(dLng / 2), 2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h))));
    }

    static Catalog loadCatalog(Connection connection) throws SQLException {
        var branches = new LinkedHashMap<String, BranchInfo>();
        try (var statement = connection.prepareStatement(BRANCH_QUERY)) {
            statement.setQueryTimeout(60);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) branches.put(rows.getString(2), new BranchInfo(rows.getLong(1), rows.getString(2), rows.getString(3),
                        rows.getDouble(4), rows.getDouble(5)));
            }
        }
        if (!branches.keySet().containsAll(List.of("SGN", "HAN", "DAD")))
            throw new IllegalStateException("Thiếu chi nhánh SGN/HAN/DAD; hãy chạy UpgradeAivenBranches trước.");
        var areas = new ArrayList<AreaInfo>();
        try (var statement = connection.prepareStatement(AREA_QUERY)) {
            statement.setQueryTimeout(60);
            try (var rows = statement.executeQuery()) { while (rows.next()) areas.add(new AreaInfo(rows.getString(1), rows.getString(2), rows.getString(3))); }
        }
        return new Catalog(branches, areas);
    }

    static List<LegacyOrder> loadLegacy(Connection connection) throws SQLException {
        var result = new ArrayList<LegacyOrder>();
        try (var statement = connection.prepareStatement(LEGACY_QUERY)) {
            statement.setQueryTimeout(120);
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    long shipper = rows.getLong(6);
                    result.add(new LegacyOrder(rows.getLong(1), rows.getString(2), rows.getString(3), rows.getString(4), rows.getString(5),
                            rows.wasNull() ? null : shipper));
                }
            }
        }
        return result;
    }

    private static void validateTarget(Connection connection) throws SQLException {
        var metadata = connection.getMetaData();
        if (!"MySQL".equals(metadata.getDatabaseProductName()) || metadata.getDatabaseMajorVersion() < 8)
            throw new IllegalArgumentException("Chỉ kiểm tra/nâng cấp MySQL 8+ đã có schema UTEExpress.");
        for (String table : List.of("shipment_order", "order_route", "branch", "province_area"))
            if (!UpgradeAivenOrderSubmissions.tableExists(connection, table))
                throw new IllegalStateException("Thiếu bảng " + table + "; hãy chạy UpgradeAivenBranches trước.");
    }
}
