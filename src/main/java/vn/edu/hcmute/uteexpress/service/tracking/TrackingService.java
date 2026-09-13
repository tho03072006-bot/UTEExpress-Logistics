package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.List;
import java.util.Optional;

import vn.edu.hcmute.uteexpress.entity.Order;

public interface TrackingService {

    /**
     * Lấy danh sách đơn được phân công cho shipper đang đăng nhập.
     */
    List<Order> findAssignedOrders(String shipperUsername);

    /**
     * Lấy chi tiết một đơn nếu đơn đó được phân công cho shipper.
     */
    Optional<Order> findAssignedOrder(
            Long orderId, String shipperUsername);
}