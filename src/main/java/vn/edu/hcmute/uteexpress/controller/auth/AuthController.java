package vn.edu.hcmute.uteexpress.controller.auth;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Chuc nang chung: dang ky (+ OTP email), dang nhap, dang xuat, quen mat khau (+ OTP email).
 * Day la phan nen chung ca nhom dung - nen lam xong o Tuan 2 truoc khi tach nhanh code rieng.
 * TODO: noi voi AuthService (dang tao stub o service/AuthService.java) de xu ly that.
 */
@Controller
public class AuthController {

    @GetMapping("/dang-nhap")
    public String trangDangNhap() {
        return "auth/login";
    }

    // TODO: @GetMapping("/dang-ky"), @PostMapping("/dang-ky") - dang ky + gui OTP qua email
    // TODO: @GetMapping("/quen-mat-khau"), @PostMapping("/quen-mat-khau") - gui OTP khoi phuc mat khau
    // TODO: @PostMapping("/dang-nhap") thuong khong can viet tay, Spring Security formLogin tu xu ly
}
