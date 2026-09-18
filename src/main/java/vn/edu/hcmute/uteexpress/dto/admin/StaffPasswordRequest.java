package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;

public class StaffPasswordRequest {
    @NotBlank(message = "Vui lòng nhập mật khẩu.")
    @Size(min = 12, max = 64, message = "Mật khẩu phải có từ 12 đến 64 ký tự.")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9])(?=.*[^a-zA-Z0-9\\s])\\S+$",
            message = "Mật khẩu cần chữ hoa, chữ thường, số, ký tự đặc biệt và không có khoảng trắng.")
    private String password;

    @NotBlank(message = "Vui lòng xác nhận mật khẩu.")
    private String confirmPassword;

    @AssertTrue(message = "Hai mật khẩu không khớp.")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    @AssertTrue(message = "Mật khẩu không được vượt quá 72 byte UTF-8.")
    public boolean isPasswordWithinByteLimit() {
        return password == null || password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    public void clearSecrets() { password = null; confirmPassword = null; }
}

