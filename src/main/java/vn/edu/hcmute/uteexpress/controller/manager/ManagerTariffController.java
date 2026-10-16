package vn.edu.hcmute.uteexpress.controller.manager;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService;

@Controller @RequestMapping("/manager")
public class ManagerTariffController {
    private final ShippingTariffService tariffs;
    public ManagerTariffController(ShippingTariffService tariffs) { this.tariffs = tariffs; }
    @GetMapping("/bang-cuoc")
    public String tariffs(Authentication auth, Model model) {
        model.addAttribute("tariffs", tariffs.findTariffs(auth.getName())); return "manager/tariffs";
    }
}

