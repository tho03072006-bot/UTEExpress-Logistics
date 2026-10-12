package vn.edu.hcmute.uteexpress.validation;

import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;

/** Service-level invariants also protect callers that do not pass through MVC validation. */
public final class OrderRequestRules {
    public static final String PHONE_PATTERN = "^0[0-9]{9,10}$";

    private OrderRequestRules() {}

    public static void validate(OrderCreateRequest request) {
        if (request == null) {
            throw new IllegalStateException("Thông tin đơn hàng không hợp lệ.");
        }
        requireText(request.getSenderName(), 100, "Tên người gửi");
        requireText(request.getReceiverName(), 100, "Tên người nhận");
        requireText(request.getSenderAddress(), 255, "Địa chỉ lấy hàng");
        requireText(request.getReceiverAddress(), 255, "Địa chỉ giao hàng");
        if (request.getReceiverPhone() == null || !request.getReceiverPhone().matches(PHONE_PATTERN)) {
            throw new IllegalStateException("Số điện thoại phải bắt đầu bằng 0 và có 10 - 11 chữ số.");
        }
        requireWeight(request.getWeightKg());
        if (request.getServiceType() == null) {
            throw new IllegalStateException("Vui lòng chọn dịch vụ.");
        }
        if (request.getPaymentMethod() == null) {
            throw new IllegalStateException("Vui lòng chọn phương thức thanh toán.");
        }
        if (request.getPromoCode() != null && request.getPromoCode().length() > 30) {
            throw new IllegalStateException("Mã giảm giá tối đa 30 ký tự.");
        }
    }

    public static void requireWeight(Double weightKg) {
        if (weightKg == null || !ShipmentWeightValidator.isSupportedWeight(weightKg)) {
            throw new IllegalStateException("Khối lượng phải từ 0,1 đến 100 kg, tối đa 3 chữ số thập phân.");
        }
    }

    private static void requireText(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalStateException(label + " không được để trống và tối đa " + maxLength + " ký tự.");
        }
    }
}
