package vn.edu.hcmute.uteexpress.controller.manager;

import java.time.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.manager.ManagerOrderService;

@Controller
@RequestMapping("/manager/don-hang")
public class ManagerOrderController {
    private final ManagerOrderService service;
    private final Clock clock;
    public ManagerOrderController(ManagerOrderService service, Clock clock) {
        this.service = service;
        this.clock = clock;
    }

    @GetMapping
    public String listOrders(@RequestParam(defaultValue = "") String tracking,
            @RequestParam(required = false) Order.OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page, Model model) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null ? end.minusDays(29) : from;
        model.addAttribute("orders", service.findOrders(tracking, status, start, end, page));
        model.addAttribute("tracking", tracking);
        model.addAttribute("status", status);
        model.addAttribute("statuses", Order.OrderStatus.values());
        model.addAttribute("statusLabels", vn.edu.hcmute.uteexpress.dto.manager.ManagerOrderResponse.getStatusLabels());
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        model.addAttribute("today", LocalDate.now(clock));
        return "manager/orders";
    }
}
