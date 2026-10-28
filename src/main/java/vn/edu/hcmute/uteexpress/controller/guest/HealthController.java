package vn.edu.hcmute.uteexpress.controller.guest;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Đường dẫn kiểm tra "app còn sống" cho Render hoặc công cụ giám sát. Không chạm database, không lộ thông tin. */
@RestController
public class HealthController {
    @GetMapping(value = "/healthz", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> health() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body("ok");
    }
}
