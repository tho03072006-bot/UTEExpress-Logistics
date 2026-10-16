package vn.edu.hcmute.uteexpress.dto.tracking;

import java.time.LocalDateTime;

/** Dữ liệu chụp trong transaction để gửi email cho người gửi sau khi commit. */
public record SenderOrderUpdateEvent(String email, String recipientName, String trackingCode, Kind kind,
        String reason, LocalDateTime nextAttemptAt) {
    public enum Kind {
        PICKUP_FAILED("chưa lấy được hàng", "Shipper chưa lấy được hàng tại địa chỉ của bạn."),
        DELIVERED("đã giao thành công", "Người nhận đã nhận hàng."),
        DELIVERY_FAILED("giao chưa thành công", "Shipper chưa giao được hàng cho người nhận."),
        RETRY_APPROVED("đã có lịch giao lại", "Manager/Admin đã phê duyệt lịch giao lại cho vận đơn."),
        RETURN_STARTED("đang chuyển hoàn", "Vận đơn đang được bàn giao hoàn về người gửi."),
        RETURNED("đã hoàn trả", "Hàng đã được hoàn trả về người gửi.");
        private final String subject;
        private final String headline;
        Kind(String subject, String headline) { this.subject = subject; this.headline = headline; }
        public String subject() { return subject; }
        public String headline() { return headline; }
    }
}
