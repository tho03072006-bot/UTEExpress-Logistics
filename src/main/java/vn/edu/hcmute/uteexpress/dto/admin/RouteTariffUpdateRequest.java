package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import vn.edu.hcmute.uteexpress.entity.routing.RouteTariff;

public class RouteTariffUpdateRequest {
    @NotNull(message = "Thiếu phiên bản biểu giá.") @PositiveOrZero private Long revision;
    @NotNull(message = "Nhập phụ phí tuyến.") @DecimalMin(value = "0", message = "Phụ phí không được âm.")
    @DecimalMax(value = "100000000", message = "Phụ phí tối đa 100 triệu đồng.")
    @Digits(integer = 9, fraction = 0, message = "Nhập số tiền VND nguyên.") private BigDecimal surcharge;
    @NotBlank(message = "Nhập thời gian dự kiến.") @Size(max = 100)
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Thời gian không chứa ký tự điều khiển.") private String leadTimeText;
    @NotBlank(message = "Nhập lý do đổi cước.") @Size(min = 5, max = 200)
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Lý do không chứa ký tự điều khiển.") private String reason;
    public static RouteTariffUpdateRequest from(RouteTariff tariff) {
        var form = new RouteTariffUpdateRequest(); form.revision = tariff.getRevision();
        form.surcharge = tariff.getSurcharge(); form.leadTimeText = tariff.getLeadTimeText(); return form;
    }
    public Long getRevision() { return revision; }
    public void setRevision(Long value) { revision = value; }
    public BigDecimal getSurcharge() { return surcharge; }
    public void setSurcharge(BigDecimal value) { surcharge = value; }
    public String getLeadTimeText() { return leadTimeText; }
    public void setLeadTimeText(String value) { leadTimeText = value == null ? null : value.strip(); }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value == null ? null : value.strip(); }
}
