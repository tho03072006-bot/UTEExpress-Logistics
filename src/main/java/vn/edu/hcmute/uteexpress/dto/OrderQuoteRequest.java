package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.validation.ShippingInputValidation;

/** Chỉ nhận dữ liệu cần báo giá; không nhận cước hoặc tọa độ do trình duyệt gửi. */
public class OrderQuoteRequest {
    @NotBlank(message="Vui lòng nhập địa chỉ lấy hàng.")
    @Size(min=5, max=255, message="Địa chỉ lấy hàng từ 5–255 ký tự.")
    @Pattern(regexp=ShippingInputValidation.ADDRESS, message="Địa chỉ không được chứa ký tự điều khiển.")
    private String senderAddress;
    @NotBlank(message="Vui lòng nhập địa chỉ giao hàng.")
    @Size(min=5, max=255, message="Địa chỉ giao hàng từ 5–255 ký tự.")
    @Pattern(regexp=ShippingInputValidation.ADDRESS, message="Địa chỉ không được chứa ký tự điều khiển.")
    private String receiverAddress;
    @NotNull(message="Vui lòng nhập khối lượng.")
    @DecimalMin(value=ShippingInputValidation.MIN_WEIGHT, message="Khối lượng tối thiểu 0,01 kg.")
    @DecimalMax(value=ShippingInputValidation.MAX_WEIGHT, message="Khối lượng tối đa 100 kg.")
    private Double weightKg;
    @NotNull(message="Vui lòng chọn dịch vụ.")
    private Order.ServiceType serviceType;
    @AssertTrue(message="Khối lượng phải là số hữu hạn.")
    public boolean isWeightFinite() { return weightKg == null || Double.isFinite(weightKg); }
    public String getSenderAddress() { return senderAddress; }
    public void setSenderAddress(String value) { senderAddress=value==null?null:value.strip(); }
    public String getReceiverAddress() { return receiverAddress; }
    public void setReceiverAddress(String value) { receiverAddress=value==null?null:value.strip(); }
    public Double getWeightKg() { return weightKg; }
    public void setWeightKg(Double value) { weightKg=value; }
    public Order.ServiceType getServiceType() { return serviceType; }
    public void setServiceType(Order.ServiceType value) { serviceType=value; }
}
