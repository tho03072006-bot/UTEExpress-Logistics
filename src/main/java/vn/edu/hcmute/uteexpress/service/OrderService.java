package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.Order;

import java.util.List;
import java.util.Optional;

/**
 * Nghiep vu van don - vai tro User (TV1 phu trach, muc 03 ke hoach).
 */
public interface OrderService {

    Order createOrder(OrderCreateRequest request, String senderUsername);

    List<Order> findOrdersOfUser(String username);

    /**
     * Loc lich su don theo trang thai. statusFilter = null nghia la lay tat ca (khong loc).
     */
    List<Order> findOrdersOfUser(String username, Order.OrderStatus statusFilter);

    Optional<Order> findByTrackingCode(String trackingCode);

    /**
     * Nguoi gui tu huy don khi don chua duoc lay hang (PENDING_PICKUP).
     * Nem IllegalStateException neu don khong thuoc ve username nay hoac da qua trang thai cho huy.
     */
    void cancelOrder(Long orderId, String username);
}
