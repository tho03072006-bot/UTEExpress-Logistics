package vn.edu.hcmute.uteexpress.controller.manager;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.service.routing.HubService;

/** Màn hình kho chi nhánh: nhận hàng tại quầy, xếp chuyến, xuất kho, nhận chuyến và nhận bổ sung kiện thiếu. */
@Controller
@RequestMapping("/manager/kho")
public class HubController {
    private final HubService hub;
    public HubController(HubService hub) { this.hub = hub; }

    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true) // một giao dịch cho cả trang
    public String overview(@RequestParam(required = false) Long branchId, Authentication auth, Model model) {
        model.addAttribute("hub", hub.overview(auth.getName(), branchId));
        return "manager/hub";
    }

    @PostMapping("/quay/{orderId}/nhan")
    public String receiveAtCounter(@PathVariable Long orderId, @RequestParam(required = false) Long branchId,
            Authentication auth, RedirectAttributes flash) {
        try {
            var route = hub.receiveAtCounter(auth.getName(), orderId);
            flash.addFlashAttribute("message", "Đã nhận " + route.getOrder().getTrackingCode() + " tại quầy. "
                    + (route.isInterprovincial() ? "Đơn nằm trong danh sách chờ xếp chuyến." : "Đơn đã sẵn sàng phân công Shipper giao."));
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return back(branchId, flash);
    }

    @PostMapping("/chuyen")
    public String create(@RequestParam(required = false) Long branchId, @RequestParam(required = false) Long destBranchId,
            @RequestParam(defaultValue = "") String vehiclePlate, @RequestParam(defaultValue = "") String driverName,
            @RequestParam(defaultValue = "") String driverPhone, @RequestParam(name = "orderIds", required = false) List<Long> orderIds,
            Authentication auth, RedirectAttributes flash) {
        try {
            var trip = hub.createTrip(auth.getName(), branchId, destBranchId, vehiclePlate, driverName, driverPhone, orderIds);
            flash.addFlashAttribute("message", "Đã tạo chuyến " + trip.getCode() + " với " + trip.getItemCount() + " kiện. Nhập mã niêm phong để xuất kho.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return back(branchId, flash);
    }

    @PostMapping("/chuyen/{id}/huy")
    public String cancel(@PathVariable Long id, @RequestParam long revision, @RequestParam(required = false) Long branchId,
            Authentication auth, RedirectAttributes flash) {
        try {
            hub.cancelTrip(auth.getName(), id, revision);
            flash.addFlashAttribute("message", "Đã hủy chuyến; các đơn quay lại danh sách chờ xếp chuyến.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return back(branchId, flash);
    }

    @PostMapping("/chuyen/{id}/xuat")
    public String depart(@PathVariable Long id, @RequestParam long revision, @RequestParam(defaultValue = "") String sealCode,
            @RequestParam(required = false) Long branchId, Authentication auth, RedirectAttributes flash) {
        try {
            hub.depart(auth.getName(), id, revision, sealCode);
            flash.addFlashAttribute("message", "Đã niêm phong và xuất kho. Kho nhận sẽ thấy chuyến trong mục chuyến đang tới.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return back(branchId, flash);
    }

    @PostMapping("/chuyen/{id}/nhan")
    public String arrive(@PathVariable Long id, @RequestParam long revision, @RequestParam(defaultValue = "") String sealCode,
            @RequestParam(name = "receivedOrderIds", required = false) List<Long> received, @RequestParam(defaultValue = "") String note,
            @RequestParam(required = false) Long branchId, Authentication auth, RedirectAttributes flash) {
        try {
            int missing = hub.arrive(auth.getName(), id, revision, sealCode, received, note);
            flash.addFlashAttribute("message", missing == 0 ? "Đã nhận đủ kiện vào kho. Các đơn đã sẵn sàng phân công giao."
                    : "Đã nhận chuyến, ghi thiếu " + missing + " kiện. Kiện thiếu nằm ở mục chờ nhận bổ sung.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return back(branchId, flash);
    }

    @PostMapping("/kien/{id}/nhan-bo-sung")
    public String receiveLate(@PathVariable Long id, @RequestParam(defaultValue = "") String note,
            @RequestParam(required = false) Long branchId, Authentication auth, RedirectAttributes flash) {
        try {
            hub.receiveLate(auth.getName(), id, note);
            flash.addFlashAttribute("message", "Đã nhận bổ sung kiện vào kho.");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return back(branchId, flash);
    }

    private static String back(Long branchId, RedirectAttributes flash) {
        if (branchId != null) flash.addAttribute("branchId", branchId);
        return "redirect:/manager/kho";
    }
}
