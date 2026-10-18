package vn.edu.hcmute.uteexpress.controller.admin;

import java.security.Principal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccountService;

@Controller
public class StaffProfileController {
    private final StaffAccountService service;
    public StaffProfileController(StaffAccountService service) { this.service = service; }
    @GetMapping("/noi-bo/tai-khoan")
    public String showOwnProfile(Principal actor, Model model) {
        model.addAttribute("profile", service.findOwnProfile(actor.getName())); return "admin/profile";
    }
}
