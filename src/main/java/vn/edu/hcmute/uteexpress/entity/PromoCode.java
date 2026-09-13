package vn.edu.hcmute.uteexpress.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Nationalized;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mã giảm cước vận chuyển - TV1 làm phần áp mã phía người gửi (việc 5).
 *
 * MÀN HÌNH TẠO/SỬA MÃ LÀ PHẦN CỦA TV3 (Manager - quản lý khuyến mãi). Bảng này đã có đủ
 * cột cho màn hình đó, TV3 chỉ cần viết CRUD lên trên, không phải sửa lại entity.
 * Trong lúc chờ, dữ liệu mẫu nạp bằng db/03_seed_ma_giam_gia.sql.
 */
@Entity
@Table(name = "promo_code")
public class PromoCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Mã người dùng gõ vào, luôn lưu dạng CHỮ HOA để gõ thường hay hoa đều tìm ra. */
    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Nationalized
    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType = DiscountType.PERCENT;

    /** Giảm bao nhiêu: PERCENT thì là số phần trăm (10 = 10%), FIXED_AMOUNT thì là số tiền. */
    @Column(name = "discount_value", nullable = false, precision = 12, scale = 0)
    private BigDecimal discountValue;

    /**
     * Trần số tiền được giảm, chỉ dùng cho mã giảm theo phần trăm.
     * null nghĩa là không giới hạn. Không có cột này thì mã "giảm 50%" áp vào đơn cước cao
     * sẽ giảm một khoản rất lớn ngoài dự tính.
     */
    @Column(name = "max_discount_amount", precision = 12, scale = 0)
    private BigDecimal maxDiscountAmount;

    /** Cước tối thiểu của đơn thì mới được dùng mã. null nghĩa là không yêu cầu. */
    @Column(name = "min_order_amount", precision = 12, scale = 0)
    private BigDecimal minOrderAmount;

    @Column(name = "start_at")
    private LocalDateTime startAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    /** Tổng số lượt được dùng. null nghĩa là dùng không giới hạn số lượt. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount = 0;

    /** Công tắc bật/tắt nhanh, để Manager ngưng một mã mà không phải xoá hẳn. */
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum DiscountType {
        PERCENT("Giảm theo phần trăm"),
        FIXED_AMOUNT("Giảm số tiền cố định");

        private final String displayName;

        DiscountType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public PromoCode() {
    }

    /** Còn lượt dùng hay không - null usageLimit nghĩa là không giới hạn. */
    public boolean hasRemainingUsage() {
        return usageLimit == null || usedCount < usageLimit;
    }

    /** Thời điểm hiện tại có nằm trong khoảng hiệu lực không (để trống đầu/cuối là không giới hạn). */
    public boolean isWithinPeriod(LocalDateTime now) {
        if (startAt != null && now.isBefore(startAt)) {
            return false;
        }
        return endAt == null || !now.isAfter(endAt);
    }

    // ----- Getter / Setter -----

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMaxDiscountAmount() {
        return maxDiscountAmount;
    }

    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) {
        this.maxDiscountAmount = maxDiscountAmount;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(BigDecimal minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public void setStartAt(LocalDateTime startAt) {
        this.startAt = startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public void setEndAt(LocalDateTime endAt) {
        this.endAt = endAt;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public int getUsedCount() {
        return usedCount;
    }

    public void setUsedCount(int usedCount) {
        this.usedCount = usedCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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
