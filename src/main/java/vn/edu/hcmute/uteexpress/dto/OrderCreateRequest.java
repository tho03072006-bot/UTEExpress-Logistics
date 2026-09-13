package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import vn.edu.hcmute.uteexpress.entity.Order;

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
    private String senderName;

    @NotBlank(message = "Vui lòng nhập địa chỉ lấy hàng")
    private String senderAddress;

    @NotBlank(message = "Vui lòng nhập tên người nhận")
    private String receiverName;

    @NotBlank(message = "Vui lòng nhập số điện thoại người nhận")
    private String receiverPhone;

    @NotBlank(message = "Vui lòng nhập địa chỉ giao hàng")
    private String receiverAddress;

    @NotNull(message = "Vui lòng nhập khối lượng")
    @Positive(message = "Khối lượng phải lớn hơn 0")
    private Double weightKg;

    @NotNull(message = "Vui lòng chọn dịch vụ")
    private Order.ServiceType serviceType;

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
        this.senderName = senderName;
    }

    public String getSenderAddress() {
        return senderAddress;
    }

    public void setSenderAddress(String senderAddress) {
        this.senderAddress = senderAddress;
    }

    public String getReceiverName() {
        return receiverName;
    }

    public void setReceiverName(String receiverName) {
        this.receiverName = receiverName;
    }

    public String getReceiverPhone() {
        return receiverPhone;
    }

    public void setReceiverPhone(String receiverPhone) {
        this.receiverPhone = receiverPhone;
    }

    public String getReceiverAddress() {
        return receiverAddress;
    }

    public void setReceiverAddress(String receiverAddress) {
        this.receiverAddress = receiverAddress;
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
}
