package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.List;
import java.util.Optional;

import vn.edu.hcmute.uteexpress.entity.Order;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;

public interface TrackingService {

    /**
     * Lấy danh sách đơn được phân công cho shipper đang đăng nhập.
     */
    List<Order> findAssignedOrders(String shipperUsername);

    Page<Order> searchAssignedOrders(String shipperUsername, String keyword,
            Order.OrderStatus status, LocalDate from, LocalDate to, int page);

    Order updateStatus(Long orderId, String shipperUsername, Order.OrderStatus newStatus,
            DeliveryFailureReason failureReason, String note, LocalDateTime nextAttemptAt);

    boolean hasCurrentProof(Order order);

    boolean canRetry(Order order);

    /** Tối đa 3 lần lấy hàng thất bại cho mỗi vận đơn. */
    int MAX_PICKUP_FAILURES = 3;

    /** Ghi nhận lấy hàng thất bại; đơn vẫn chờ lấy hàng, có lý do và hẹn lấy lại nếu có. */
    vn.edu.hcmute.uteexpress.entity.OrderStatusHistory reportPickupFailure(Long orderId, String shipperUsername,
            DeliveryFailureReason reason, String note, LocalDateTime nextAttemptAt);

    long pickupFailures(Order order);

    boolean canReportPickupFailure(Order order);

    Optional<Order> findAssignedOrderByCode(String code, String username);

    /**
     * Lấy chi tiết một đơn nếu đơn đó được phân công cho shipper.
     */
    Optional<Order> findAssignedOrder(
            Long orderId, String shipperUsername);

    /**
     * Cập nhật trạng thái nếu đơn thuộc Shipper và bước chuyển hợp lệ.
     */
    Order updateStatus(
            Long orderId,
            String shipperUsername,
            Order.OrderStatus newStatus);
}
