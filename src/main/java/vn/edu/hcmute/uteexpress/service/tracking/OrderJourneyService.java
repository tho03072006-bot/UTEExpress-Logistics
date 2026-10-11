package vn.edu.hcmute.uteexpress.service.tracking;

import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.*;

@Service
@Transactional(readOnly = true)
public class OrderJourneyService {
    private final OrderRepository orders;
    private final OrderStatusHistoryRepository history;
    private final LogisticsAccess access;
    public OrderJourneyService(OrderRepository orders, OrderStatusHistoryRepository history, LogisticsAccess access) {
        this.orders = orders; this.history = history; this.access = access;
    }
    public Order accessibleOrder(Long id, String username) {
        var order = orders.findById(id).orElseThrow(() -> new IllegalArgumentException("Không tìm thấy vận đơn."));
        access.checkOrder(order, username);
        return order;
    }
    public Page<OrderStatusHistory> history(Long id, String username, int page) {
        var order = accessibleOrder(id, username);
        return history.findByOrder(order, PageRequest.of(LogisticsInput.page(page), 20,
                Sort.by("occurredAt", "id").descending()));
    }
}
