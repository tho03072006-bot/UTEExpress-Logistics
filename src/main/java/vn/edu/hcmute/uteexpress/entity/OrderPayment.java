package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thông tin thanh toán cước của một vận đơn - TV1 phụ trách (việc 3).
 *
 * VÌ SAO TÁCH BẢNG RIÊNG THAY VÌ THÊM CỘT VÀO Order:
 * Order.java là entity dùng chung cả nhóm, quy ước là không tự thêm/sửa field.
 * Tách riêng cũng đúng nghiệp vụ hơn - thanh toán là một mối quan tâm độc lập với
 * hành trình giao hàng, và sau này muốn lưu nhiều lần thử thanh toán cho một đơn
 * thì chỉ cần bỏ ràng buộc unique ở đây, không phải đụng vào Order.
 *
 * Mỗi vận đơn có đúng một bản ghi thanh toán, tạo ngay lúc tạo đơn.
 */
@Entity
@Table(name = "order_payment")
public class OrderPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentMethod method = PaymentMethod.COD;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.UNPAID;

    /** Số tiền phải trả, chốt tại thời điểm tạo đơn (bằng cước phí của vận đơn). */
    @Column(nullable = false, precision = 12, scale = 0)
    private BigDecimal amount;

    /**
     * Mã giao dịch. Với ví điện tử mô phỏng thì đây là mã do hệ thống tự sinh,
     * KHÔNG phải mã từ cổng thanh toán thật - đồ án không tích hợp API thật.
     */
    @Column(name = "transaction_ref", length = 50)
    private String transactionRef;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    /**
     * Phương thức thanh toán.
     * onlineWallet = true nghĩa là phải trả trước qua trang thanh toán mô phỏng;
     * COD thì thu tiền lúc giao hàng nên không có bước đó.
     */
    public enum PaymentMethod {
        COD("Thanh toán khi nhận hàng (COD)", false),
        VNPAY("Ví VNPay", true),
        MOMO("Ví MoMo", true);

        private final String displayName;
        private final boolean onlineWallet;

        PaymentMethod(String displayName, boolean onlineWallet) {
            this.displayName = displayName;
            this.onlineWallet = onlineWallet;
        }

        public String getDisplayName() {
            return displayName;
        }

        public boolean isOnlineWallet() {
            return onlineWallet;
        }
    }

    public enum PaymentStatus {
        UNPAID("Chưa thanh toán"),
        PAID("Đã thanh toán");

        private final String displayName;

        PaymentStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public OrderPayment() {
    }

    // ----- Getter / Setter -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public PaymentMethod getMethod() {
        return method;
    }

    public void setMethod(PaymentMethod method) {
        this.method = method;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public void setTransactionRef(String transactionRef) {
        this.transactionRef = transactionRef;
    }

    public LocalDateTime getPaidAt() {
        return paidAt;
    }

    public void setPaidAt(LocalDateTime paidAt) {
        this.paidAt = paidAt;
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
