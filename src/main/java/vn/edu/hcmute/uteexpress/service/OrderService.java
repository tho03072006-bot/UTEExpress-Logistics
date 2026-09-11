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

    Optional<Order> findByTrackingCode(String trackingCode);
}
