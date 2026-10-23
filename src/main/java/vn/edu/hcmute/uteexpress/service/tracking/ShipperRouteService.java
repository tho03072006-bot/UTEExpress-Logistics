package vn.edu.hcmute.uteexpress.service.tracking;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperRoute;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderStatusHistory;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy;
import vn.edu.hcmute.uteexpress.entity.routing.ProvinceArea;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.service.routing.RouteDistance;

/**
 * Gom công việc hôm nay thành lộ trình nhiều điểm (B6).
 * Đơn chờ lấy dùng địa chỉ người gửi, đơn đang xử lý dùng địa chỉ người nhận; cùng địa chỉ thì gộp một điểm.
 * Thứ tự: chia nhóm độ gấp (1. Hỏa tốc hoặc đến hẹn giao/lấy lại; 2. sắp quá hạn theo Chính sách vận hành;
 * 3. còn lại), trong từng nhóm đi điểm gần nhất trước (Haversine giữa tâm khu vực), xuất phát từ kho chi nhánh
 * của shipper; nhóm sau đi tiếp từ điểm cuối của nhóm trước. Không dùng địa chỉ nhà shipper, không gọi API bản đồ trả phí.
 * Đơn cũ chưa có tuyến chi nhánh giữ thứ tự cũ và xếp cuối.
 * Google Maps URL giới hạn 9 điểm trung gian và 2.048 ký tự nên lộ trình được chia thành nhiều chặng.
 */
@Service
@Transactional(readOnly = true)
public class ShipperRouteService {
    static final int MAX_ORDERS = 60;
    static final int MAX_STOPS_PER_LEG = 10;
    /** Google Maps trên trình duyệt điện thoại nhận tối đa 3 điểm dừng giữa đường (+ điểm đến). */
    static final int MAX_STOPS_PER_MOBILE_LEG = 4;
    static final int MAX_URL_LENGTH = 2000;
    private final LogisticsAccess access;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService policies;
    private final vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes;
    private final vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository staffBranches;
    private final vn.edu.hcmute.uteexpress.repository.routing.ProvinceAreaRepository areas;

    public ShipperRouteService(LogisticsAccess access, OrderRepository orders, OrderStatusHistoryRepository history, Clock clock,
            vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService policies,
            vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routes,
            vn.edu.hcmute.uteexpress.repository.routing.StaffBranchRepository staffBranches,
            vn.edu.hcmute.uteexpress.repository.routing.ProvinceAreaRepository areas) {
        this.access = access; this.orders = orders; this.history = history; this.clock = clock;
        this.policies = policies; this.routes = routes; this.staffBranches = staffBranches; this.areas = areas;
    }

    /** Một điểm dừng chờ xếp: thứ tự cũ, nhóm độ gấp (1 gấp nhất), tâm khu vực (null = đơn cũ chưa có tuyến). */
    record Candidate(int index, int group, ProvinceArea area) { }

    /**
     * Thứ tự đi: nhóm 1 → 2 → 3, trong nhóm đi láng giềng gần nhất từ điểm hiện tại (ban đầu là kho, có thể null);
     * khoảng cách bằng nhau thì giữ thứ tự cũ. Điểm chưa có khu vực xếp cuối theo thứ tự cũ.
     */
    static List<Integer> sequence(List<Candidate> candidates, ProvinceArea start) {
        var result = new ArrayList<Integer>(candidates.size());
        ProvinceArea current = start;
        for (int group = 1; group <= 3; group++) {
            final int level = group;
            var remaining = new ArrayList<>(candidates.stream().filter(c -> c.area() != null && c.group() == level).toList());
            while (!remaining.isEmpty()) {
                final ProvinceArea from = current;
                var next = remaining.stream().min(Comparator.<Candidate>comparingDouble(c -> from == null ? 0 : RouteDistance.kilometers(from, c.area()))
                        .thenComparingInt(Candidate::index)).orElseThrow();
                remaining.remove(next); result.add(next.index()); current = next.area();
            }
        }
        candidates.stream().filter(c -> c.area() == null).map(Candidate::index).forEach(result::add);
        return result;
    }

    /** Nhóm độ gấp của một đơn theo B6. */
    static int urgency(Order order, boolean pickup, boolean due, OperationsPolicy policy, LocalDateTime now) {
        if (due || order.getServiceType() == Order.ServiceType.SUPER_EXPRESS) return 1;
        if (order.getCreatedAt() == null) return 3;
        int hours = pickup ? policy.getPickupHours() : switch (order.getServiceType() == null ? Order.ServiceType.STANDARD : order.getServiceType()) {
            case STANDARD -> policy.getStandardHours(); case EXPRESS -> policy.getExpressHours(); case SUPER_EXPRESS -> policy.getSuperExpressHours();
        };
        // "Sắp quá hạn": đã dùng từ 75% thời hạn trở lên (kể cả đã quá hạn).
        var warnFrom = order.getCreatedAt().plusMinutes(Math.round(hours * 60 * 0.75));
        return now.isBefore(warnFrom) ? 3 : 2;
    }

