package vn.edu.hcmute.uteexpress.controller.manager;

import java.time.LocalDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.service.tracking.DispatchService;

@Controller
@RequestMapping("/manager/phan-cong")
public class DispatchController {
    private final DispatchService service;
    private final vn.edu.hcmute.uteexpress.service.tracking.ShipperWorkspaceService workspace;
    private final vn.edu.hcmute.uteexpress.service.routing.DispatchBulkService bulk;
    public DispatchController(DispatchService service, vn.edu.hcmute.uteexpress.service.tracking.ShipperWorkspaceService workspace,
            vn.edu.hcmute.uteexpress.service.routing.DispatchBulkService bulk) {
        this.service = service; this.workspace = workspace; this.bulk = bulk;
    }
    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int shipperPage, @RequestParam(required = false) Long branchId, Authentication auth, Model model) {
        var orders = service.pending(auth.getName(), branchId, page);
        model.addAttribute("orders", orders);
        model.addAttribute("money", workspace.money(auth.getName(), orders.getContent()));
        model.addAttribute("suggestions", service.suggestions(auth.getName(), orders.getContent()));
        model.addAttribute("branches", service.branches(auth.getName()));
        model.addAttribute("branchId", branchId);
        return "manager/dispatch";
    }
    @PostMapping("/hang-loat")
    public String bulk(@RequestParam(required = false) Long branchId, Authentication auth, RedirectAttributes flash) {
        var result = bulk.assign(auth.getName(), branchId);
        flash.addFlashAttribute("message", "Đã phân công " + result.assigned() + " đơn theo gợi ý.");
        flash.addFlashAttribute("remaining", result.remaining());
        if (branchId != null) flash.addAttribute("branchId", branchId);
        return "redirect:/manager/phan-cong";
    }
    @PostMapping
    public String assign(@RequestParam String trackingCode, @RequestParam Long shipperId,
            @RequestParam long expectedShipperId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime expectedUpdatedAt,
            @RequestParam String note, Authentication auth, RedirectAttributes flash) {
        try {
            service.assign(trackingCode, shipperId, expectedShipperId, expectedUpdatedAt, note, auth.getName());
            flash.addFlashAttribute("message", "Đã phân công và gửi thông báo cho Shipper.");
        } catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/manager/phan-cong";
    }
}
