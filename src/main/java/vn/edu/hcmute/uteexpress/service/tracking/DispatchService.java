package vn.edu.hcmute.uteexpress.service.tracking;

import java.time.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperNotificationEvent;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.*;

@Service
@Transactional(readOnly = true)
public class DispatchService {
    private final OrderRepository orders;
    private final AppUserRepository users;
    private final OrderStatusHistoryRepository history;
    private final ShipperNotificationRepository notifications;
    private final LogisticsAccess access;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final vn.edu.hcmute.uteexpress.service.admin.AuditService audit;
    private final vn.edu.hcmute.uteexpress.service.OrderPaymentService payments;
    public DispatchService(OrderRepository orders, AppUserRepository users, OrderStatusHistoryRepository history,
            ShipperNotificationRepository notifications, LogisticsAccess access, Clock clock, ApplicationEventPublisher events,
            vn.edu.hcmute.uteexpress.service.admin.AuditService audit,
            vn.edu.hcmute.uteexpress.service.OrderPaymentService payments) {
        this.orders = orders; this.users = users; this.history = history; this.notifications = notifications;
        this.access = access; this.clock = clock; this.events = events;
        this.audit = audit;
        this.payments = payments;
    }
    public Page<Order> pending(String actor, int page) {
        access.internal(actor);
        return orders.findByStatus(Order.OrderStatus.PENDING_PICKUP,
                PageRequest.of(LogisticsInput.page(page), 10, Sort.by("createdAt", "id")));
    }
    public Page<AppUser> shippers(String actor, int page) {
        access.internal(actor);
        return users.findByRoleAndEnabledTrue(AppUser.Role.SHIPPER,
                PageRequest.of(LogisticsInput.page(page), 100, Sort.by("username")));
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void assign(String code, Long shipperId, long expectedShipperId, LocalDateTime expectedUpdatedAt,
            String note, String actor) {
        AppUser manager = access.internal(actor);
        String reason = LogisticsInput.text(note, 5, 350, "Lý do phân công");
        if (shipperId == null || shipperId <= 0 || expectedShipperId < 0) throw new IllegalArgumentException("Mã Shipper không hợp lệ.");
        var shipper = users.findById(shipperId).filter(u -> u.isEnabled() && !u.isLockedByAdmin() && u.getRole() == AppUser.Role.SHIPPER)
                .orElseThrow(() -> new IllegalArgumentException("Hãy chọn Shipper đang hoạt động."));
        var order = orders.findLockedByTrackingCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        if (order.getStatus() != Order.OrderStatus.PENDING_PICKUP)
            throw new IllegalArgumentException("Chỉ phân công khi đơn còn chờ lấy hàng.");
        payments.requireReadyForFulfillment(order);
        long current = order.getShipper() == null ? 0 : order.getShipper().getId();
        if (current != expectedShipperId || expectedUpdatedAt == null || !expectedUpdatedAt.equals(order.getUpdatedAt()))
            throw new IllegalArgumentException("Vận đơn đã thay đổi. Hãy tải lại trang trước khi phân công.");
        if (current == shipperId) throw new IllegalArgumentException("Đơn đã được phân công cho Shipper này.");
        LocalDateTime now = LocalDateTime.now(clock);
        notifications.dismissPreviousAssignment(order, now);
        order.setShipper(shipper);
        order.setUpdatedAt(now);
        orders.save(order);
        history.save(new OrderStatusHistory(order, manager, order.getStatus(), order.getStatus(), null,
                "Phân công cho " + (shipper.getFullName() == null ? shipper.getUsername() : shipper.getFullName())
                        + ". " + reason, null, now));
        var notification = notifications.findByOrderAndRecipient(order, shipper).orElseGet(ShipperNotification::new);
        notification.assign(order, shipper, now);
        notifications.saveAndFlush(notification);
        events.publishEvent(new ShipperNotificationEvent(shipper.getUsername(), notification.getId(), order.getTrackingCode()));
        events.publishEvent(new vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent(
                order.getId(), order.getTrackingCode(), order.getStatus().name(), now));
        audit.record(actor, "ORDER_ASSIGN", "order:" + order.getId(), "Shipper " + shipper.getId() + "; " + reason);
    }
}
