package vn.edu.hcmute.uteexpress.controller.manager;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.edu.hcmute.uteexpress.service.manager.ManagerDashboardService;

/**
 * Vai tro Manager - muc 03 ke hoach, TV3 phu trach.
 * Cac chuc nang can lam: quan ly user, quan ly don toan he thong, phan cong shipper,
 * quan ly bang gia cuoc, quan ly khuyen mai, quan ly khu vuc/tuyen, thong ke doanh thu.
 */
@Controller
@RequestMapping("/manager")
public class ManagerController {

    private final ManagerDashboardService managerDashboardService;

    public ManagerController(ManagerDashboardService managerDashboardService) {
        this.managerDashboardService = managerDashboardService;
    }

    @GetMapping("/trang-chu")
    public String dashboard(Model model) {
        model.addAttribute("summary", managerDashboardService.getSummary());
        return "manager/dashboard";
    }

    // TODO: @GetMapping("/nguoi-dung"), @GetMapping("/don-hang"), @GetMapping("/thong-ke")
}
