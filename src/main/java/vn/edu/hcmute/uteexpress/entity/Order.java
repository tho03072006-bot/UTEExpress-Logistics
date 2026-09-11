package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity van don (lenh van chuyen) - trung tam nghiep vu cua UTEExpress.
 * TV1 (tao don), TV2 (cap nhat trang thai), TV3 (thong ke) deu thao tac tren entity nay,
 * nen KHONG sua truc tiep field o day khi chua thong bao ca nhom - tranh conflict Git.
 */
@Entity
@Table(name = "shipment_order")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tracking_code", nullable = false, unique = true, length = 20)
    private String trackingCode;

    @ManyToOne
    @JoinColumn(name = "sender_id", nullable = false)
    private AppUser sender; // User tao don

    @ManyToOne
    @JoinColumn(name = "shipper_id")
    private AppUser shipper; // Shipper duoc phan cong, co the null khi chua phan cong

    @Column(name = "sender_name", length = 100)
    private String senderName;

    @Column(name = "sender_address", length = 255)
    private String senderAddress;

    @Column(name = "receiver_name", length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", length = 20)
    private String receiverPhone;

    @Column(name = "receiver_address", length = 255)
    private String receiverAddress;

    @Column(name = "weight_kg")
    private Double weightKg;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", length = 20)
    private ServiceType serviceType = ServiceType.STANDARD;

    @Column(name = "shipping_fee", precision = 12, scale = 0)
    private BigDecimal shippingFee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING_PICKUP;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum ServiceType {
        STANDARD, EXPRESS, SUPER_EXPRESS
    }

    public enum OrderStatus {
        PENDING_PICKUP, PICKED_UP, IN_TRANSIT, DELIVERED, CANCELLED, RETURNED
    }

    public Order() {
    }

    // ----- Getter / Setter -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTrackingCode() {
        return trackingCode;
    }

    public void setTrackingCode(String trackingCode) {
        this.trackingCode = trackingCode;
    }

    public AppUser getSender() {
        return sender;
    }

    public void setSender(AppUser sender) {
        this.sender = sender;
    }

    public AppUser getShipper() {
        return shipper;
    }

    public void setShipper(AppUser shipper) {
        this.shipper = shipper;
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

    public ServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(ServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
