package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;
import vn.edu.hcmute.uteexpress.entity.AppUser;

public class AdminAccountProfileRequest {
    @jakarta.validation.Valid @NotNull(message = "Thiếu thông tin chi nhánh.")
    private StaffBranchRequest assignment = new StaffBranchRequest();
    public StaffBranchRequest getAssignment() { return assignment; }
    public void setAssignment(StaffBranchRequest value) { assignment = value; }
    @NotBlank(message = "Vui lòng nhập họ tên.")
    @Size(min = 2, max = 100, message = "Họ tên phải từ 2 đến 100 ký tự.")
    @Pattern(regexp = "(?=.*\\p{L})[\\p{L}\\p{M} .'-]+", message = "Họ tên chỉ chứa chữ và dấu phân cách tên hợp lệ.")
    private String fullName;
    @NotBlank(message = "Vui lòng nhập email.")
    @vn.edu.hcmute.uteexpress.validation.ValidPublicEmail
    private String email;
    @NotBlank(message = "Vui lòng nhập số điện thoại.")
    @Pattern(regexp = "0[35789][0-9]{8}", message = "Số di động Việt Nam phải có 10 số, bắt đầu bằng 03/05/07/08/09.")
    private String phone;
    @NotNull(message = "Vui lòng chọn vai trò hợp lệ.")
    private AppUser.Role role;
    public String getFullName() { return fullName; }
    public void setFullName(String value) { fullName = value == null ? null : value.strip(); }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = vn.edu.hcmute.uteexpress.validation.PublicAccountValidation.email(value); }
    public String getPhone() { return phone; }
    public void setPhone(String value) {
        phone = value == null ? null : value.strip().replace(" ", "").replace("-", "").replace(".", "");
        if (phone != null && phone.startsWith("+84")) { phone = "0" + phone.substring(3); }
    }
    public AppUser.Role getRole() { return role; }
    public void setRole(AppUser.Role value) { role = value; }
}
