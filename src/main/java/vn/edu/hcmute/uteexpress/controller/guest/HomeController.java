package vn.edu.hcmute.uteexpress.controller.guest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Trang chu danh cho Guest (chua dang nhap) - muc 03 ke hoach, TV1 phu trach.
 * TODO (TV1): them bang gia cuoc tham khao + form tra cuu van don theo ma tracking.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String trangChu() {
        return "index";
    }
}
