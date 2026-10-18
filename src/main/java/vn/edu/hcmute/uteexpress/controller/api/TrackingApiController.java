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
 * API công khai chỉ trả tiến độ, không trả thông tin người nhận.
 * API nhân viên dùng JWT được tách riêng tại /api/internal/**.
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
        if (trackingCode == null || !trackingCode.matches("[A-Za-z0-9-]{1,20}")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Mã vận đơn không hợp lệ."));
        }
        Optional<Order> order = orderService.findByTrackingCode(trackingCode);
        if (order.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Order o = order.get();
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("trackingCode", o.getTrackingCode());
        result.put("status", o.getStatus() == null ? "UNKNOWN" : o.getStatus().name());
        result.put("serviceType", o.getServiceType() == null ? null : o.getServiceType().name());
        result.put("createdAt", o.getCreatedAt() == null ? null : o.getCreatedAt().toString());
        return ResponseEntity.ok(result);
    }
}
