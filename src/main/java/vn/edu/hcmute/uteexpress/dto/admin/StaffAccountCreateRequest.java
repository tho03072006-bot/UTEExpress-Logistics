package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import java.util.Locale;
import vn.edu.hcmute.uteexpress.entity.AppUser;

public class StaffAccountCreateRequest extends StaffPasswordRequest {
    @NotBlank(message = "Vui lòng nhập tên đăng nhập.")
    @Pattern(regexp = "[a-z][a-z0-9._-]{3,49}", message = "Tên đăng nhập gồm 4–50 ký tự; bắt đầu bằng chữ, chỉ dùng chữ thường, số, dấu chấm, gạch dưới hoặc gạch ngang.")
    private String username;

    @NotBlank(message = "Vui lòng nhập họ tên.")
    @Size(min = 2, max = 100, message = "Họ tên phải có 2–100 ký tự.")
    @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M} .'-]+", message = "Họ tên phải có chữ, chỉ gồm chữ và dấu phân cách tên.")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email công việc.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 100, message = "Email không được quá 100 ký tự.")
    @Pattern(regexp = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+", message = "Email cần có tên miền hợp lệ.")
    private String email;

    @NotNull(message = "Vui lòng chọn cấp quyền.")
    private AppUser.Role role;

    @AssertTrue(message = "Chỉ cấp tài khoản MANAGER hoặc ADMIN tại đây.")
    public boolean isPrivilegedRole() {
        return role == null || role == AppUser.Role.MANAGER || role == AppUser.Role.ADMIN;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = normalize(username); }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName == null ? null : fullName.strip(); }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = normalize(email); }
    public AppUser.Role getRole() { return role; }
    public void setRole(AppUser.Role role) { this.role = role; }
    private String normalize(String value) { return value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
}
