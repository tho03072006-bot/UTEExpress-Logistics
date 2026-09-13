package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.List;

import vn.edu.hcmute.uteexpress.entity.Order;

public interface TrackingService {

    /**
     * Lấy danh sách đơn được phân công cho shipper đang đăng nhập.
     */
    List<Order> findAssignedOrders(String shipperUsername);
}