package vn.edu.hcmute.uteexpress.controller.user;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.service.OrderService;

/**
 * Vai tro User (nguoi gui hang) - muc 03 ke hoach, TV1 phu trach.
 * Da lam trong ban demo co ban: tao don gui hang, xem lich su don cua minh.
 * Con lai (dia chi da luu, gio don, thanh toan COD/VNPay/Momo, danh gia dich vu,
 * theo doi realtime qua WebSocket, ap ma giam gia) - lam tiep theo dung mau nay.
 */
@Controller
@RequestMapping("/nguoi-dung")
public class UserOrderController {

    private final OrderService orderService;

    public UserOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/trang-chu")
    public String dashboard(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("orderCount", orderService.findOrdersOfUser(authentication.getName()).size());
        return "user/dashboard";
    }

    @GetMapping("/tao-don")
    public String createOrderForm(Model model) {
        model.addAttribute("form", new OrderCreateRequest());
        return "user/order-form";
    }

    @PostMapping("/tao-don")
    public String createOrder(@Valid @ModelAttribute("form") OrderCreateRequest form, BindingResult bindingResult,
                               Authentication authentication, Model model) {
        if (bindingResult.hasErrors()) {
            return "user/order-form";
        }
        var order = orderService.createOrder(form, authentication.getName());
        model.addAttribute("order", order);
        return "user/order-created";
    }

    @GetMapping("/don-hang")
    public String orderHistory(Authentication authentication, Model model) {
        model.addAttribute("orders", orderService.findOrdersOfUser(authentication.getName()));
        return "user/order-list";
    }
}
