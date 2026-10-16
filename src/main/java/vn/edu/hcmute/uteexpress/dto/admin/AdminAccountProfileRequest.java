package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import java.util.Locale;
import vn.edu.hcmute.uteexpress.entity.AppUser;

public class AdminAccountProfileRequest {
    @NotBlank(message = "Vui lòng nhập họ tên.")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự.")
    @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M} .'-]+", message = "Họ tên chỉ chứa chữ và dấu phân cách tên hợp lệ.")
    private String fullName;
    @NotBlank(message = "Vui lòng nhập email.")
    @Email(message = "Email không đúng định dạng.")
    @Size(max = 100, message = "Email tối đa 100 ký tự.")
    @Pattern(regexp = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+", message = "Email cần tên hộp thư và tên miền hợp lệ.")
    private String email;
    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Pattern(regexp = "0[35789][0-9]{8}", message = "Số di động Việt Nam phải có 10 số, bắt đầu bằng 03/05/07/08/09.")
    private String phone;
    @NotNull(message = "Vui lòng chọn vai trò hợp lệ.")
    private AppUser.Role role;
    public String getFullName() { return fullName; }
    public void setFullName(String value) { fullName = value == null ? null : value.strip(); }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value == null ? null : value.strip().toLowerCase(Locale.ROOT); }
    public String getPhone() { return phone; }
    public void setPhone(String value) {
        phone = value == null ? null : value.strip().replace(" ", "").replace("-", "").replace(".", "");
        if (phone != null && phone.startsWith("+84")) { phone = "0" + phone.substring(3); }
    }
    public AppUser.Role getRole() { return role; }
    public void setRole(AppUser.Role value) { role = value; }
}
