package vn.edu.hcmute.uteexpress.controller.guest;

import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.edu.hcmute.uteexpress.dto.FeeEstimateRequest;
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

    /**
     * Trang uoc tinh cuoc phi, khach chua dang nhap cung dung duoc.
     *
     * Dung GET chu khong POST de ket qua nam tren duong dan - nguoi dung luu lai hoac
     * gui cho nguoi khac xem deu duoc, va bam F5 khong bi hoi gui lai bieu mau.
     * Khong co tham so weightKg thi chi hien form rong, chua tinh gi.
     */
    @GetMapping("/uoc-tinh-cuoc")
    public String feeEstimatePage(@Valid @ModelAttribute("form") FeeEstimateRequest form,
                                  BindingResult bindingResult, Model model) {
        // Chua nhap gi (vua mo trang) hoac nhap sai thi chi hien lai form, chua tinh cuoc.
        if (form.getWeightKg() == null || bindingResult.hasErrors()) {
            return "uoc-tinh-cuoc";
        }

        model.addAttribute("bangCuoc", orderService.estimateAllServices(form.getWeightKg()));
        return "uoc-tinh-cuoc";
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
