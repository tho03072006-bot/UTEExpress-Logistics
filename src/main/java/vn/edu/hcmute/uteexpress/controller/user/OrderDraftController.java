package vn.edu.hcmute.uteexpress.controller.user;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.service.OrderDraftService;

import java.util.List;

/**
 * Giỏ đơn chờ xác nhận - TV1 phụ trách (việc 2).
 *
 * Chỉ lo trang giỏ và các thao tác trên giỏ. Nút "Thêm vào giỏ" nằm ở UserOrderController
 * vì nó dùng chung form tạo đơn (khi nhập thiếu còn phải vẽ lại đúng form đó kèm lỗi).
 */
@Controller
@RequestMapping("/nguoi-dung/gio-don")
public class OrderDraftController {

    private final OrderDraftService orderDraftService;

    public OrderDraftController(OrderDraftService orderDraftService) {
        this.orderDraftService = orderDraftService;
    }

    @GetMapping
    public String viewCart(Authentication authentication, Model model) {
        String username = authentication.getName();
        model.addAttribute("drafts", orderDraftService.findDrafts(username));
        model.addAttribute("totalFee", orderDraftService.totalEstimatedFee(username));
        return "user/order-cart";
    }

    @PostMapping("/{id}/xoa")
    public String removeDraft(@PathVariable Long id, Authentication authentication,
                              RedirectAttributes redirectAttributes) {
        try {
            orderDraftService.removeDraft(id, authentication.getName());
            redirectAttributes.addFlashAttribute("message", "Đã xoá đơn nháp khỏi giỏ.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/nguoi-dung/gio-don";
    }

    @PostMapping("/xoa-het")
    public String clearCart(Authentication authentication, RedirectAttributes redirectAttributes) {
        orderDraftService.clearDrafts(authentication.getName());
        redirectAttributes.addFlashAttribute("message", "Đã xoá toàn bộ đơn nháp trong giỏ.");
        return "redirect:/nguoi-dung/gio-don";
    }

    @PostMapping("/{id}/xac-nhan")
    public String confirmOne(@PathVariable Long id, Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            var order = orderDraftService.confirmDraft(id, authentication.getName());
            redirectAttributes.addFlashAttribute("message",
                    "Đã gửi đơn thành công. Mã vận đơn: " + order.getTrackingCode());
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/nguoi-dung/gio-don";
    }

    @PostMapping("/xac-nhan")
    public String confirmAll(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            var orders = orderDraftService.confirmAllDrafts(authentication.getName());
            List<String> codes = orders.stream().map(order -> order.getTrackingCode()).toList();
            redirectAttributes.addFlashAttribute("message",
                    "Đã gửi " + orders.size() + " đơn thành công. Mã vận đơn: " + String.join(", ", codes));
            // Gio da trong sau khi xac nhan het nen chuyen thang sang lich su don cho de theo doi.
            return "redirect:/nguoi-dung/don-hang";
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/nguoi-dung/gio-don";
        }
    }
}
