package vn.edu.hcmute.uteexpress.controller.manager;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.entity.CodSettlement;
import vn.edu.hcmute.uteexpress.service.tracking.CodSettlementService;

/** Manager xác nhận hoặc từ chối phiếu nộp tiền COD của Shipper (phần Shipper - Nguyễn Hữu Tài). */
@Controller
@RequestMapping("/manager/doi-soat-cod")
public class ManagerCodController {
    private final CodSettlementService settlements;
    public ManagerCodController(CodSettlementService settlements) { this.settlements = settlements; }

    @GetMapping
    public String list(@RequestParam(required = false) CodSettlement.Status status,
            @RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        model.addAttribute("settlements", settlements.search(auth.getName(), status, page));
        model.addAttribute("summary", settlements.managerSummary(auth.getName()));
        model.addAttribute("statuses", CodSettlement.Status.values());
        model.addAttribute("selectedStatus", status);
        return "manager/cod-settlements";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        var settlement = settlements.viewAsManager(id, auth.getName());
        model.addAttribute("settlement", settlement);
        model.addAttribute("items", settlements.items(settlement));
        model.addAttribute("viewer", "staff");
        return "logistics/cod-settlement";
    }

    @PostMapping("/{id}")
    public String review(@PathVariable Long id, @RequestParam(defaultValue = "-1") long revision,
            @RequestParam(defaultValue = "") String decision, @RequestParam(defaultValue = "") String note,
            Authentication auth, RedirectAttributes flash) {
        try {
            if (!decision.equals("CONFIRM") && !decision.equals("REJECT"))
                throw new IllegalArgumentException("Hãy chọn xác nhận hoặc từ chối phiếu.");
            boolean approve = decision.equals("CONFIRM");
            settlements.review(auth.getName(), id, revision, approve, note);
            flash.addFlashAttribute("message", approve ? "Đã xác nhận phiếu nộp tiền." : "Đã từ chối phiếu; các đơn trở lại danh sách chờ nộp.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("formNote", note);
        }
        return "redirect:/manager/doi-soat-cod/" + id;
    }
}
