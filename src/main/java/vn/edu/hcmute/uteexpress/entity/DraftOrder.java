package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Một đơn nháp nằm trong "giỏ đơn chờ xác nhận" của người gửi hàng - TV1 phụ trách (việc 2).
 *
 * VÌ SAO LÀ ENTITY RIÊNG CHỨ KHÔNG THÊM TRẠNG THÁI "DRAFT" VÀO Order:
 * enum Order.OrderStatus nằm trong Order.java - file dùng chung cả nhóm, quy ước là không
 * được sửa. Tách bảng riêng cũng sạch hơn về mặt nghiệp vụ: đơn nháp chưa có mã vận đơn,
 * chưa được phân shipper, không được xuất hiện trong thống kê của Manager hay danh sách
 * cần đi lấy hàng của Shipper. Khi người dùng bấm xác nhận, đơn nháp mới được chuyển
 * thành Order thật (qua OrderService.createOrder) rồi xoá khỏi giỏ.
 */
@Entity
@Table(name = "draft_order")
public class DraftOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chủ giỏ đơn - mỗi người dùng chỉ thấy giỏ của chính mình. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Nationalized
    @Column(name = "sender_name", nullable = false, length = 100)
    private String senderName;

    @Nationalized
    @Column(name = "sender_address", nullable = false, length = 255)
    private String senderAddress;

    @Nationalized
    @Column(name = "receiver_name", nullable = false, length = 100)
    private String receiverName;

    @Column(name = "receiver_phone", nullable = false, length = 20)
    private String receiverPhone;

    @Nationalized
    @Column(name = "receiver_address", nullable = false, length = 255)
    private String receiverAddress;

    @Column(name = "weight_kg", nullable = false)
    private Double weightKg;

    /**
     * Dùng lại enum ServiceType của Order để bảng giá và cách tính cước luôn khớp nhau.
     * Đây chỉ là tham chiếu tới enum có sẵn, không sửa gì trong Order.java.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 20)
    private Order.ServiceType serviceType = Order.ServiceType.STANDARD;

    /** Phương thức thanh toán đã chọn, giữ lại để lúc xác nhận tạo đúng bản ghi thanh toán. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private OrderPayment.PaymentMethod paymentMethod = OrderPayment.PaymentMethod.COD;

    /**
     * Mã giảm giá người dùng đã nhập, lưu nguyên chuỗi chứ không lưu khoá ngoại.
     * Mã được kiểm tra lại lúc bấm xác nhận - nếu tới lúc đó mã đã hết hạn hay hết lượt
     * thì người dùng được báo ngay, thay vì áp một mã không còn giá trị.
     */
    @Column(name = "promo_code", length = 30)
    private String promoCode;

    /**
     * Cước tạm tính lúc thêm vào giỏ, chỉ để hiển thị cho người dùng ước lượng.
     * Lúc bấm xác nhận, cước được tính lại từ đầu nên nếu nhóm đổi bảng giá thì
     * đơn thật vẫn ăn theo giá mới, không ăn theo giá cũ lưu trong giỏ.
     */
    @Column(name = "estimated_fee", precision = 12, scale = 0)
    private BigDecimal estimatedFee;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public DraftOrder() {
    }

    // ----- Getter / Setter -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public AppUser getUser() {
        return user;
    }

    public void setUser(AppUser user) {
        this.user = user;
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

    public OrderPayment.PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(OrderPayment.PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public void setPromoCode(String promoCode) {
        this.promoCode = promoCode;
    }

    public BigDecimal getEstimatedFee() {
        return estimatedFee;
    }

    public void setEstimatedFee(BigDecimal estimatedFee) {
        this.estimatedFee = estimatedFee;
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
