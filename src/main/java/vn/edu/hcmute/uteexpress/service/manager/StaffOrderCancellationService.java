package vn.edu.hcmute.uteexpress.service.manager;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent;
import vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderStatusHistory;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository;
import vn.edu.hcmute.uteexpress.service.OrderCancellationEffects;
import vn.edu.hcmute.uteexpress.service.admin.AuditService;
import vn.edu.hcmute.uteexpress.service.routing.DispatchRoutingPolicy;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsAccess;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsInput;

/**
 * Manager chi nhánh gửi (hoặc Admin) hủy đơn chưa lấy hàng mà không xử lý tiếp được: lấy hàng thất bại nhiều lần và
 * không liên lạc được người gửi, khách không mang hàng tới quầy, đơn ví không bao giờ được thanh toán.
 * Hàng chưa vào tay hãng nên chỉ hủy ở trạng thái Chờ lấy hàng; lý do bắt buộc, có nhật ký và email báo người gửi.
 */
@Service
@Transactional
public class StaffOrderCancellationService {
    private final LogisticsAccess access;
    private final DispatchRoutingPolicy routing;
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final OrderCancellationEffects effects;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public StaffOrderCancellationService(LogisticsAccess access, DispatchRoutingPolicy routing, OrderRepository orders,
            OrderStatusHistoryRepository history, OrderCancellationEffects effects, AuditService audit,
            ApplicationEventPublisher events, Clock clock) {
        this.access = access; this.routing = routing; this.orders = orders; this.history = history;
        this.effects = effects; this.audit = audit; this.events = events; this.clock = clock;
    }

    /** Đơn nào trong danh sách người này hủy được (để chỉ hiện nút cho đúng Manager chi nhánh gửi hoặc Admin). */
    @Transactional(readOnly = true)
    public java.util.Set<Long> cancelable(String actor, java.util.Collection<Order> list) {
        var staff = access.internal(actor);
        var result = new java.util.HashSet<Long>();
        var pending = list.stream().filter(order -> order.getStatus() == Order.OrderStatus.PENDING_PICKUP).toList();
        var routeByOrder = routing.routesOf(pending); // một truy vấn cho cả trang
        for (var order : pending) {
            var route = routeByOrder.get(order.getId());
            if (route == null || routing.canManage(staff, route)) result.add(order.getId());
        }
        return result;
    }

    public void cancel(String actor, Long orderId, long revision, String reason) {
        var staff = access.internal(actor);
        String note = LogisticsInput.text(reason, 10, 300, "Lý do hủy");
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("Mã vận đơn không hợp lệ.");
        Order order = orders.findLockedById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        // Đơn chờ lấy thuộc chi nhánh gửi; đơn cũ chưa có tuyến theo quy tắc chuyển tiếp (chi nhánh nào cũng xử lý được).
        routing.routeIfPresent(order).ifPresent(route -> routing.requireManager(staff, route));
        if (order.getRevision() != revision) throw new StaffConflictException("Đơn vừa được cập nhật. Hãy tải lại trang.");
        if (order.getStatus() != Order.OrderStatus.PENDING_PICKUP)
            throw new IllegalArgumentException("Chỉ hủy được đơn chưa lấy hàng (đang chờ lấy hoặc chờ khách mang tới quầy).");
        LocalDateTime now = LocalDateTime.now(clock);
        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setUpdatedAt(now);
        orders.save(order);
        history.save(new OrderStatusHistory(order, staff, Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.CANCELLED, null,
                "Chi nhánh hủy vận đơn: " + note, null, now));
        effects.afterCancel(order, actor);
        events.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getTrackingCode(), order.getStatus().name(), now));
        var sender = order.getSender();
        if (sender != null) events.publishEvent(new SenderOrderUpdateEvent(sender.getEmail(),
                sender.getFullName() == null || sender.getFullName().isBlank() ? order.getSenderName() : sender.getFullName(),
                order.getTrackingCode(), SenderOrderUpdateEvent.Kind.CANCELLED_BY_BRANCH, note, null));
        audit.record(actor, "ORDER_CANCEL", "order:" + order.getId(), order.getTrackingCode() + "; " + note);
    }
}
