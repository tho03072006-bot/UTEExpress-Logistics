package vn.edu.hcmute.uteexpress.controller.user;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.SavedAddressRequest;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;
import vn.edu.hcmute.uteexpress.service.SavedAddressService;

/**
 * Sổ địa chỉ đã lưu của người gửi hàng - TV1 phụ trách (việc 1).
 * Controller chỉ nhận dữ liệu, gọi Service và điều hướng; mọi kiểm tra nghiệp vụ
 * (quyền sở hữu, trùng nhãn, địa chỉ mặc định) đều nằm trong SavedAddressService.
 */
@Controller
@RequestMapping("/nguoi-dung/dia-chi")
public class SavedAddressController {

    private final SavedAddressService savedAddressService;
    private final vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService catalog;
    private final vn.edu.hcmute.uteexpress.service.routing.SavedAddressRoutingService routing;

    public SavedAddressController(SavedAddressService savedAddressService,
            vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService catalog,
            vn.edu.hcmute.uteexpress.service.routing.SavedAddressRoutingService routing) {
        this.savedAddressService = savedAddressService;
        this.catalog = catalog; this.routing = routing;
    }
    @ModelAttribute public void areaOptions(Model model) {
        model.addAttribute("branches", catalog.branches()); model.addAttribute("areas", catalog.areas());
    }

    /** Danh sách loại địa chỉ dùng chung cho các ô select trên form. */
    @ModelAttribute("addressTypes")
    public SavedAddress.AddressType[] addressTypes() {
        return SavedAddress.AddressType.values();
    }

    @GetMapping
    public String listAddresses(Authentication authentication, Model model) {
        model.addAttribute("addressGroups", savedAddressService.findGroupedByType(authentication.getName()));
        return "user/address-list";
    }

    @GetMapping("/them")
    public String createForm(Model model) {
        model.addAttribute("form", new SavedAddressRequest());
        model.addAttribute("editingId", null);
        return "user/address-form";
    }

    @PostMapping("/them")
    public String createAddress(@Valid @ModelAttribute("form") SavedAddressRequest form,
                                BindingResult bindingResult, Authentication authentication,
                                Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editingId", null);
            return "user/address-form";
        }
        try {
            savedAddressService.create(form, authentication.getName());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            model.addAttribute("editingId", null);
            model.addAttribute("error", ex.getMessage());
            return "user/address-form";
        }
        redirectAttributes.addFlashAttribute("message", "Đã lưu địa chỉ mới vào sổ địa chỉ.");
        return "redirect:/nguoi-dung/dia-chi";
    }

    @GetMapping("/{id}/sua")
    public String editForm(@PathVariable Long id, Authentication authentication,
                           Model model, RedirectAttributes redirectAttributes) {
        try {
            SavedAddress address = savedAddressService.findOwned(id, authentication.getName());
            var form = SavedAddressRequest.from(address);
            var selection = routing.selections(authentication.getName()).get(id);
            if (selection != null) { form.setProvinceCode(selection.provinceCode()); form.setAreaCode(selection.areaCode()); }
            model.addAttribute("form", form);
            model.addAttribute("editingId", id);
            return "user/address-form";
        } catch (IllegalStateException | IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/nguoi-dung/dia-chi";
        }
    }

    @PostMapping("/{id}/sua")
    public String updateAddress(@PathVariable Long id,
                                @Valid @ModelAttribute("form") SavedAddressRequest form,
                                BindingResult bindingResult, Authentication authentication,
                                Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("editingId", id);
            return "user/address-form";
        }
        try {
            savedAddressService.update(id, form, authentication.getName());
        } catch (IllegalStateException | IllegalArgumentException ex) {
            model.addAttribute("editingId", id);
            model.addAttribute("error", ex.getMessage());
            return "user/address-form";
        }
        redirectAttributes.addFlashAttribute("message", "Đã cập nhật địa chỉ.");
        return "redirect:/nguoi-dung/dia-chi";
    }

    @PostMapping("/{id}/xoa")
    public String deleteAddress(@PathVariable Long id, Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            savedAddressService.delete(id, authentication.getName());
            redirectAttributes.addFlashAttribute("message", "Đã xoá địa chỉ khỏi sổ địa chỉ.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/nguoi-dung/dia-chi";
    }

    @PostMapping("/{id}/mac-dinh")
    public String markAsDefault(@PathVariable Long id, Authentication authentication,
                                RedirectAttributes redirectAttributes) {
        try {
            savedAddressService.markAsDefault(id, authentication.getName());
            redirectAttributes.addFlashAttribute("message", "Đã đặt làm địa chỉ mặc định.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/nguoi-dung/dia-chi";
    }
}
