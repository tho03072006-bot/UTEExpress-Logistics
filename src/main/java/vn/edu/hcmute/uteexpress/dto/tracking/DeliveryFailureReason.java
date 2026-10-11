package vn.edu.hcmute.uteexpress.dto.tracking;

public enum DeliveryFailureReason {
    CUSTOMER_ABSENT("Khách không có mặt"),
    PHONE_UNREACHABLE("Không liên hệ được khách"),
    WRONG_ADDRESS("Địa chỉ không chính xác"),
    REFUSED("Khách từ chối nhận hàng"),
    DAMAGED("Hàng hóa bị hư hỏng"),
    OTHER("Lý do khác");

    private final String label;
    DeliveryFailureReason(String label) { this.label = label; }
    public String getLabel() { return label; }
}
