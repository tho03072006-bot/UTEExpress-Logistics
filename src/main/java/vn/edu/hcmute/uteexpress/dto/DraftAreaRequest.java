package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

/** Chọn lại địa bàn cho đơn nháp cũ; không cho sửa chủ sở hữu hoặc trạng thái. */
public class DraftAreaRequest {
    @NotNull(message = "Thiếu phiên bản đơn nháp.") private LocalDateTime expectedUpdatedAt;
    @NotBlank(message = "Chọn tỉnh gửi.") @Size(max = 10) private String pickupProvinceCode;
    @NotBlank(message = "Chọn khu vực gửi.") @Size(max = 40) private String pickupAreaCode;
    @NotBlank(message = "Chọn tỉnh nhận.") @Size(max = 10) private String deliveryProvinceCode;
    @NotBlank(message = "Chọn khu vực nhận.") @Size(max = 40) private String deliveryAreaCode;
    public LocalDateTime getExpectedUpdatedAt() { return expectedUpdatedAt; }
    public void setExpectedUpdatedAt(LocalDateTime value) { expectedUpdatedAt = value; }
    public String getPickupProvinceCode() { return pickupProvinceCode; }
    public void setPickupProvinceCode(String value) { pickupProvinceCode = value; }
    public String getPickupAreaCode() { return pickupAreaCode; }
    public void setPickupAreaCode(String value) { pickupAreaCode = value; }
    public String getDeliveryProvinceCode() { return deliveryProvinceCode; }
    public void setDeliveryProvinceCode(String value) { deliveryProvinceCode = value; }
    public String getDeliveryAreaCode() { return deliveryAreaCode; }
    public void setDeliveryAreaCode(String value) { deliveryAreaCode = value; }
}
