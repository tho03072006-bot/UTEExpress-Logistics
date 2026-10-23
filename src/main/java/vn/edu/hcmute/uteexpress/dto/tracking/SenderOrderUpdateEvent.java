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
        RETURNED("đã hoàn trả", "Hàng đã được hoàn trả về người gửi."),
        AT_ORIGIN_HUB("đã nhập kho gửi", "Vận đơn đã được nhập kho chi nhánh gửi, chờ chuyến trung chuyển."),
        IN_LINEHAUL("đang trung chuyển", "Vận đơn đang trên chuyến trung chuyển tới chi nhánh nhận."),
        AT_DEST_HUB("đã tới kho nhận", "Vận đơn đã tới kho chi nhánh nhận, chờ shipper giao."),
        RECEIVED_AT_COUNTER("đã nhận tại quầy", "Chi nhánh đã nhận hàng bạn mang tới quầy.");
        private final String subject;
        private final String headline;
        Kind(String subject, String headline) { this.subject = subject; this.headline = headline; }
        public String subject() { return subject; }
        public String headline() { return headline; }
    }
}
