package vn.edu.hcmute.uteexpress.controller.shipper;

import java.util.Optional;
import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.service.tracking.OrderJourneyService;
import vn.edu.hcmute.uteexpress.service.tracking.OrderComplaintService;
import vn.edu.hcmute.uteexpress.service.tracking.ShipmentQrService;

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
    private final OrderJourneyService journey;
    private final OrderComplaintService complaints;
    private final ShipmentQrService qr;
    private final vn.edu.hcmute.uteexpress.service.tracking.ShipperWorkspaceService workspace;
    private final vn.edu.hcmute.uteexpress.service.tracking.CodSettlementService codSettlements;

    public ShipperController(
            TrackingService trackingService,
            DeliveryProofService deliveryProofService,
            ShipperStatisticsService shipperStatisticsService,
            OrderJourneyService journey, OrderComplaintService complaints, ShipmentQrService qr,
            vn.edu.hcmute.uteexpress.service.tracking.ShipperWorkspaceService workspace,
            vn.edu.hcmute.uteexpress.service.tracking.CodSettlementService codSettlements) {
        this.trackingService = trackingService;
        this.deliveryProofService = deliveryProofService;
        this.shipperStatisticsService = shipperStatisticsService;
        this.journey = journey;
        this.complaints = complaints;
        this.qr = qr;
        this.workspace = workspace;
        this.codSettlements = codSettlements;
    }

    @GetMapping("/trang-chu")
    public String dashboard(
            @RequestParam(defaultValue = "ALL") vn.edu.hcmute.uteexpress.dto.tracking.ShipperWorkView work,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        org.springframework.data.domain.Page<Order> orderPage;
        try {
            orderPage = work == vn.edu.hcmute.uteexpress.dto.tracking.ShipperWorkView.ALL
                    ? trackingService.searchAssignedOrders(authentication.getName(), keyword, status, from, to, page)
                    : workspace.work(authentication.getName(), work, keyword, status, from, to, page);
        } catch (IllegalArgumentException exception) {
            // Bộ lọc sai (từ khóa quá dài, ngày ngược...) vẫn giữ trang và báo lỗi tại chỗ.
            model.addAttribute("filterError", exception.getMessage());
            orderPage = org.springframework.data.domain.Page.empty(
                    org.springframework.data.domain.PageRequest.of(0, 10));
        }
        String username = authentication.getName();
        var visible = orderPage.getContent();
        model.addAttribute("work", work);
        model.addAttribute("workCounts", workspace.counts(username));
        model.addAttribute("money", workspace.money(username, visible));
        model.addAttribute("retries", workspace.retries(username, visible));
        model.addAttribute("pickupFailures", workspace.pickupFailures(username, visible));
        model.addAttribute("pickupCounts", workspace.pickupCounts(username, visible));
        model.addAttribute("codHeld", codSettlements.held(username));
        model.addAttribute("maxPickupFailures", TrackingService.MAX_PICKUP_FAILURES);
        model.addAttribute("bulkMax", vn.edu.hcmute.uteexpress.service.tracking.ShipperBulkService.MAX_ORDERS);
        model.addAttribute("now", workspace.now());
        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("orderPage", orderPage);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedStatus", status);
        model.addAttribute("statuses", Order.OrderStatus.values());
        model.addAttribute("from", from);
        model.addAttribute("to", to);

        return "shipper/dashboard";
    }

    @GetMapping("/thong-ke")
    public String statistics(
            @RequestParam(
                    name = "period",
                    defaultValue = "DAY")
                    ShipperStatisticsPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication authentication,
            Model model) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        try {
            model.addAttribute("summary", shipperStatisticsService.getStatistics(authentication.getName(), period, from, to));
            model.addAttribute("customRange", from != null);
        } catch (IllegalArgumentException exception) {
            // Khoảng ngày sai: hiện thống kê mặc định và giữ lại ngày người dùng vừa nhập để sửa.
            model.addAttribute("statsError", exception.getMessage());
            model.addAttribute("summary", shipperStatisticsService.getStatistics(authentication.getName(), period, null, null));
            model.addAttribute("customRange", false);
            model.addAttribute("invalidFrom", from);
            model.addAttribute("invalidTo", to);
        }
        model.addAttribute(
                "periods",
                ShipperStatisticsPeriod.values());

        return "shipper/statistics";
    }

    @GetMapping("/thong-ke/xuat.csv")
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(defaultValue = "DAY") ShipperStatisticsPeriod period,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to, Authentication auth) {
        return org.springframework.http.ResponseEntity.ok().header("Content-Disposition", "attachment; filename=thong-ke-shipper.csv")
                .header("X-Content-Type-Options", "nosniff").cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(shipperStatisticsService.exportCsv(auth.getName(), period, from, to));
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
        model.addAttribute("money", workspace.money(authentication.getName(), java.util.List.of(assignedOrder)).get(id));
        model.addAttribute("retry", workspace.retries(authentication.getName(), java.util.List.of(assignedOrder)).get(id));
        model.addAttribute("now", workspace.now());
        model.addAttribute("history", journey.history(id, authentication.getName(), 0));
        model.addAttribute("canRetry", trackingService.canRetry(assignedOrder));
        model.addAttribute("pickupFailure", workspace.pickupFailures(authentication.getName(), java.util.List.of(assignedOrder)).get(id));
        model.addAttribute("pickupCount", trackingService.pickupFailures(assignedOrder));
        model.addAttribute("canReportPickupFailure", trackingService.canReportPickupFailure(assignedOrder));
        model.addAttribute("maxPickupFailures", TrackingService.MAX_PICKUP_FAILURES);
        model.addAttribute("currentProof", trackingService.hasCurrentProof(assignedOrder));
        model.addAttribute("failureReasons", DeliveryFailureReason.values());
        model.addAttribute("complaint", complaints.forOrder(assignedOrder, authentication.getName()).orElse(null));
        model.addAttribute(
                "deliveryProof",
                deliveryProofService.findByOrder(assignedOrder)
                        .orElse(null));

        return "shipper/order-detail";
    }

    @PostMapping("/don/{id}/bang-chung")
    public String saveDeliveryProof(
            @PathVariable Long id,
            @RequestParam(name = "proofImage", required = false)
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
        } catch (IllegalStateException | IllegalArgumentException exception) {
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
            @RequestParam(required = false) DeliveryFailureReason failureReason,
            @RequestParam(defaultValue = "") String note,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime nextAttemptAt,
            Authentication authentication,
            RedirectAttributes redirectAttributes) {
        if (isAnonymous(authentication)) {
            return "redirect:/dang-nhap";
        }

        try {
            trackingService.updateStatus(
                    id,
                    authentication.getName(),
                    newStatus, failureReason, note, nextAttemptAt);

            redirectAttributes.addFlashAttribute(
                    "message",
                    "Cập nhật trạng thái đơn thành công.");
        } catch (IllegalStateException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    exception.getMessage());
            if (newStatus == Order.OrderStatus.FAILED) {
                // Giữ lại nội dung đã nhập để Shipper sửa thay vì nhập lại từ đầu.
                redirectAttributes.addFlashAttribute("openForm", "delivery-failure");
                redirectAttributes.addFlashAttribute("formReason", failureReason);
                redirectAttributes.addFlashAttribute("formNote", note);
                redirectAttributes.addFlashAttribute("formNextAttemptAt", nextAttemptAt);
            }
        }

        return "redirect:/shipper/don/" + id;
    }

    @GetMapping("/quet-ma")
    public String scanPage() { return "shipper/scan"; }

    @PostMapping("/quet-ma/ma")
    public String scanCode(@RequestParam(defaultValue = "") String code, Authentication auth, RedirectAttributes flash) {
        try {
            code = code.strip(); qr.checkCode(code);
            var order = trackingService.findAssignedOrderByCode(code, auth.getName())
                    .orElseThrow(() -> new IllegalArgumentException("QR này không thuộc đơn được phân công cho bạn."));
            return "redirect:/shipper/don/" + order.getId();
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/shipper/quet-ma";
    }

    @PostMapping("/quet-ma")
    public String scan(@RequestParam(name = "qrImage", required = false) MultipartFile image, Authentication authentication,
            RedirectAttributes flash) {
        try {
            var order = trackingService.findAssignedOrderByCode(qr.decode(image), authentication.getName())
                    .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn được phân công cho bạn với QR này."));
            return "redirect:/shipper/don/" + order.getId();
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/shipper/quet-ma";
    }

    private boolean isAnonymous(Authentication authentication) {
        return authentication == null
                || authentication instanceof AnonymousAuthenticationToken;
    }
}
