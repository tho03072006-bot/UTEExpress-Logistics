package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;

/**
 * Du lieu nguoi dung nhap tu form "Tao don gui hang" (khong dung truc tiep
 * Entity Order de nhan du lieu tu form - tranh loi bind sai quan he sender/shipper).
 */
public class OrderCreateRequest {
    @NotBlank(message = "Chọn tỉnh/thành gửi.") @Size(max = 10) private String pickupProvinceCode;
    @NotBlank(message = "Chọn khu vực lấy hàng.") @Size(max = 40) private String pickupAreaCode;
    @NotBlank(message = "Chọn tỉnh/thành nhận.") @Size(max = 10) private String deliveryProvinceCode;
    @NotBlank(message = "Chọn khu vực giao hàng.") @Size(max = 40) private String deliveryAreaCode;
    public String getPickupProvinceCode() { return pickupProvinceCode; }
    public void setPickupProvinceCode(String value) { pickupProvinceCode = value; }
    public String getPickupAreaCode() { return pickupAreaCode; }
    public void setPickupAreaCode(String value) { pickupAreaCode = value; }
    public String getDeliveryProvinceCode() { return deliveryProvinceCode; }
    public void setDeliveryProvinceCode(String value) { deliveryProvinceCode = value; }
    public String getDeliveryAreaCode() { return deliveryAreaCode; }
    public void setDeliveryAreaCode(String value) { deliveryAreaCode = value; }
    /** Shipper đến lấy (mặc định) hay khách tự mang tới quầy chi nhánh gửi; vẫn bắt nhập địa chỉ người gửi. */
    @NotNull(message = "Chọn hình thức gửi hàng.")
    private vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode pickupMode = vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode.COURIER;
    public vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode getPickupMode() { return pickupMode; }
    public void setPickupMode(vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.PickupMode value) { pickupMode = value; }

    @Pattern(regexp="[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}",message="Phiên biểu mẫu không hợp lệ. Hãy mở lại trang tạo đơn.")
    private String submissionToken;
    public String getSubmissionToken() { return submissionToken; }
    public void setSubmissionToken(String value) { submissionToken=value; }

    /**
     * Id dia chi vua chon o o "Chon tu so dia chi" phia nguoi gui.
     * Chi phuc vu giao dien: giu nguyen lua chon tren dropdown khi form bi tra ve
     * vi loi validation. Du lieu that luu vao don van la senderName/senderAddress ben duoi,
     * vi nguoi dung co the chon dia chi roi sua lai vai chu truoc khi bam tao don.
     */
    private Long senderAddressId;

    /** Tuong tu senderAddressId nhung cho o chon dia chi nguoi nhan. */
    private Long receiverAddressId;

    @NotBlank(message = "Vui lòng nhập tên người gửi")
    @Size(min = 2, max = 100, message = "Tên người gửi từ 2–100 ký tự.")
    @Pattern(regexp = "[\\p{L}\\p{M} .'-]+", message = "Tên người gửi chỉ chứa chữ, khoảng trắng, dấu chấm/gạch nối/nháy.")
    private String senderName;

    @NotBlank(message = "Vui lòng nhập địa chỉ lấy hàng")
    @Size(min = 5, max = 255, message = "Địa chỉ lấy hàng từ 5–255 ký tự.")
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Địa chỉ không được chứa ký tự điều khiển.")
    private String senderAddress;

    @NotBlank(message = "Vui lòng nhập tên người nhận")
    @Size(min = 2, max = 100, message = "Tên người nhận từ 2–100 ký tự.")
    @Pattern(regexp = "[\\p{L}\\p{M} .'-]+", message = "Tên người nhận chỉ chứa chữ, khoảng trắng, dấu chấm/gạch nối/nháy.")
    private String receiverName;

    @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
    @Pattern(regexp = "0[35789][0-9]{8}", message = "Nhập số di động Việt Nam 10 số hoặc dạng +84.")
    private String receiverPhone;

    @NotBlank(message = "Vui lòng nhập địa chỉ giao hàng")
    @Size(min = 5, max = 255, message = "Địa chỉ giao hàng từ 5–255 ký tự.")
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Địa chỉ không được chứa ký tự điều khiển.")
    private String receiverAddress;

    @NotNull(message = "Vui lòng nhập khối lượng")
    @Positive(message = "Khối lượng phải lớn hơn 0")
    @DecimalMin(value="0.01",message="Khối lượng tối thiểu 0,01 kg.")
    @DecimalMax(value = "100", message = "Khối lượng tối đa 100 kg.")
    private Double weightKg;

    @NotNull(message = "Vui lòng chọn dịch vụ")
    private Order.ServiceType serviceType;

    @NotNull(message = "Vui lòng chọn phương thức thanh toán")
    private OrderPayment.PaymentMethod paymentMethod = OrderPayment.PaymentMethod.COD;

    /**
     * Mã giảm cước, không bắt buộc. Để trống nghĩa là không áp mã nào.
     * Mã sai/hết hạn được báo lỗi ở Service chứ không validate bằng annotation,
     * vì còn phải tra CSDL và so với cước của chính đơn này.
     */
    @Size(max = 30, message = "Mã khuyến mãi tối đa 30 ký tự.")
    @Pattern(regexp = "([A-Za-z0-9][A-Za-z0-9_-]{2,29})?", message = "Mã khuyến mãi chỉ gồm chữ/số/gạch ngang/gạch dưới, từ 3–30 ký tự.")
    private String promoCode;

    @AssertTrue(message = "Khối lượng phải là số hữu hạn.")
    public boolean isWeightFinite() { return weightKg == null || Double.isFinite(weightKg); }

    @AssertTrue(message = "Chỉ hỗ trợ COD hoặc MoMo mô phỏng. Vui lòng chọn lại phương thức thanh toán.")
    public boolean isPaymentMethodSupported() {
        return paymentMethod == null || paymentMethod == OrderPayment.PaymentMethod.COD || paymentMethod == OrderPayment.PaymentMethod.MOMO;
    }

    public OrderCreateRequest() {
    }

    // ----- Getter / Setter -----

    public Long getSenderAddressId() {
        return senderAddressId;
    }

    public void setSenderAddressId(Long senderAddressId) {
        this.senderAddressId = senderAddressId;
    }

    public Long getReceiverAddressId() {
        return receiverAddressId;
    }

    public void setReceiverAddressId(Long receiverAddressId) {
        this.receiverAddressId = receiverAddressId;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName == null ? null : senderName.strip();
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress == null ? null : senderAddress.strip();
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName == null ? null : receiverName.strip();
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone == null ? null : receiverPhone.strip().replace(" ", "").replace("-", "");
        if (this.receiverPhone != null && this.receiverPhone.startsWith("+84")) {
            this.receiverPhone = "0" + this.receiverPhone.substring(3);
        }
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress == null ? null : receiverAddress.strip();
    }

    public Double getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(Double weightKg) {
        this.weightKg = weightKg;
    }

    public Order.ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(Order.ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode == null ? null : promoCode.strip();
    }

    public OrderPayment.PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(OrderPayment.PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }
}
