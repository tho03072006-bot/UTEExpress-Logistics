package vn.edu.hcmute.uteexpress.dto.tracking;

/**
 * Lý do giao/lấy hàng không thành công.
 * Cột failure_reason trên Aiven là ENUM nên không thêm hằng mới; lấy hàng thất bại
 * dùng lại cùng hằng nhưng hiển thị nhãn dành cho người gửi.
 */
public enum DeliveryFailureReason {
    CUSTOMER_ABSENT("Khách không có mặt", "Người gửi không có mặt"),
    PHONE_UNREACHABLE("Không liên hệ được khách", "Không liên hệ được người gửi"),
    WRONG_ADDRESS("Địa chỉ không chính xác", "Địa chỉ lấy hàng không chính xác"),
    REFUSED("Khách từ chối nhận hàng", "Người gửi hoãn hoặc không gửi hàng"),
    DAMAGED("Hàng hóa bị hư hỏng", "Hàng chưa đóng gói đúng hoặc bị hư hỏng"),
    OTHER("Lý do khác", "Lý do khác");

    private final String label;
    private final String pickupLabel;
    DeliveryFailureReason(String label, String pickupLabel) { this.label = label; this.pickupLabel = pickupLabel; }
    public String getLabel() { return label; }
    public String getPickupLabel() { return pickupLabel; }
    public String labelFor(boolean pickup) { return pickup ? pickupLabel : label; }
}
