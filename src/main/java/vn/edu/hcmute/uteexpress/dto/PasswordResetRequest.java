package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Dữ liệu người dùng nhập ở bước đặt lại mật khẩu (sau khi đã nhận mã OTP qua email).
 *
 * Email được mang theo trong ô ẩn của form chứ không đặt trên URL - tránh để địa chỉ email
 * của người dùng nằm trong lịch sử trình duyệt hay log của máy chủ.
 */
public class PasswordResetRequest {

    @NotBlank(message = "Vui lòng nhập email đã đăng ký")
    @vn.edu.hcmute.uteexpress.validation.ValidPublicEmail
    private String email;

    @NotBlank(message = "Vui lòng nhập mã OTP")
    @Pattern(regexp = "^[0-9]{6}$", message = "Mã OTP gồm đúng 6 chữ số")
    private String otp;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @vn.edu.hcmute.uteexpress.validation.ValidPublicPassword
    private String newPassword;

    @NotBlank(message = "Vui lòng nhập lại mật khẩu mới")
    @Size(max=64,message="Xác nhận mật khẩu tối đa 64 ký tự.")
    private String confirmPassword;

    public PasswordResetRequest() {
    }

    /**
     * Hai ô mật khẩu có khớp nhau không. Kiểm tra ở Controller thay vì viết annotation
     * kiểm tra chéo 2 field cho gọn - chỉ dùng đúng một chỗ.
     */
    public boolean isPasswordConfirmed() {
        return newPassword != null && newPassword.equals(confirmPassword);
    }

    // ----- Getter / Setter -----

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.email(email);
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public void setConfirmPassword(String confirmPassword) {
        this.confirmPassword = confirmPassword;
    }
    public void clearSecrets() { newPassword=null; confirmPassword=null; }
}
