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
