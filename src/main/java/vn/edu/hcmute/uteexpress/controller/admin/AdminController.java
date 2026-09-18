package vn.edu.hcmute.uteexpress.controller.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.edu.hcmute.uteexpress.service.manager.ManagerDashboardService;

/**
 * Vai tro Admin - muc 03 ke hoach, TV3 phu trach (ke thua quyen Manager + cau hinh he thong).
 * Cac chuc nang can lam: quan ly shipper, quan ly loai dich vu, chiet khau/hoa hong,
 * cau hinh he thong, quan ly log/giao dich, thong ke tong quan toan he thong.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final ManagerDashboardService managerDashboardService;

    public AdminController(ManagerDashboardService managerDashboardService) {
        this.managerDashboardService = managerDashboardService;
    }

    @GetMapping("/trang-chu")
    public String dashboard(Model model) {
        model.addAttribute("summary", managerDashboardService.getSummary());
        return "admin/dashboard";
    }
}
