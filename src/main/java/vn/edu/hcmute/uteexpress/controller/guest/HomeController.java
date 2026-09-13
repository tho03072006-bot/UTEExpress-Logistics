package vn.edu.hcmute.uteexpress.controller.guest;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.ServiceReviewService;

import java.util.Optional;

/**
 * Trang dung cho Guest (chua dang nhap) - muc 03 ke hoach, TV1 phu trach.
 */
@Controller
public class HomeController {

    /** So danh gia hien o khoi "Khach hang noi gi" tren trang chu. */
    private static final int HOME_REVIEW_LIMIT = 3;

    private final OrderService orderService;
    private final ServiceReviewService serviceReviewService;

    public HomeController(OrderService orderService, ServiceReviewService serviceReviewService) {
        this.orderService = orderService;
        this.serviceReviewService = serviceReviewService;
    }

    @GetMapping("/")
    public String home(Model model) {
        // TODO (TV1): them bang gia cuoc tham khao theo khu vuc/khoi luong len trang nay
        model.addAttribute("latestReviews", serviceReviewService.findLatestReviews(HOME_REVIEW_LIMIT));
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
