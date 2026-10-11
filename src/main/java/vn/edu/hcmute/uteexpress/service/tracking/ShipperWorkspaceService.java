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
    public ShipperWorkspaceService(LogisticsAccess access, OrderRepository orders, OrderPaymentRepository payments,
            OrderStatusHistoryRepository history, Clock clock) {
        this.access = access; this.orders = orders; this.payments = payments; this.history = history; this.clock = clock;
    }
    public Map<String, Long> counts(String username) {
        var shipper = access.shipper(username);
        return Map.of("pending", orders.countByShipperAndStatus(shipper, Order.OrderStatus.PENDING_PICKUP),
                "pickedUp", orders.countByShipperAndStatus(shipper, Order.OrderStatus.PICKED_UP),
                "inTransit", orders.countByShipperAndStatus(shipper, Order.OrderStatus.IN_TRANSIT),
                "retryDue", orders.countDueRetries(shipper, LocalDateTime.now(clock)));
    }
    public Page<Order> work(String username, ShipperWorkView work, String keyword, Order.OrderStatus status,
            LocalDate from, LocalDate to, int page) {
        var shipper = access.shipper(username);
        String term = LogisticsInput.text(keyword, 0, 100, "Từ khóa");
        if (from != null && to != null && from.isAfter(to)) throw new IllegalArgumentException("Từ ngày không được sau đến ngày.");
        for (var date : new LocalDate[]{from, to}) if (date != null && (date.getYear() < 2000 || date.getYear() > 2100))
            throw new IllegalArgumentException("Ngày lọc phải nằm trong năm 2000–2100.");
        String escaped = term.isEmpty() ? null : term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var now = LocalDateTime.now(clock);
        var request = PageRequest.of(LogisticsInput.page(page), 10);
        var result = orders.searchWorkOrders(shipper, status, escaped, from == null ? null : from.atStartOfDay(),
                to == null ? null : to.plusDays(1).atStartOfDay(), work == ShipperWorkView.TODAY ? 1 : 2,
                now, now.toLocalDate().plusDays(1).atStartOfDay(), request);
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
    public Map<Long, OrderStatusHistory> retries(String username, List<Order> visible) {
        var result = new HashMap<Long, OrderStatusHistory>();
        access.checkOrders(visible, username);
        if (!visible.isEmpty()) for (var entry : history.latestFailures(visible.stream().map(Order::getId).toList()))
            result.put(entry.getOrder().getId(), entry);
        return result;
    }
    /** Lần lấy hàng thất bại gần nhất của các đơn đang hiển thị. */
    public Map<Long, OrderStatusHistory> pickupFailures(String username, List<Order> visible) {
        var result = new HashMap<Long, OrderStatusHistory>();
        access.checkOrders(visible, username);
        if (!visible.isEmpty()) for (var entry : history.latestPickupFailures(visible.stream().map(Order::getId).toList()))
            result.put(entry.getOrder().getId(), entry);
        return result;
    }
    public Map<Long, Long> pickupCounts(String username, List<Order> visible) {
        var result = new HashMap<Long, Long>();
        access.checkOrders(visible, username);
        if (!visible.isEmpty()) for (var count : history.pickupFailureCounts(visible.stream().map(Order::getId).toList()))
            result.put(count.orderId(), count.count());
        return result;
    }
    public LocalDateTime now() { return LocalDateTime.now(clock); }
}
