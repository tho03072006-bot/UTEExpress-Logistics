package vn.edu.hcmute.uteexpress.controller.admin;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.admin.TariffScheduleRequest;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.admin.TariffVersionService;
@Controller
public class TariffScheduleController {
    private final TariffVersionService service;
    private final java.time.Clock clock;
    public TariffScheduleController(TariffVersionService service, java.time.Clock clock) { this.service = service; this.clock = clock; }
    @GetMapping({"/admin/lich-cuoc", "/manager/lich-cuoc"})
    public String list(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        model.addAttribute("versions", service.list(auth.getName(), page)); model.addAttribute("services", Order.ServiceType.values());
        model.addAttribute("now", java.time.LocalDateTime.now(clock));
        return "admin/tariff-schedule";
    }
    @PostMapping("/admin/lich-cuoc")
    public String schedule(@Valid @ModelAttribute TariffScheduleRequest form, BindingResult errors, Authentication auth, RedirectAttributes flash) {
        if (errors.hasErrors()) flash.addFlashAttribute("error", "Kiểm tra dịch vụ, số tiền nguyên, khoảng hiệu lực và lý do.");
        else { try { service.schedule(auth.getName(), form); flash.addFlashAttribute("message", "Đã lập lịch giá. Đơn cũ giữ nguyên cước."); }
            catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); } }
        flash.addFlashAttribute("submitted", form); return "redirect:/admin/lich-cuoc";
    }
    @PostMapping("/admin/lich-cuoc/{id}/huy")
    public String cancel(@PathVariable Long id, @RequestParam long revision, @RequestParam String reason,
            Authentication auth, RedirectAttributes flash) {
        try { service.cancel(auth.getName(), id, revision, reason); flash.addFlashAttribute("message", "Đã hủy lịch chưa áp dụng."); }
        catch (IllegalArgumentException ex) { flash.addFlashAttribute("error", ex.getMessage()); }
        return "redirect:/admin/lich-cuoc";
    }
}