    public ShipperRoute today(String username) {
        var shipper = access.shipper(username);
        var now = LocalDateTime.now(clock);
        var page = orders.searchWorkOrders(shipper, null, null, null, null, 1, now.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1000),
                now.toLocalDate().plusDays(1).atStartOfDay(), policies.current().getMaxDeliveryAttempts(), now, PageRequest.of(0, MAX_ORDERS));
        var ids = page.getContent().stream().map(Order::getId).toList();
        Set<Long> due = new HashSet<>();
        if (!ids.isEmpty()) {
            // Lần giao thất bại và lần lấy hàng thất bại gần nhất của từng đơn (một truy vấn, cũ trước mới sau).
            Map<Long, OrderStatusHistory> latestDelivery = new HashMap<>(), latestPickup = new HashMap<>();
            for (var h : history.failureEntries(ids)) (h.isPickupFailure() ? latestPickup : latestDelivery).put(h.getOrder().getId(), h);
            for (var latest : List.of(latestDelivery, latestPickup)) latest.values().stream()
                    .filter(h -> h.getNextAttemptAt() != null && !h.getNextAttemptAt().isAfter(now))
                    .forEach(h -> due.add(h.getOrder().getId()));
        }
        Map<Long, vn.edu.hcmute.uteexpress.entity.routing.OrderRoute> routeByOrder = new HashMap<>();
        if (!ids.isEmpty()) for (var route : routes.findWithAreasByOrderIds(ids)) routeByOrder.put(route.getOrder().getId(), route);
        var policy = policies.current();
        Map<String, List<ShipperRoute.Task>> grouped = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        Map<String, Integer> groupOf = new HashMap<>();
        Map<String, ProvinceArea> areaOf = new HashMap<>();
        int missing = 0;
        for (Order order : page.getContent()) {
            boolean pickup = order.getStatus() == Order.OrderStatus.PENDING_PICKUP;
            String address = clean(pickup ? order.getSenderAddress() : order.getReceiverAddress());
            if (address.isEmpty()) { missing++; continue; }
            String key = address.toLowerCase(Locale.ROOT);
            labels.putIfAbsent(key, address);
            boolean isDue = due.contains(order.getId());
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(new ShipperRoute.Task(order.getId(), order.getTrackingCode(),
                    pickup, pickup ? order.getSenderName() : order.getReceiverName(),
                    pickup ? order.getSender().getPhone() : order.getReceiverPhone(), isDue));
            // Điểm dừng lấy nhóm gấp nhất trong các đơn của nó; tọa độ theo khu vực lấy (chờ lấy) hoặc khu vực giao.
            groupOf.merge(key, urgency(order, pickup, isDue, policy, now), Math::min);
            var route = routeByOrder.get(order.getId());
            if (route != null) areaOf.putIfAbsent(key, pickup ? route.getPickupArea() : route.getDeliveryArea());
        }
        var hub = staffBranches.findByUser_Id(shipper.getId()).map(staff -> staff.getBranch()).orElse(null);
        ProvinceArea hubArea = hub == null ? null : areas.findByAreaCode(hub.getHubAreaCode()).orElse(null);
        var keys = new ArrayList<>(grouped.keySet());
        var candidates = new ArrayList<Candidate>();
        for (int i = 0; i < keys.size(); i++) candidates.add(new Candidate(i, groupOf.get(keys.get(i)), areaOf.get(keys.get(i))));
        List<ShipperRoute.Stop> stops = new ArrayList<>();
        for (int index : sequence(candidates, hubArea)) {
            String key = keys.get(index);
            stops.add(new ShipperRoute.Stop(stops.size() + 1, labels.get(key), List.copyOf(grouped.get(key))));
        }
        int taskCount = stops.stream().mapToInt(stop -> stop.tasks().size()).sum();
        String origin = hub == null || hub.getHubAddress() == null ? null : clean(hub.getHubAddress());
        String startHub = origin == null ? null : hub.getName() + " · " + origin;
        return new ShipperRoute(List.copyOf(stops), legs(stops, MAX_STOPS_PER_LEG, origin), legs(stops, MAX_STOPS_PER_MOBILE_LEG, origin),
                taskCount, missing, page.getTotalElements() > MAX_ORDERS, startHub);
    }

    static List<ShipperRoute.Leg> legs(List<ShipperRoute.Stop> stops) { return legs(stops, MAX_STOPS_PER_LEG); }

    static List<ShipperRoute.Leg> legs(List<ShipperRoute.Stop> stops, int maxStops) { return legs(stops, maxStops, null); }

    /** firstOrigin: điểm xuất phát của chặng đầu (địa chỉ kho); null thì Google Maps dùng vị trí hiện tại của điện thoại. */
    static List<ShipperRoute.Leg> legs(List<ShipperRoute.Stop> stops, int maxStops, String firstOrigin) {
        List<ShipperRoute.Leg> legs = new ArrayList<>();
        int start = 0;
        String origin = firstOrigin == null || firstOrigin.isBlank() ? null : firstOrigin;
        while (start < stops.size()) {
            int end = start + 1;
            while (end < stops.size() && end - start < maxStops
                    && url(origin, stops.subList(start, end + 1)).length() <= MAX_URL_LENGTH) end++;
            var chunk = stops.subList(start, end);
            legs.add(new ShipperRoute.Leg(legs.size() + 1, chunk.get(0).number(), chunk.get(chunk.size() - 1).number(),
                    url(origin, chunk)));
            origin = chunk.get(chunk.size() - 1).address();
            start = end;
        }
        return List.copyOf(legs);
    }

    static String url(String origin, List<ShipperRoute.Stop> chunk) {
        var url = new StringBuilder(ShipperMaps.DIRECTIONS);
        if (origin != null) url.append("&origin=").append(encode(ShipperMaps.place(origin)));
        url.append("&destination=").append(encode(ShipperMaps.place(chunk.get(chunk.size() - 1).address())));
        if (chunk.size() > 1) url.append("&waypoints=").append(encode(String.join("|",
                chunk.subList(0, chunk.size() - 1).stream().map(stop -> ShipperMaps.place(stop.address())).toList())));
        return url.toString();
    }

    /** Bỏ ký tự điều khiển và dấu "|" (dấu phân cách điểm của Google Maps), gộp khoảng trắng. */
    static String clean(String address) {
        if (address == null) return "";
        return address.replaceAll("[\\p{Cntrl}|]+", " ").replaceAll("\\s+", " ").strip();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
