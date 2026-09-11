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

    @NotBlank(message = "Vui long nhap ten nguoi gui")
    private String senderName;

    @NotBlank(message = "Vui long nhap dia chi lay hang")
    private String senderAddress;

    @NotBlank(message = "Vui long nhap ten nguoi nhan")
    private String receiverName;

    @NotBlank(message = "Vui long nhap so dien thoai nguoi nhan")
    private String receiverPhone;

    @NotBlank(message = "Vui long nhap dia chi giao hang")
    private String receiverAddress;

    @NotNull(message = "Vui long nhap khoi luong")
    @Positive(message = "Khoi luong phai lon hon 0")
    private Double weightKg;

    @NotNull(message = "Vui long chon dich vu")
    private Order.ServiceType serviceType;

    public OrderCreateRequest() {
    }

    // ----- Getter / Setter -----

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
