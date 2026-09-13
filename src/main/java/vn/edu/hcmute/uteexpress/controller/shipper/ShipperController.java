package vn.edu.hcmute.uteexpress.controller.shipper;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

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
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken) {
            return "redirect:/dang-nhap";
        }

        model.addAttribute(
                "orders",
                trackingService.findAssignedOrders(authentication.getName()));

        return "shipper/dashboard";
    }
}