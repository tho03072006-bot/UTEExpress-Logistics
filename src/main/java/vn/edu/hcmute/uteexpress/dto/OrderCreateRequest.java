package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.validation.OrderRequestRules;
import vn.edu.hcmute.uteexpress.validation.ShipmentWeight;

/**
 * Du lieu nguoi dung nhap tu form "Tao don gui hang" (khong dung truc tiep
 * Entity Order de nhan du lieu tu form - tranh loi bind sai quan he sender/shipper).
 */
public class OrderCreateRequest {

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
    @Size(max = 100, message = "Tên người gửi tối đa 100 ký tự")
    private String senderName;

    @NotBlank(message = "Vui lòng nhập địa chỉ lấy hàng")
    @Size(max = 255, message = "Địa chỉ lấy hàng tối đa 255 ký tự")
    private String senderAddress;

    @NotBlank(message = "Vui lòng nhập tên người nhận")
    @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự")
    private String receiverName;

    @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
    @Pattern(regexp = OrderRequestRules.PHONE_PATTERN,
            message = "Số điện thoại phải bắt đầu bằng 0 và có 10 - 11 chữ số")
    private String receiverPhone;

    @NotBlank(message = "Vui lòng nhập địa chỉ giao hàng")
    @Size(max = 255, message = "Địa chỉ giao hàng tối đa 255 ký tự")
    private String receiverAddress;

    @NotNull(message = "Vui lòng nhập khối lượng")
    @ShipmentWeight
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
    @Size(max = 30, message = "Mã giảm giá tối đa 30 ký tự")
    private String promoCode;

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
        this.senderName = normalize(senderName);
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = normalize(senderAddress);
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = normalize(receiverName);
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = normalize(receiverPhone);
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = normalize(receiverAddress);
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
        this.promoCode = promoCode == null ? null : promoCode.strip().toUpperCase(Locale.ROOT);
    }

    public OrderPayment.PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(OrderPayment.PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    private static String normalize(String value) {
        return value == null ? null : value.strip();
    }
}
