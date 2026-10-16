package vn.edu.hcmute.uteexpress.service.tracking;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.tracking.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.*;

@Service
@Transactional(readOnly = true)
public class ShipperWorkspaceService {
    private final LogisticsAccess access;
    private final OrderRepository orders;
    private final OrderPaymentRepository payments;
    private final OrderStatusHistoryRepository history;
    private final Clock clock;
    private final vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService policies;
    private final vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService operations;
    public ShipperWorkspaceService(LogisticsAccess access, OrderRepository orders, OrderPaymentRepository payments,
            OrderStatusHistoryRepository history, Clock clock,
            vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService policies,
            vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService operations) {
        this.access = access; this.orders = orders; this.payments = payments; this.history = history; this.clock = clock;
        this.policies = policies; this.operations = operations;
    }
    public Map<String, Long> counts(String username) {
        var shipper = access.shipper(username);
        var byStatus = new EnumMap<Order.OrderStatus, Long>(Order.OrderStatus.class);
        for (var row : orders.countStatuses(shipper, List.of(Order.OrderStatus.PENDING_PICKUP,
                Order.OrderStatus.PICKED_UP, Order.OrderStatus.IN_TRANSIT))) byStatus.put(row.status(), row.count());
        return Map.of("pending", byStatus.getOrDefault(Order.OrderStatus.PENDING_PICKUP, 0L),
                "pickedUp", byStatus.getOrDefault(Order.OrderStatus.PICKED_UP, 0L),
                "inTransit", byStatus.getOrDefault(Order.OrderStatus.IN_TRANSIT, 0L),
                "retryDue", orders.countDueRetries(shipper, LocalDateTime.now(clock), policies.current().getMaxDeliveryAttempts()));
    }
    public Page<Order> work(String username, ShipperWorkView work, String keyword, Order.OrderStatus status,
            LocalDate from, LocalDate to, int page) {
        var shipper = access.shipper(username);
        String escaped = LogisticsInput.workFilter(keyword, from, to, page);
        var now = LocalDateTime.now(clock);
        var request = PageRequest.of(page, 10);
        var result = orders.searchWorkOrders(shipper, status, escaped, from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(), work == ShipperWorkView.TODAY ? 1 : 2,
                work == ShipperWorkView.TODAY ? now.toLocalDate().plusDays(1).atStartOfDay().minusNanos(1000) : now,
                now.toLocalDate().plusDays(1).atStartOfDay(), policies.current().getMaxDeliveryAttempts(), now, request);
        if (result.getTotalPages() > 0 && page >= result.getTotalPages()) return work(username, work, keyword, status, from, to, result.getTotalPages() - 1);
        return result;
    }
    public Map<Long, ShipperMoneyView> money(String username, List<Order> visible) {
        var result = new HashMap<Long, ShipperMoneyView>();
        access.checkOrders(visible, username);
        for (var order : visible) result.put(order.getId(), ShipperMoneyView.from(null));
        if (!visible.isEmpty()) for (var payment : payments.findByOrderIdIn(visible.stream().map(Order::getId).toList()))
            result.put(payment.getOrder().getId(), ShipperMoneyView.from(payment));
        return result;
    }

    /**
     * Thất bại của các đơn đang hiển thị: lần giao thất bại gần nhất (để hẹn giao lại), lần lấy hàng thất bại
     * gần nhất và số lần của từng loại. Đọc bằng một truy vấn thay vì ba.
     */
    public record Failures(Map<Long, OrderStatusHistory> retries, Map<Long, OrderStatusHistory> pickups,
            Map<Long, Long> pickupCounts, Map<Long, Long> deliveryCounts, Set<Long> retryable) {
        public boolean canRetry(Order order) {
            return retryable.contains(order.getId());
        }
        public boolean canReportPickupFailure(Order order) {
            return order.getStatus() == Order.OrderStatus.PENDING_PICKUP
                    && pickupCounts.getOrDefault(order.getId(), 0L) < TrackingService.MAX_PICKUP_FAILURES;
        }
    }

    public Failures failures(String username, List<Order> visible) {
        var result = new Failures(new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashSet<>());
        access.checkOrders(visible, username);
        if (visible.isEmpty()) return result;
        for (var entry : history.failureEntries(visible.stream().map(Order::getId).toList())) {
            Long id = entry.getOrder().getId();
            if (entry.isPickupFailure()) {
                result.pickups().put(id, entry);
                result.pickupCounts().merge(id, 1L, Long::sum);
            } else {
                result.retries().put(id, entry);
                result.deliveryCounts().merge(id, 1L, Long::sum);
            }
        }
        for (var order : visible) if (operations.canRetry(order)) result.retryable().add(order.getId());
        return result;
    }
    public LocalDateTime now() { return LocalDateTime.now(clock); }
}
