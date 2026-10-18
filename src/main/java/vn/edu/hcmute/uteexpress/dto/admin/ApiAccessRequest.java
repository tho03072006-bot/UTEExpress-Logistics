package vn.edu.hcmute.uteexpress.dto.admin;

import jakarta.validation.constraints.*;

public record ApiAccessRequest(
    @NotBlank(message = "Nhập mật khẩu hiện tại để xác nhận.")
    @Size(max = 72, message = "Mật khẩu xác nhận tối đa 72 ký tự.") String password) { }
