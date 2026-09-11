package vn.edu.hcmute.uteexpress.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Vai tro Admin - muc 03 ke hoach, TV3 phu trach (ke thua quyen Manager + cau hinh he thong).
 * Cac chuc nang can lam: quan ly shipper, quan ly loai dich vu, chiet khau/hoa hong,
 * cau hinh he thong, quan ly log/giao dich, thong ke tong quan toan he thong.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    @GetMapping("/trang-chu")
    public String trangChuAdmin() {
        // Dung chung template voi Manager luc dau, tach rieng khi can giao dien khac biet
        return "manager/dashboard";
    }
}
