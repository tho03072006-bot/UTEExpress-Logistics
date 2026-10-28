package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;

public class AdminAccountDeleteRequest extends AdminAccountActionRequest {
    @NotBlank(message = "Vui lòng nhập chính xác tên đăng nhập cần xóa.")
    @Size(max = 50, message = "Tên đăng nhập tối đa 50 ký tự.")
    private String confirmedUsername;
    public String getConfirmedUsername() { return confirmedUsername; }
    public void setConfirmedUsername(String value) { confirmedUsername = value == null ? null : value.strip(); }
}
