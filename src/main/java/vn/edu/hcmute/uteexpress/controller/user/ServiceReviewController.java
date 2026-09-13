package vn.edu.hcmute.uteexpress.controller.user;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.ServiceReviewRequest;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.ServiceReviewService;

/**
 * Đánh giá chất lượng dịch vụ giao hàng - TV1 phụ trách (việc 4).
 * Controller chỉ điều hướng; mọi kiểm tra (đơn có phải của mình không, đã giao chưa,
 * đã đánh giá chưa) nằm trong ServiceReviewService.
 */
@Controller
@RequestMapping("/nguoi-dung/danh-gia")
public class ServiceReviewController {

    private final ServiceReviewService serviceReviewService;

    public ServiceReviewController(ServiceReviewService serviceReviewService) {
        this.serviceReviewService = serviceReviewService;
    }

    @GetMapping("/{orderId}")
    public String reviewForm(@PathVariable Long orderId, Authentication authentication,
                             Model model, RedirectAttributes redirectAttributes) {
        try {
            Order order = serviceReviewService.requireReviewableOrder(orderId, authentication.getName());
            model.addAttribute("order", order);
            model.addAttribute("form", new ServiceReviewRequest());
            return "user/review-form";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/nguoi-dung/don-hang";
        }
    }

    @PostMapping("/{orderId}")
    public String submitReview(@PathVariable Long orderId,
                               @Valid @ModelAttribute("form") ServiceReviewRequest form,
                               BindingResult bindingResult, Authentication authentication,
                               Model model, RedirectAttributes redirectAttributes) {
        String username = authentication.getName();

        // Ve lai form thi van phai co thong tin don hang de trang hien duoc ma van don.
        if (bindingResult.hasErrors()) {
            return addOrderThenBack(orderId, username, model, redirectAttributes, null);
        }
        try {
            serviceReviewService.createReview(orderId, form, username);
        } catch (IllegalStateException ex) {
            return addOrderThenBack(orderId, username, model, redirectAttributes, ex.getMessage());
        }

        redirectAttributes.addFlashAttribute("message",
                "Cảm ơn bạn đã đánh giá dịch vụ. Nhận xét của bạn đã hiển thị ở trang chủ.");
        return "redirect:/nguoi-dung/don-hang";
    }

    /**
     * Vẽ lại form đánh giá kèm lỗi. Nếu tới lúc này mà đơn đã hết quyền đánh giá
     * (ví dụ vừa được đánh giá ở tab khác) thì quay về danh sách đơn kèm thông báo.
     */
    private String addOrderThenBack(Long orderId, String username, Model model,
                                    RedirectAttributes redirectAttributes, String error) {
        try {
            model.addAttribute("order", serviceReviewService.requireReviewableOrder(orderId, username));
            if (error != null) {
                model.addAttribute("error", error);
            }
            return "user/review-form";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/nguoi-dung/don-hang";
        }
    }
}
