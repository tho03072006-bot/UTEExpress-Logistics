package vn.edu.hcmute.uteexpress.controller.manager;

import jakarta.validation.Valid;
import java.time.*;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.PromoCode;
import vn.edu.hcmute.uteexpress.service.manager.*;

@Controller @RequestMapping("/manager")
public class ManagerPromotionController {
    private final ManagerPromotionService service;
    private final Clock clock;
    public ManagerPromotionController(ManagerPromotionService service, Clock clock) { this.service = service; this.clock = clock; }
    @GetMapping("/khuyen-mai")
    public String promotions(@RequestParam(defaultValue = "0") int page, Authentication auth, Model model) {
        model.addAttribute("promotions", service.findPromotions(auth.getName(), page));
        return "manager/promotions";
    }
    @GetMapping("/khuyen-mai/tao")
    public String create(Model model) {
        var form = new PromotionRequest();
        form.setDiscountType(PromoCode.DiscountType.PERCENT);
        form.setMinOrderAmount(java.math.BigDecimal.ZERO); form.setUsageLimit(100);
        form.setStartAt(LocalDateTime.now(clock).withSecond(0).withNano(0));
        form.setEndAt(form.getStartAt().plusDays(30));
        model.addAttribute("form", form);
        return promotionForm(null, model);
    }
    @GetMapping("/khuyen-mai/{id}")
    public String edit(@PathVariable Long id, Authentication auth, Model model) {
        model.addAttribute("form", service.preparePromotion(auth.getName(), id));
        return promotionForm(id, model);
    }
    @PostMapping({"/khuyen-mai/tao", "/khuyen-mai/{id}"})
    public String save(@PathVariable(required = false) Long id, @Valid @ModelAttribute("form") PromotionRequest form,
            BindingResult errors, Authentication auth, Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) { return promotionForm(id, model); }
        try { service.savePromotion(auth.getName(), id, form); }
        catch (IllegalArgumentException ex) { model.addAttribute("error", ex.getMessage()); return promotionForm(id, model); }
        redirect.addFlashAttribute("success", "Đã lưu khuyến mãi. Không xóa mã để bảo toàn lịch sử thanh toán.");
        return "redirect:/manager/khuyen-mai";
    }
    private String promotionForm(Long id, Model model) {
        model.addAttribute("id", id); model.addAttribute("types", PromoCode.DiscountType.values());
        return "manager/promotion-edit";
    }

    @PostMapping("/khuyen-mai/{id}/trang-thai")
    public String toggle(@PathVariable Long id, @Valid @ModelAttribute PromotionStatusRequest form,
            BindingResult errors, Authentication auth, RedirectAttributes redirect) {
        if (errors.hasErrors()) { redirect.addFlashAttribute("error", "Trạng thái hoặc phiên bản khuyến mãi không hợp lệ."); }
        else {
            try { service.changePromotionStatus(auth.getName(), id, form); redirect.addFlashAttribute("success", "Đã đổi trạng thái khuyến mãi."); }
            catch (IllegalArgumentException ex) { redirect.addFlashAttribute("error", ex.getMessage()); }
        }
        return "redirect:/manager/khuyen-mai";
    }
}

