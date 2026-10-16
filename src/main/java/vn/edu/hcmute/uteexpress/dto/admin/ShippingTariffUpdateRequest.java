package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;


public class ShippingTariffUpdateRequest {
    @NotNull(message = "Thiếu phiên bản bảng cước.") @PositiveOrZero(message = "Phiên bản không được âm.")
    private Long revision;

    @NotNull(message = "Nhập phí cơ bản.") @DecimalMin(value = "0", message = "Phí không được âm.") @DecimalMax(value = "100000000", message = "Phí cơ bản tối đa 100 triệu đồng.") @Digits(integer = 9, fraction = 0, message = "Nhập số tiền VND nguyên.")
    private BigDecimal baseFee;

    @NotNull(message = "Nhập phí mỗi kg.") @DecimalMin(value = "0", message = "Phí không được âm.") @DecimalMax(value = "5000000", message = "Phí mỗi kg tối đa 5 triệu đồng.") @Digits(integer = 7, fraction = 0, message = "Nhập số tiền VND nguyên.")
    private BigDecimal feePerKg;

    @NotBlank(message = "Nhập lý do đổi cước.") @Size(min = 5, max = 200, message = "Lý do từ 5–200 ký tự.") @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Lý do không được chứa ký tự điều khiển.")
    private String reason;

    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public BigDecimal getBaseFee() { return baseFee; }
    public void setBaseFee(BigDecimal baseFee) { this.baseFee = baseFee; }
    public BigDecimal getFeePerKg() { return feePerKg; }
    public void setFeePerKg(BigDecimal feePerKg) { this.feePerKg = feePerKg; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason == null ? null : reason.strip(); }

}
