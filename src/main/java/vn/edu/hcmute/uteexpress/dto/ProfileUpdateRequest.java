package vn.edu.hcmute.uteexpress.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.edu.hcmute.uteexpress.entity.AppUser;

/**
 * Dữ liệu người dùng nhập ở trang hồ sơ tài khoản - TV1 phụ trách.
 *
 * Chỉ mang đúng ba trường người dùng được phép tự sửa. Tên đăng nhập và vai trò
 * cố tình KHÔNG nằm trong DTO này: tên đăng nhập là khoá định danh phiên làm việc
 * của Spring Security, còn vai trò là việc của Admin - để chúng ở đây thì chỉ cần
 * người dùng thêm một ô input vào form gửi lên là tự nâng quyền được cho mình.
 */
public class ProfileUpdateRequest {

    @NotBlank(message = "Vui lòng nhập họ tên")
    @Size(max = 100, message = "Họ tên không được quá 100 ký tự")
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được quá 100 ký tự")
    private String email;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^0[0-9]{9,10}$", message = "Số điện thoại phải bắt đầu bằng 0 và có 10 - 11 chữ số")
    private String phone;

    public ProfileUpdateRequest() {
    }

    /** Đổ dữ liệu đang lưu trong tài khoản ra form để người dùng thấy sẵn giá trị cũ. */
    public static ProfileUpdateRequest from(AppUser appUser) {
        ProfileUpdateRequest form = new ProfileUpdateRequest();
        form.setFullName(appUser.getFullName());
        form.setEmail(appUser.getEmail());
        form.setPhone(appUser.getPhone());
        return form;
    }

    // ----- Getter / Setter -----

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }
}
