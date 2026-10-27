package vn.edu.hcmute.uteexpress.service.tracking;

import java.time.*;
import java.util.*;
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
    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;
    private final OrderRepository orders;
    private final AppUserRepository users;
    private final OrderStatusHistoryRepository history;
    private final ShipperNotificationRepository notifications;
    private final LogisticsAccess access;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final vn.edu.hcmute.uteexpress.service.admin.AuditService audit;
    private final vn.edu.hcmute.uteexpress.service.OrderPaymentService payments;
    private final vn.edu.hcmute.uteexpress.service.routing.DispatchRoutingPolicy routing;
    private final vn.edu.hcmute.uteexpress.service.admin.StaffAccessPolicy staffAccess;
    public DispatchService(OrderRepository orders, AppUserRepository users, OrderStatusHistoryRepository history,
            ShipperNotificationRepository notifications, LogisticsAccess access, Clock clock, ApplicationEventPublisher events,
            vn.edu.hcmute.uteexpress.service.admin.AuditService audit,
            vn.edu.hcmute.uteexpress.service.OrderPaymentService payments,
            vn.edu.hcmute.uteexpress.service.routing.DispatchRoutingPolicy routing,
            vn.edu.hcmute.uteexpress.service.admin.StaffAccessPolicy staffAccess) {
        this.orders = orders; this.users = users; this.history = history; this.notifications = notifications;
        this.access = access; this.clock = clock; this.events = events;
        this.audit = audit;
        this.payments = payments;
        this.routing = routing; this.staffAccess = staffAccess;
    }
    public Page<Order> pending(String actor, int page) {
        return pending(actor, null, page);
    }
    public Page<Order> pending(String actor, Long branchId, int page) { return routing.pending(access.internal(actor), branchId, page); }
    public List<vn.edu.hcmute.uteexpress.entity.routing.Branch> branches(String actor) { return routing.branches(access.internal(actor)); }
    public Map<Long, List<vn.edu.hcmute.uteexpress.service.routing.DispatchRoutingPolicy.Candidate>> suggestions(String actor, List<Order> orders) {
        return routing.candidates(access.internal(actor), orders);
    }
    public List<String> unassigned(String actor, Long branchId) { return routing.unassigned(access.internal(actor), branchId); }
    public Page<AppUser> shippers(String actor, int page) {
        var manager = access.internal(actor); var all = new ArrayList<AppUser>();
        for (var branch : routing.branches(manager)) all.addAll(routing.branchShippers(branch.getId()).stream()
                .filter(u -> u.isEnabled() && !u.isLockedByAdmin()).toList());
        all.sort(Comparator.comparing(AppUser::getId)); var request = PageRequest.of(LogisticsInput.page(page), 100);
        int start = (int)Math.min(request.getOffset(), all.size()); int end = Math.min(start + request.getPageSize(), all.size());
        return new PageImpl<>(all.subList(start, end), request, all.size());
    }
    public long load(Long shipperId) { return routing.load(shipperId); }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void assign(String code, Long shipperId, long expectedShipperId, LocalDateTime expectedUpdatedAt,
            String note, String actor) {
        AppUser manager = access.internal(actor);
        String reason = LogisticsInput.text(note, 5, 350, "Lý do phân công");
        if (shipperId == null || shipperId <= 0 || expectedShipperId < 0) throw new IllegalArgumentException("Mã Shipper không hợp lệ.");
        // Khóa Shipper trước đơn: hai Manager cùng phân công không được vượt sức chứa.
        var shipper = users.lockById(shipperId).filter(u -> u.isEnabled() && !u.isLockedByAdmin() && u.getRole() == AppUser.Role.SHIPPER)
                .orElseThrow(() -> new IllegalArgumentException("Hãy chọn Shipper đang hoạt động."));
        var order = orders.findLockedByTrackingCode(code)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        payments.requireReadyForFulfillment(order);
        long current = order.getShipper() == null ? 0 : order.getShipper().getId();
        if (current != expectedShipperId || expectedUpdatedAt == null || !expectedUpdatedAt.equals(order.getUpdatedAt()))
            throw new IllegalArgumentException("Vận đơn đã thay đổi. Hãy tải lại trang trước khi phân công.");
        if (current == shipperId) throw new IllegalArgumentException("Đơn đã được phân công cho Shipper này.");
        assignLocked(order, shipper, reason, manager);
    }
    /** Điểm vào tương thích của service cũ; toàn bộ nghiệp vụ vẫn chỉ thực hiện ở đây. */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void assignById(String actor, Long orderId, vn.edu.hcmute.uteexpress.dto.manager.OrderAssignmentRequest form) {
        var manager = access.internal(actor); staffAccess.validateId(orderId); staffAccess.validate(form);
        String reason = LogisticsInput.text(form.getReason(), 5, 350, "Lý do phân công");
        var shipper = users.lockById(form.getShipperId()).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy Shipper."));
        var order = orders.findLockedById(orderId).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        if (order.getRevision() != form.getRevision()) throw new vn.edu.hcmute.uteexpress.exception.StaffConflictException("Đơn vừa được cập nhật. Hãy tải lại trang.");
        payments.requireReadyForFulfillment(order);
        assignLocked(order, shipper, reason, manager);
    }
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void assignSuggested(String code, String actor) {
        var manager = access.internal(actor);
        var snapshot = orders.findByTrackingCode(code).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        var route = routing.route(snapshot); routing.requireManager(manager, route);
        // Khóa theo ID cố định trước khi đọc tải, tránh hai lô chọn cùng chỗ trống cuối cùng.
        for (var user : routing.branchShippers(routing.branch(route).getId()))
            users.lockById(user.getId()).ifPresent(entityManager::refresh);
        var order = orders.findLockedByTrackingCode(code).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        entityManager.refresh(order); entityManager.refresh(route);
        if (order.getShipper() != null) throw new IllegalArgumentException("Đơn đã được phân công bởi thao tác khác.");
        payments.requireReadyForFulfillment(order);
        var choices = routing.candidates(manager, order);
        if (choices.isEmpty()) throw new IllegalArgumentException("Hết shipper còn sức chứa.");
        var selected = choices.get(0);
        assignLocked(order, users.findById(selected.id()).orElseThrow(), selected.reason(), manager);
    }
    /**
     * Thu hồi đơn từ Shipper đang giữ (nghỉ ca, nghỉ việc, ốm) và giao cho Shipper khác cùng chi nhánh: chỉ khi đã lấy hàng mà
     * chưa đi giao, hoặc vừa giao thất bại (xem DispatchRoutingPolicy.isReclaimable). Cùng cách khóa và kiểm tra như phân công.
     */
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public void reclaim(String code, Long shipperId, long expectedShipperId, LocalDateTime expectedUpdatedAt,
            String note, String actor) {
        AppUser manager = access.internal(actor);
        String reason = LogisticsInput.text(note, 5, 350, "Lý do thu hồi");
        if (shipperId == null || shipperId <= 0 || expectedShipperId <= 0) throw new IllegalArgumentException("Mã Shipper không hợp lệ.");
        var shipper = users.lockById(shipperId).filter(u -> u.isEnabled() && !u.isLockedByAdmin() && u.getRole() == AppUser.Role.SHIPPER)
                .orElseThrow(() -> new IllegalArgumentException("Hãy chọn Shipper đang hoạt động."));
        var order = orders.findLockedByTrackingCode(code).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        long current = order.getShipper() == null ? 0 : order.getShipper().getId();
        if (current != expectedShipperId || expectedUpdatedAt == null || !expectedUpdatedAt.equals(order.getUpdatedAt()))
            throw new IllegalArgumentException("Vận đơn đã thay đổi. Hãy tải lại trang trước khi thu hồi.");
        if (current == shipperId) throw new IllegalArgumentException("Hãy chọn Shipper khác Shipper đang giữ đơn.");
        payments.requireReadyForFulfillment(order);
        var route = routing.route(order); routing.requireReclaimable(route); routing.requireManager(manager, route);
        routing.requireShipper(shipper, route);
        var previous = order.getShipper();
        route.reassignHolder(shipper);
        handOver(order, shipper, manager, "Thu hồi từ " + name(previous) + ", chuyển cho " + name(shipper) + ". " + reason,
                "ORDER_REASSIGN", "Shipper " + previous.getId() + " -> " + shipper.getId() + "; " + reason);
    }
    /** Shipper cùng chi nhánh có thể nhận đơn thu hồi (đang hoạt động, khác người đang giữ), kèm số đơn đang giữ; đơn không thu hồi được thì rỗng. */
    public Map<Long, List<vn.edu.hcmute.uteexpress.service.routing.DispatchRoutingPolicy.Candidate>> reclaimOptions(String actor, List<Order> list) {
        var manager = access.internal(actor);
        var result = new LinkedHashMap<Long, List<vn.edu.hcmute.uteexpress.service.routing.DispatchRoutingPolicy.Candidate>>();
        int capacity = routing.capacity();
        for (var order : list) {
            var route = routing.routeIfPresent(order).orElse(null);
            if (route == null || !routing.isReclaimable(route) || !routing.canManage(manager, route)) continue;
            var options = routing.branchShippers(routing.branch(route).getId()).stream()
                    .filter(u -> u.isEnabled() && !u.isLockedByAdmin() && !u.getId().equals(order.getShipper().getId()))
                    .map(u -> new vn.edu.hcmute.uteexpress.service.routing.DispatchRoutingPolicy.Candidate(u.getId(), u.getUsername(),
                            u.getFullName(), routing.load(u.getId()), capacity, 0, 0, "", "Thu hồi"))
                    .filter(candidate -> candidate.activeOrders() < capacity)
                    .toList();
            result.put(order.getId(), options);
        }
        return result;
    }
    private static String name(AppUser user) { return user.getFullName() == null ? user.getUsername() : user.getFullName(); }
    private void assignLocked(Order order, AppUser shipper, String reason, AppUser manager) {
        var route = routing.route(order); routing.requireStage(route); routing.requireManager(manager, route);
        routing.requireShipper(shipper, route);
        if (order.getShipper() != null && order.getShipper().getId().equals(shipper.getId()))
            throw new IllegalArgumentException("Đơn đã được phân công cho Shipper này.");
        route.assignShipper(shipper);
        handOver(order, shipper, manager, "Phân công cho " + name(shipper) + ". " + reason,
                "ORDER_ASSIGN", "Shipper " + shipper.getId() + "; " + reason);
    }
    /** Ghi Shipper mới cho đơn, lịch sử, thông báo cho Shipper mới (thông báo cũ của đơn bị gỡ) và nhật ký. */
    private void handOver(Order order, AppUser shipper, AppUser manager, String historyNote, String auditAction, String auditDetail) {
        String actor = manager.getUsername();
        LocalDateTime now = LocalDateTime.now(clock);
        notifications.dismissPreviousAssignment(order, now);
        order.setShipper(shipper);
        order.setUpdatedAt(now);
        orders.save(order);
        history.save(new OrderStatusHistory(order, manager, order.getStatus(), order.getStatus(), null, historyNote, null, now));
        var notification = notifications.findByOrderAndRecipient(order, shipper).orElseGet(ShipperNotification::new);
        notification.assign(order, shipper, now);
        notifications.saveAndFlush(notification);
        events.publishEvent(new ShipperNotificationEvent(shipper.getUsername(), notification.getId(), order.getTrackingCode()));
        events.publishEvent(new vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent(
                order.getId(), order.getTrackingCode(), order.getStatus().name(), now));
        audit.record(actor, auditAction, "order:" + order.getId(), auditDetail);
    }
}
