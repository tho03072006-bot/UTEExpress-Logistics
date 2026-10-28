package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;

public class AdminAccountActionRequest {
    @NotNull(message = "Thiếu phiên bản tài khoản. Hãy tải lại trang.")
    @PositiveOrZero(message = "Phiên bản tài khoản không hợp lệ.")
    private Long revision;
    @NotBlank(message = "Vui lòng nhập mật khẩu Admin đang đăng nhập để xác nhận.")
    @Size(max = 128, message = "Mật khẩu xác nhận quá dài.")
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Mật khẩu xác nhận không được chứa ký tự điều khiển.")
    private String currentPassword;
    @NotBlank(message = "Vui lòng nhập lý do thay đổi.")
    @Size(min = 5, max = 200, message = "Lý do phải từ 5 đến 200 ký tự.")
    @Pattern(regexp = "[^\\p{Cntrl}]+", message = "Lý do không được chứa ký tự điều khiển hoặc xuống dòng.")
    private String reason;
    public Long getRevision() { return revision; }
    public void setRevision(Long value) { revision = value; }
    public String getCurrentPassword() { return currentPassword; }
    public void setCurrentPassword(String value) { currentPassword = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value == null ? null : value.strip(); }
    @AssertTrue(message = "Mật khẩu xác nhận không được vượt quá 72 byte UTF-8.")
    public boolean isCurrentPasswordWithinByteLimit() {
        return currentPassword == null || currentPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 72;
    }
    public void clearSecrets() { currentPassword = null; }
}
