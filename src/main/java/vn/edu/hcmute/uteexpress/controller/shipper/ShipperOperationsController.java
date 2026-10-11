package vn.edu.hcmute.uteexpress.controller.shipper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriComponentsBuilder;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperWorkView;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.tracking.*;

/** Lộ trình hôm nay, cập nhật nhiều đơn và ghi nhận lấy hàng thất bại. */
@Controller
@RequestMapping("/shipper")
public class ShipperOperationsController {
    private final TrackingService tracking;
    private final ShipperRouteService routes;
    private final ShipperBulkService bulk;
    public ShipperOperationsController(TrackingService tracking, ShipperRouteService routes, ShipperBulkService bulk) {
        this.tracking = tracking; this.routes = routes; this.bulk = bulk;
    }

    @GetMapping("/lo-trinh")
    public String route(Authentication auth, Model model) {
        model.addAttribute("route", routes.today(auth.getName()));
        model.addAttribute("today", LocalDate.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
        return "shipper/route";
    }

    @PostMapping("/don/{id}/lay-hang-that-bai")
    public String pickupFailure(@PathVariable Long id, @RequestParam(required = false) DeliveryFailureReason failureReason,
            @RequestParam(defaultValue = "") String note,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime nextAttemptAt,
            Authentication auth, RedirectAttributes flash) {
        try {
            tracking.reportPickupFailure(id, auth.getName(), failureReason, note, nextAttemptAt);
            flash.addFlashAttribute("message", "Đã ghi nhận lấy hàng không thành công và báo cho người gửi.");
        } catch (IllegalStateException | IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("openForm", "pickup-failure");
            flash.addFlashAttribute("formReason", failureReason);
            flash.addFlashAttribute("formNote", note);
            flash.addFlashAttribute("formNextAttemptAt", nextAttemptAt);
        }
        return "redirect:/shipper/don/" + id;
    }

    /** Quay lại đúng bộ lọc danh sách; chỉ dựng URL nội bộ từ các tham số đã biết, không nhận URL tùy ý. */
    @PostMapping("/cap-nhat-nhieu")
    public String bulkUpdate(@RequestParam(name = "orderIds", required = false) List<Long> orderIds,
            @RequestParam(name = "status", required = false) Order.OrderStatus status,
            @RequestParam(required = false) ShipperWorkView work, @RequestParam(required = false) Order.OrderStatus filterStatus,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, Authentication auth, RedirectAttributes flash) {
        try {
            var result = bulk.update(auth.getName(), orderIds, status);
            flash.addFlashAttribute(result.failed().isEmpty() ? "message" : "error", result.summary());
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        var url = UriComponentsBuilder.fromPath("/shipper/trang-chu");
        if (work != null && work != ShipperWorkView.ALL) url.queryParam("work", work.name());
        if (filterStatus != null) url.queryParam("status", filterStatus.name());
        String term = keyword.strip();
        if (!term.isEmpty() && term.length() <= 100) url.queryParam("keyword", term);
        if (from != null) url.queryParam("from", from);
        if (to != null) url.queryParam("to", to);
        if (page > 0 && page <= 100_000) url.queryParam("page", page);
        return "redirect:" + url.encode().build().toUriString();
    }
}
