package vn.edu.hcmute.uteexpress.controller.user;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Vai tro User (nguoi gui hang) - muc 03 ke hoach, TV1 phu trach.
 * Cac chuc nang can lam theo ban phan cong:
 *  - Tao don gui hang (nhap nguoi gui/nhan, khoi luong, chon dich vu, tinh cuoc tu dong)
 *  - Quan ly dia chi da luu, gio don cho xac nhan
 *  - Thanh toan cuoc (COD/VNPay/Momo)
 *  - Theo doi don theo trang thai + nhan cap nhat realtime (WebSocket)
 *  - Lich su don co filter, danh gia dich vu, ap ma giam gia
 */
@Controller
@RequestMapping("/nguoi-dung")
public class UserOrderController {

    @GetMapping("/trang-chu")
    public String trangChuUser() {
        // TODO: doi sang dashboard that khi co du lieu don hang
        return "user/dashboard";
    }

    // TODO: @GetMapping("/tao-don"), @PostMapping("/tao-don")
    // TODO: @GetMapping("/don-hang") - lich su + filter theo trang thai
    // TODO: @GetMapping("/dia-chi") - quan ly dia chi lay/giao da luu
}
