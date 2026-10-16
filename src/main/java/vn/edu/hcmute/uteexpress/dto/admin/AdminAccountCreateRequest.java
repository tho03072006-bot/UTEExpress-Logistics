package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;

public class AdminAccountCreateRequest extends AdminAccountProfileRequest {
    @NotBlank(message = "Vui lòng nhập tên đăng nhập.")
    @Pattern(regexp = "[a-z][a-z0-9._-]{3,49}", message = "Tên đăng nhập từ 4–50 ký tự, bắt đầu bằng chữ; chỉ dùng chữ, số, . _ -.")
    private String username;
    @Valid @NotNull(message = "Thiếu mật khẩu cấp mới.")
    private StaffPasswordRequest credentials = new StaffPasswordRequest();
    public String getUsername() { return username; }
    public void setUsername(String value) { username = value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
    public StaffPasswordRequest getCredentials() { return credentials; }
    public void setCredentials(StaffPasswordRequest value) { credentials = value; }
    public void clearSecrets() { if (credentials != null) { credentials.clearSecrets(); } }
}
