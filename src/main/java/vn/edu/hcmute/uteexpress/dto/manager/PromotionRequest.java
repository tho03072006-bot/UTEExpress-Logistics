package vn.edu.hcmute.uteexpress.dto.manager;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.hcmute.uteexpress.entity.PromoCode;


public class PromotionRequest {
    @NotNull(message = "Thiếu phiên bản khuyến mãi.") @PositiveOrZero(message = "Phiên bản không được âm.")
    private Long revision = 0L;

    @NotBlank(message = "Nhập mã khuyến mãi.") @Pattern(regexp = "[A-Z0-9][A-Z0-9_-]{2,29}", message = "Mã từ 3–30 chữ/số/gạch ngang/gạch dưới.")
    private String code;

    @NotBlank(message = "Nhập mô tả.") @Size(max = 255, message = "Mô tả tối đa 255 ký tự.") @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Mô tả không được chứa ký tự điều khiển.")
    private String description;

    @NotNull(message = "Chọn kiểu giảm giá.")
    private PromoCode.DiscountType discountType;

    @NotNull(message = "Nhập mức giảm.") @DecimalMin(value = "1", message = "Mức giảm phải dương.") @DecimalMax(value = "100000000", message = "Mức giảm tối đa 100 triệu đồng.") @Digits(integer = 9, fraction = 0, message = "Mức giảm phải là số nguyên.")
    private BigDecimal discountValue;

    @DecimalMin(value = "1", message = "Trần giảm phải dương.") @DecimalMax(value = "100000000", message = "Trần giảm tối đa 100 triệu đồng.") @Digits(integer = 9, fraction = 0, message = "Trần giảm phải là tiền VND nguyên.")
    private BigDecimal maxDiscountAmount;

    @NotNull(message = "Nhập cước tối thiểu (0 nếu không giới hạn).") @DecimalMin(value = "0", message = "Cước tối thiểu không được âm.") @DecimalMax(value = "100000000", message = "Cước tối thiểu tối đa 100 triệu đồng.") @Digits(integer = 9, fraction = 0, message = "Cước tối thiểu phải là tiền VND nguyên.")
    private BigDecimal minOrderAmount;

    @NotNull(message = "Nhập tổng lượt sử dụng.") @Min(value = 1, message = "Ít nhất một lượt.") @Max(value = 1000000, message = "Tối đa một triệu lượt.")
    private Integer usageLimit;

    @NotNull(message = "Nhập thời điểm bắt đầu.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime startAt;

    @NotNull(message = "Nhập thời điểm kết thúc.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private LocalDateTime endAt;

    @NotNull(message = "Thiếu trạng thái khuyến mãi.")
    private Boolean active = true;

    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code == null ? null : code.strip().toUpperCase(Locale.ROOT); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description == null ? null : description.strip(); }
    public PromoCode.DiscountType getDiscountType() { return discountType; }
    public void setDiscountType(PromoCode.DiscountType discountType) { this.discountType = discountType; }
    public BigDecimal getDiscountValue() { return discountValue; }
    public void setDiscountValue(BigDecimal discountValue) { this.discountValue = discountValue; }
    public BigDecimal getMaxDiscountAmount() { return maxDiscountAmount; }
    public void setMaxDiscountAmount(BigDecimal maxDiscountAmount) { this.maxDiscountAmount = maxDiscountAmount; }
    public BigDecimal getMinOrderAmount() { return minOrderAmount; }
    public void setMinOrderAmount(BigDecimal minOrderAmount) { this.minOrderAmount = minOrderAmount; }
    public Integer getUsageLimit() { return usageLimit; }
    public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    @AssertTrue(message = "Phần trăm phải từ 1 đến 100 và có trần giảm; mã giảm cố định không dùng trần.")
    public boolean isDiscountConsistent() {
        if (discountType == null || discountValue == null) { return true; }
        return discountType == PromoCode.DiscountType.PERCENT
            ? discountValue.compareTo(BigDecimal.valueOf(100)) <= 0 && maxDiscountAmount != null
            : maxDiscountAmount == null;
    }
    @AssertTrue(message = "Ngày kết thúc phải sau ngày bắt đầu và thời hạn tối đa 366 ngày.")
    public boolean isPeriodConsistent() {
        return startAt == null || endAt == null || (endAt.isAfter(startAt)
            && java.time.Duration.between(startAt, endAt).compareTo(java.time.Duration.ofDays(366)) <= 0);
    }
}
