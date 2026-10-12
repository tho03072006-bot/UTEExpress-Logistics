package vn.edu.hcmute.uteexpress.controller.shipper;

import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.service.tracking.CodSettlementService;

/** Shipper lập phiếu nộp tiền cước COD đã thu và theo dõi kết quả đối soát. */
@Controller
@RequestMapping("/shipper/doi-soat")
public class ShipperCodController {
    private final CodSettlementService settlements;
    public ShipperCodController(CodSettlementService settlements) { this.settlements = settlements; }

    /** Cả trang chung một giao dịch chỉ đọc (như trang chủ Shipper) để bớt lượt đi về database. */
    @GetMapping
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String overview(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        String username = auth.getName();
        model.addAttribute("held", settlements.held(username));
        model.addAttribute("unsettled", settlements.unsettled(username));
        model.addAttribute("pending", settlements.pending(username).orElse(null));
        model.addAttribute("history", settlements.history(username, page));
        model.addAttribute("maxOrders", CodSettlementService.MAX_ORDERS);
        return "shipper/cod";
    }

    @PostMapping
    public String submit(@RequestParam(name = "orderIds", required = false) List<Long> orderIds,
            @RequestParam(defaultValue = "") String declaredAmount, @RequestParam(defaultValue = "") String note,
            Authentication auth, RedirectAttributes flash) {
        try {
            var settlement = settlements.submit(auth.getName(), orderIds,
                    CodSettlementService.parseAmount(declaredAmount), note);
            flash.addFlashAttribute("message", "Đã gửi phiếu nộp tiền cho Manager xác nhận.");
            return "redirect:/shipper/doi-soat/" + settlement.getId();
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
            flash.addFlashAttribute("formAmount", declaredAmount);
            flash.addFlashAttribute("formNote", note);
            return "redirect:/shipper/doi-soat";
        }
    }

    @GetMapping("/{id}")
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public String detail(@PathVariable Long id, Authentication auth, Model model) {
        var settlement = settlements.viewOwn(id, auth.getName());
        model.addAttribute("settlement", settlement);
        model.addAttribute("items", settlements.items(settlement));
        model.addAttribute("viewer", "shipper");
        return "logistics/cod-settlement";
    }
}
