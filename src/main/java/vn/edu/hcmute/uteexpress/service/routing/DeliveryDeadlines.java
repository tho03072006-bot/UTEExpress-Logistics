package vn.edu.hcmute.uteexpress.service.routing;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy;
import vn.edu.hcmute.uteexpress.entity.routing.OrderRoute;
import vn.edu.hcmute.uteexpress.repository.routing.RouteTariffRepository;

/**
 * Hạn giao của một đơn, dùng chung cho cảnh báo "Quá hạn giao" của Manager và nhóm "sắp quá hạn" trong lộ trình Shipper.
 * Đơn có tuyến chi nhánh lấy mốc trên của thời gian dự kiến trong Cước tuyến, tức đúng điều đã báo khách lúc tạo đơn
 * (Giao nhanh SGN → HAN "2–3 ngày" thì hạn là 72 giờ, Tiêu chuẩn nội tỉnh "1–2 ngày" là 48 giờ).
 * Đơn cũ chưa có tuyến, hoặc biểu giá không đọc được, dùng số giờ theo dịch vụ trong Chính sách vận hành. Mốc tính từ lúc tạo đơn.
 */
@Service @Transactional(readOnly = true)
public class DeliveryDeadlines {
    private final RouteTariffRepository tariffs;

    public DeliveryDeadlines(RouteTariffRepository tariffs) { this.tariffs = tariffs; }

    record Key(Long originId, Long destId, Order.ServiceType service) { }

    /** Chụp biểu giá tuyến hiện hành một lần cho cả danh sách đơn. */
    public Rules rules(OperationsPolicy policy) {
        Map<Key, Integer> routeHours = new HashMap<>();
        for (var tariff : tariffs.findAll()) LeadTimes.hours(tariff.getLeadTimeText()).ifPresent(hours -> routeHours.put(
                new Key(tariff.getOriginBranch().getId(), tariff.getDestBranch().getId(), tariff.getServiceType()), hours));
        return new Rules(policy, Map.copyOf(routeHours));
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
        public LocalDateTime due(Order order, OrderRoute route) {
            return order.getCreatedAt() == null ? null : order.getCreatedAt().plusHours(hours(order, route));
        }
        /** Hạn ngắn nhất trong mọi tuyến và dịch vụ, để lọc sơ bộ ở cơ sở dữ liệu trước khi xét từng đơn. */
        public int shortest() {
            int min = Math.min(policy.getStandardHours(), Math.min(policy.getExpressHours(), policy.getSuperExpressHours()));
            for (int hours : routeHours.values()) min = Math.min(min, hours);
            return min;
        }
    }
}
