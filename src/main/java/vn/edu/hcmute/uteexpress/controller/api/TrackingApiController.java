package vn.edu.hcmute.uteexpress.controller.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.OrderService;

import java.util.Map;
import java.util.Optional;

/**
 * API tra cuu van don cong khai (khong can dang nhap) - se dung JWT khi mo rong
 * cho ung dung/dich vu ben ngoai goi vao (tinh nang sang tao, TV3 phu trach).
 * Hien tai da noi voi OrderService that (khong con du lieu mau).
 */
@RestController
@RequestMapping("/api/tracking")
public class TrackingApiController {

    private final OrderService orderService;

    public TrackingApiController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/{trackingCode}")
    public ResponseEntity<?> lookup(@PathVariable String trackingCode) {
        Optional<Order> order = orderService.findByTrackingCode(trackingCode);
        if (order.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Order o = order.get();
        return ResponseEntity.ok(Map.of(
                "trackingCode", o.getTrackingCode(),
                "status", o.getStatus().name(),
                "receiverName", o.getReceiverName(),
                "serviceType", o.getServiceType().name(),
                "createdAt", o.getCreatedAt().toString()
        ));
    }
}
