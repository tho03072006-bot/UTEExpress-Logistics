package vn.edu.hcmute.uteexpress.service.routing;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy;
import vn.edu.hcmute.uteexpress.entity.routing.OrderRoute;
import vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository;
import vn.edu.hcmute.uteexpress.repository.routing.RouteTariffRepository;

/**
 * Hạn giao của một đơn, dùng chung cho cảnh báo "Quá hạn giao" của Manager và nhóm "sắp quá hạn" trong lộ trình Shipper.
 * Đơn có tuyến chi nhánh lấy mốc trên của thời gian dự kiến trong Cước tuyến, tức đúng điều đã báo khách lúc tạo đơn
 * (Giao nhanh SGN → HAN "2–3 ngày" thì hạn là 72 giờ, Tiêu chuẩn nội tỉnh "1–2 ngày" là 48 giờ).
 * Đơn cũ chưa có tuyến, hoặc biểu giá không đọc được, dùng số giờ theo dịch vụ trong Chính sách vận hành.
 * Hạn tính từ lúc hàng vào tay hãng (Shipper lấy hàng hoặc kho nhận tại quầy), như cách các hãng tính thời gian giao;
 * đơn không có mốc này trong lịch sử thì tính từ lúc tạo đơn.
 */
@Service @Transactional(readOnly = true)
public class DeliveryDeadlines {
    private final RouteTariffRepository tariffs;
    private final OrderStatusHistoryRepository history;

    public DeliveryDeadlines(RouteTariffRepository tariffs, OrderStatusHistoryRepository history) {
        this.tariffs = tariffs; this.history = history;
    }

    record Key(Long originId, Long destId, Order.ServiceType service) { }

    /** Chụp biểu giá tuyến hiện hành một lần cho cả danh sách đơn. */
    public Rules rules(OperationsPolicy policy) {
        // Trang tổng quan đếm nhiều nhóm cảnh báo trong một giao dịch: chỉ đọc bảng cước tuyến một lần.
        Map<Key, Integer> routeHours = vn.edu.hcmute.uteexpress.service.tracking.TransactionCache.get("route-lead-hours", () -> {
            Map<Key, Integer> hoursByRoute = new HashMap<>();
            for (var tariff : tariffs.findAll()) LeadTimes.hours(tariff.getLeadTimeText()).ifPresent(hours -> hoursByRoute.put(
                    new Key(tariff.getOriginBranch().getId(), tariff.getDestBranch().getId(), tariff.getServiceType()), hours));
            return Map.copyOf(hoursByRoute);
        });
        return new Rules(policy, routeHours);
    }

    /** Lúc lấy hàng (hoặc nhận tại quầy) của từng đơn; đơn chưa có mốc này không nằm trong kết quả. */
    public Map<Long, LocalDateTime> pickupTimes(Collection<Long> orderIds) {
        Map<Long, LocalDateTime> result = new HashMap<>();
        if (orderIds.isEmpty()) return result;
        for (Object[] row : history.firstPickupTimes(orderIds)) result.put((Long) row[0], (LocalDateTime) row[1]);
        return result;
    }

    public static int policyHours(OperationsPolicy policy, Order.ServiceType service) {
        return switch (service == null ? Order.ServiceType.STANDARD : service) {
            case STANDARD -> policy.getStandardHours(); case EXPRESS -> policy.getExpressHours(); case SUPER_EXPRESS -> policy.getSuperExpressHours();
        };
    }

    public record Rules(OperationsPolicy policy, Map<Key, Integer> routeHours) {
        /** Số giờ được phép giao; route null là đơn cũ chưa có tuyến. */
        public int hours(Order order, OrderRoute route) {
            Integer promised = route == null ? null : routeHours.get(new Key(route.getOriginBranch().getId(),
                    route.getDestBranch().getId(), order.getServiceType() == null ? Order.ServiceType.STANDARD : order.getServiceType()));
            return promised != null ? promised : policyHours(policy, order.getServiceType());
        }
        /** Hạn giao; pickedUpAt null (chưa có mốc lấy hàng trong lịch sử) thì tính từ lúc tạo đơn. */
        public LocalDateTime due(Order order, OrderRoute route, LocalDateTime pickedUpAt) {
            LocalDateTime start = pickedUpAt != null ? pickedUpAt : order.getCreatedAt();
            return start == null ? null : start.plusHours(hours(order, route));
        }
        /**
         * Hạn lấy hàng của đơn Hỏa tốc: không thể hứa giao trong 4 giờ nếu để cả ngày mới lấy, nên hạn lấy không quá thời gian
         * giao đã hứa (ngắn nhất trong các tuyến Hỏa tốc) và cũng không quá hạn lấy chung của Chính sách vận hành.
         */
        public int superExpressPickupHours() {
            int promised = routeHours.entrySet().stream().filter(e -> e.getKey().service() == Order.ServiceType.SUPER_EXPRESS)
                    .mapToInt(Map.Entry::getValue).min().orElse(policy.getSuperExpressHours());
            return Math.min(policy.getPickupHours(), promised);
        }
        /** Hạn ngắn nhất trong mọi tuyến và dịch vụ, để lọc sơ bộ ở cơ sở dữ liệu trước khi xét từng đơn. */
        public int shortest() {
            int min = Math.min(policy.getStandardHours(), Math.min(policy.getExpressHours(), policy.getSuperExpressHours()));
            for (int hours : routeHours.values()) min = Math.min(min, hours);
            return min;
        }
    }
}
