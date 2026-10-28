package vn.edu.hcmute.uteexpress.tool.database;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import vn.edu.hcmute.uteexpress.entity.Order.ServiceType;

/** Danh mục cố định của đồ án; không đọc cấu hình riêng và không mở kết nối database. */
public final class BranchSeedData {
    public record BranchSeed(String code, String name, String provinceCode, String hubAddress, String hubAreaCode) { }
    public record AreaSeed(String branchCode, String provinceCode, String code, String name, BigDecimal lat, BigDecimal lng) { }
    public record NeighborSeed(String areaCode, String neighborCode) { }
    public record TariffSeed(String origin, String destination, ServiceType service, BigDecimal surcharge, String leadTime) { }
    public record Catalog(List<BranchSeed> branches, List<AreaSeed> areas, List<NeighborSeed> neighbors, List<TariffSeed> tariffs) { }
    private BranchSeedData() { }

    public static Catalog load() {
        var branches = List.of(
                new BranchSeed("SGN", "Chi nhánh TP. Hồ Chí Minh", "79", "Kho SGN, khu vực Thủ Đức, TP. Hồ Chí Minh", "SGN-THUDUC"),
                new BranchSeed("HAN", "Chi nhánh Hà Nội", "01", "Kho HAN, khu vực Cầu Giấy, Hà Nội", "HAN-CAUGIAY"),
                new BranchSeed("DAD", "Chi nhánh Đà Nẵng", "48", "Kho DAD, khu vực Hải Châu, Đà Nẵng", "DAD-HAICHAU"));
        var areas = rows("areas.csv", "branch_code,province_code,area_code,area_name,lat,lng", 6).stream()
                .map(row -> new AreaSeed(row[0], row[1], row[2], row[3], new BigDecimal(row[4]), new BigDecimal(row[5]))).toList();
        var byCode = new LinkedHashMap<String, AreaSeed>();
        var counts = new HashMap<String, Integer>();
        var provinces = new HashMap<String, String>();
        branches.forEach(branch -> provinces.put(branch.code(), branch.provinceCode()));
        for (var area : areas) {
            if (!Objects.equals(provinces.get(area.branchCode()), area.provinceCode())
                    || !area.code().matches(area.branchCode() + "-[A-Z0-9]+") || area.name().isBlank()
                    || area.lat().abs().compareTo(new BigDecimal("90")) > 0
                    || area.lng().abs().compareTo(new BigDecimal("180")) > 0
                    || area.lat().scale() > 6 || area.lng().scale() > 6
                    || byCode.putIfAbsent(area.code(), area) != null) throw invalid();
            counts.merge(area.branchCode(), 1, Integer::sum);
        }
        if (!counts.equals(Map.of("SGN", 22, "HAN", 30, "DAD", 7))) throw invalid();
        for (var branch : branches) {
            var hub = byCode.get(branch.hubAreaCode());
            if (hub == null || !hub.branchCode().equals(branch.code())) throw invalid();
        }
        var neighbors = new LinkedHashSet<NeighborSeed>();
        for (var row : rows("neighbors.csv", "area_code,neighbor_code", 2)) {
            var area = byCode.get(row[0]); var neighbor = byCode.get(row[1]);
            if (area == null || neighbor == null || area.code().equals(neighbor.code())
                    || !area.branchCode().equals(neighbor.branchCode())
                    || !neighbors.add(new NeighborSeed(area.code(), neighbor.code()))) throw invalid();
            if (!neighbors.add(new NeighborSeed(neighbor.code(), area.code()))) throw invalid();
        }
        return new Catalog(List.copyOf(branches), List.copyOf(areas), List.copyOf(neighbors), tariffs(branches));
    }

    private static List<TariffSeed> tariffs(List<BranchSeed> branches) {
        var result = new ArrayList<TariffSeed>();
        for (var origin : branches) for (var dest : branches) for (var service : ServiceType.values()) {
            boolean local = origin.code().equals(dest.code());
            if (!local && service == ServiceType.SUPER_EXPRESS) continue;
            boolean longRoute = !local && Set.of(origin.code(), dest.code()).containsAll(Set.of("SGN", "HAN"));
            int surcharge = local ? 0 : service == ServiceType.STANDARD ? (longRoute ? 25000 : 15000) : (longRoute ? 40000 : 25000);
            String leadTime = local ? switch (service) {
                case STANDARD -> "1–2 ngày"; case EXPRESS -> "Trong ngày"; case SUPER_EXPRESS -> "Trong 4 giờ";
            } : service == ServiceType.STANDARD ? (longRoute ? "4–5 ngày" : "3–4 ngày") : (longRoute ? "2–3 ngày" : "2 ngày");
            result.add(new TariffSeed(origin.code(), dest.code(), service, BigDecimal.valueOf(surcharge), leadTime));
        }
        return List.copyOf(result);
    }

    private static List<String[]> rows(String file, String header, int count) {
        try (var stream = BranchSeedData.class.getResourceAsStream("/db/branches/" + file)) {
            if (stream == null) throw invalid();
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                var lines = reader.lines().map(String::strip).filter(line -> !line.isBlank() && !line.startsWith("#")).toList();
                if (lines.isEmpty() || !lines.get(0).equals(header)) throw invalid();
                var result = new ArrayList<String[]>();
                for (String line : lines.subList(1, lines.size())) {
                    String[] values = line.split(",", -1);
                    if (values.length != count || Arrays.stream(values).anyMatch(String::isBlank)) throw invalid();
                    result.add(values);
                }
                return result;
            }
        } catch (IOException | NumberFormatException ex) {
            throw new IllegalStateException("Không đọc được danh mục khu vực giao nhận của migration.", ex);
        }
    }
    private static IllegalStateException invalid() { return new IllegalStateException("Danh mục khu vực giao nhận không hợp lệ; dừng trước khi ghi database."); }
}
