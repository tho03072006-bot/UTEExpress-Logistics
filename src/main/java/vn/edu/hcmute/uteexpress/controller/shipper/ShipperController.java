package vn.edu.hcmute.uteexpress.controller.shipper;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperStatisticsPeriod;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofService;
import vn.edu.hcmute.uteexpress.service.tracking.ShipperStatisticsService;
import vn.edu.hcmute.uteexpress.service.tracking.TrackingService;

@Controller
@RequestMapping("/shipper")
public class ShipperController {

    private final TrackingService trackingService;
    private final DeliveryProofService deliveryProofService;
    private final ShipperStatisticsService shipperStatisticsService;

    public ShipperController(
            TrackingService trackingService,
            DeliveryProofService deliveryProofService,
            ShipperStatisticsService shipperStatisticsService) {
        this.trackingService = trackingService;
        this.deliveryProofService = deliveryProofService;
        this.shipperStatisticsService = shipperStatisticsService;
    }

    @GetMapping("/trang-chu")
    public String dashboard(
            Authentication authentication,
            Model model) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        model.addAttribute(
                "orders",
                trackingService.findAssignedOrders(
                        authentication.getName()));

        return "shipper/dashboard";
    }

    @GetMapping("/thong-ke")
    public String statistics(
            @RequestParam(
                    name = "period",
                    defaultValue = "DAY")
                    ShipperStatisticsPeriod period,
            Authentication authentication,
            Model model) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        model.addAttribute(
                "summary",
                shipperStatisticsService.getStatistics(
                        authentication.getName(), period));
        model.addAttribute(
                "periods",
                ShipperStatisticsPeriod.values());

        return "shipper/statistics";
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
                    "Không tìm thấy đơn hoặc đơn không được "
                            + "phân công cho bạn.");
            return "redirect:/shipper/trang-chu";
        }

        Order assignedOrder = order.get();

        model.addAttribute("order", assignedOrder);
        model.addAttribute(
                "deliveryProof",
                deliveryProofService.findByOrder(assignedOrder)
                        .orElse(null));

        return "shipper/order-detail";
    }

    @PostMapping("/don/{id}/bang-chung")
    public String saveDeliveryProof(
            @PathVariable Long id,
            @RequestParam("proofImage")
                    MultipartFile proofImage,
            @RequestParam(
                    name = "signatureData",
                    defaultValue = "")
                    String signatureData,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        try {
            deliveryProofService.saveProof(
                    id,
                    authentication.getName(),
                    proofImage,
                    signatureData);

            redirectAttributes.addFlashAttribute(
                    "message",
                    "Đã lưu ảnh bằng chứng và chữ ký người nhận.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    exception.getMessage());
        }

        return "redirect:/shipper/don/" + id;
    }

    @PostMapping("/don/{id}/trang-thai")
    public String updateStatus(
            @PathVariable Long id,
            @RequestParam("status")
                    Order.OrderStatus newStatus,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        try {
            trackingService.updateStatus(
                    id,
                    authentication.getName(),
                    newStatus);

            redirectAttributes.addFlashAttribute(
                    "message",
                    "Cập nhật trạng thái đơn thành công.");
        } catch (IllegalStateException exception) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    exception.getMessage());
        }

        return "redirect:/shipper/don/" + id;
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || authentication instanceof AnonymousAuthenticationToken;
    }
}
