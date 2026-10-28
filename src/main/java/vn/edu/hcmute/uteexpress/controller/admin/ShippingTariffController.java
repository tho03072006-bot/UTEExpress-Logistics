package vn.edu.hcmute.uteexpress.controller.admin;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.admin.ShippingTariffUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService;

@Controller @RequestMapping("/admin/bang-cuoc")
public class ShippingTariffController {
    private final ShippingTariffService service;
    public ShippingTariffController(ShippingTariffService service) { this.service = service; }
    @GetMapping public String list(Authentication auth, Model model) {
        model.addAttribute("tariffs", service.findTariffs(auth.getName()));
        return "admin/tariffs";
    }
    @GetMapping("/{type}") public String edit(@PathVariable Order.ServiceType type, Authentication auth, Model model) {
        var tariff = service.findTariffs(auth.getName()).stream().filter(t -> t.getServiceType() == type)
            .findFirst().orElseThrow(() -> new IllegalArgumentException("Không tìm thấy bảng cước."));
        var form = new ShippingTariffUpdateRequest();
        form.setRevision(tariff.getRevision()); form.setBaseFee(tariff.getBaseFee()); form.setFeePerKg(tariff.getFeePerKg());
        model.addAttribute("form", form); model.addAttribute("type", type);
        return "admin/tariff-edit";
    }
    @PostMapping("/{type}") public String save(@PathVariable Order.ServiceType type,
            @Valid @ModelAttribute("form") ShippingTariffUpdateRequest form, BindingResult errors,
            Authentication auth, Model model, RedirectAttributes redirect) {
        model.addAttribute("type", type);
        if (errors.hasErrors()) { return "admin/tariff-edit"; }
        try { service.updateTariff(auth.getName(), type, form); }
        catch (IllegalArgumentException ex) { model.addAttribute("error", ex.getMessage()); return "admin/tariff-edit"; }
        redirect.addFlashAttribute("success", "Đã cập nhật cước. Chỉ áp dụng cho đơn mới; đơn cũ giữ nguyên.");
        return "redirect:/admin/bang-cuoc";
    }
}

