package vn.edu.hcmute.uteexpress.controller.guest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.OrderService;

import java.util.Optional;

/**
 * Trang dung cho Guest (chua dang nhap) - muc 03 ke hoach, TV1 phu trach.
 */
@Controller
public class HomeController {

    private final OrderService orderService;

    public HomeController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/")
    public String home() {
        // TODO (TV1): them bang gia cuoc tham khao theo khu vuc/khoi luong len trang nay
        return "index";
    }

    @GetMapping("/tra-cuu")
    public String trackingPage(String trackingCode, Model model) {
        if (trackingCode != null && !trackingCode.isBlank()) {
            Optional<Order> order = orderService.findByTrackingCode(trackingCode.trim());
            model.addAttribute("searched", true);
            model.addAttribute("order", order.orElse(null));
        }
        return "tra-cuu";
    }
}
