package vn.edu.hcmute.uteexpress.dto.tracking;

import java.time.LocalDateTime;

/**
 * Du lieu trang thai don duoc phat sau khi cap nhat thanh cong.
 */
public record OrderStatusChangedEvent(
        Long orderId,
        String trackingCode,
        String status,
        LocalDateTime updatedAt) {
}
