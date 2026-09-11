package vn.edu.hcmute.uteexpress.controller.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * API tra cuu van don cong khai (khong can dang nhap) - dung JWT khi mo rong
 * cho ung dung/dich vu ben ngoai goi vao (tinh nang sang tao, TV3 phu trach).
 * Hien tai tra ve du lieu mau de nhom co the test route ngay.
 */
@RestController
@RequestMapping("/api/tracking")
public class TrackingApiController {

    @GetMapping("/{trackingCode}")
    public Map<String, Object> traCuu(@PathVariable String trackingCode) {
        // TODO: thay bang OrderRepository.findByTrackingCode that
        return Map.of(
                "trackingCode", trackingCode,
                "status", "PENDING_PICKUP",
                "note", "Du lieu mau - can noi voi OrderRepository"
        );
    }
}
