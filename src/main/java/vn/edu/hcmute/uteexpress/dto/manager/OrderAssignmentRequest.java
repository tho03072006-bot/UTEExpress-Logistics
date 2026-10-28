package vn.edu.hcmute.uteexpress.dto.manager;

import jakarta.validation.constraints.*;


public class OrderAssignmentRequest {
    @NotNull(message = "Chọn Shipper.") @Positive(message = "Mã Shipper phải dương.")
    private Long shipperId;

    @NotNull(message = "Thiếu phiên bản đơn.") @PositiveOrZero(message = "Phiên bản không hợp lệ.")
    private Long revision;

    @NotBlank(message = "Nhập lý do phân công.") @Size(min = 5, max = 200, message = "Lý do từ 5 đến 200 ký tự.")
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Lý do không được chứa ký tự điều khiển.")
    private String reason;

    public Long getShipperId() { return shipperId; }
    public void setShipperId(Long shipperId) { this.shipperId = shipperId; }
    public Long getRevision() { return revision; }
    public void setRevision(Long revision) { this.revision = revision; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason == null ? null : reason.strip(); }

}
