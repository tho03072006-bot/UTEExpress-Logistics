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
import vn.edu.hcmute.uteexpress.repository.*;

/**
 * Gom công việc hôm nay thành lộ trình nhiều điểm theo đúng thứ tự ưu tiên của danh sách.
 * Đơn chờ lấy dùng địa chỉ người gửi, đơn đang xử lý dùng địa chỉ người nhận; cùng địa chỉ thì gộp một điểm.
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

    public ShipperRouteService(LogisticsAccess access, OrderRepository orders, OrderStatusHistoryRepository history, Clock clock) {
        this.access = access; this.orders = orders; this.history = history; this.clock = clock;
    }

    public ShipperRoute today(String username) {
        var shipper = access.shipper(username);
        var now = LocalDateTime.now(clock);
        var page = orders.searchWorkOrders(shipper, null, null, null, null, 1, now,
                now.toLocalDate().plusDays(1).atStartOfDay(), PageRequest.of(0, MAX_ORDERS));
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
        Map<String, List<ShipperRoute.Task>> grouped = new LinkedHashMap<>();
        Map<String, String> labels = new HashMap<>();
        int missing = 0;
        for (Order order : page.getContent()) {
            boolean pickup = order.getStatus() == Order.OrderStatus.PENDING_PICKUP;
            String address = clean(pickup ? order.getSenderAddress() : order.getReceiverAddress());
            if (address.isEmpty()) { missing++; continue; }
            String key = address.toLowerCase(Locale.ROOT);
            labels.putIfAbsent(key, address);
            grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(new ShipperRoute.Task(order.getId(), order.getTrackingCode(),
                    pickup, pickup ? order.getSenderName() : order.getReceiverName(),
                    pickup ? order.getSender().getPhone() : order.getReceiverPhone(), due.contains(order.getId())));
        }
        List<ShipperRoute.Stop> stops = new ArrayList<>();
        grouped.forEach((key, tasks) -> stops.add(new ShipperRoute.Stop(stops.size() + 1, labels.get(key), List.copyOf(tasks))));
        int taskCount = stops.stream().mapToInt(stop -> stop.tasks().size()).sum();
        return new ShipperRoute(List.copyOf(stops), legs(stops), legs(stops, MAX_STOPS_PER_MOBILE_LEG), taskCount, missing, page.getTotalElements() > MAX_ORDERS);
    }

    static List<ShipperRoute.Leg> legs(List<ShipperRoute.Stop> stops) { return legs(stops, MAX_STOPS_PER_LEG); }

    static List<ShipperRoute.Leg> legs(List<ShipperRoute.Stop> stops, int maxStops) {
        List<ShipperRoute.Leg> legs = new ArrayList<>();
        int start = 0;
        String origin = null;
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
