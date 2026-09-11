package vn.edu.hcmute.uteexpress.controller.shipper;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Vai tro Shipper - muc 03 ke hoach, TV2 phu trach (+ WebSocket realtime tracking).
 * Cac chuc nang can lam:
 *  - Nhan danh sach don duoc phan cong theo khu vuc/tuyen
 *  - Cap nhat trang thai: da lay -> dang giao -> thanh cong/that bai -> hoan tra
 *  - Upload anh/chu ky xac nhan giao hang thanh cong
 *  - Day trang thai realtime qua WebSocket (xem config/WebSocketConfig.java)
 *  - Thong ke so don da giao theo ngay/tuan/thang
 */
@Controller
@RequestMapping("/shipper")
public class ShipperController {

    @GetMapping("/trang-chu")
    public String dashboard() {
        return "shipper/dashboard";
    }

    // TODO: @GetMapping("/don-duoc-giao") - danh sach don duoc phan cong
    // TODO: @PostMapping("/don/{id}/cap-nhat-trang-thai")
}
