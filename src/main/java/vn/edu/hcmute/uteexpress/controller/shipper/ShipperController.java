package vn.edu.hcmute.uteexpress.controller.shipper;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.tracking.TrackingService;

@Controller
@RequestMapping("/shipper")
public class ShipperController {

    private final TrackingService trackingService;

    public ShipperController(TrackingService trackingService) {
        this.trackingService = trackingService;
    }

    @GetMapping("/trang-chu")
    public String dashboard(Authentication authentication, Model model) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        model.addAttribute(
                "orders",
                trackingService.findAssignedOrders(authentication.getName()));

        return "shipper/dashboard";
    }

    @GetMapping("/don/{id}")
    public String orderDetail(
            @PathVariable Long id,
            Authentication authentication,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        Optional<Order> order = trackingService.findAssignedOrder(
                id, authentication.getName());

        if (order.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Không tìm thấy đơn hoặc đơn không được phân công cho bạn.");
            return "redirect:/shipper/trang-chu";
        }

        model.addAttribute("order", order.get());
        return "shipper/order-detail";
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || authentication instanceof AnonymousAuthenticationToken;
    }
}