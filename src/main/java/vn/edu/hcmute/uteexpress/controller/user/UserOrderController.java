package vn.edu.hcmute.uteexpress.controller.user;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;
import vn.edu.hcmute.uteexpress.service.OrderDraftService;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.SavedAddressService;
import vn.edu.hcmute.uteexpress.service.ServiceReviewService;

/**
 * Vai tro User (nguoi gui hang) - muc 03 ke hoach, TV1 phu trach.
 * Da lam trong ban demo co ban: tao don gui hang, xem lich su don cua minh (co filter
 * theo trang thai), tu huy don khi con dang cho lay hang.
 * Con lai (dia chi da luu, gio don nhieu don cho xac nhan cung luc, thanh toan
 * COD/VNPay/Momo, danh gia dich vu, theo doi realtime qua WebSocket, ap ma giam gia)
 * - lam tiep theo dung mau nay.
 */
@Controller
@RequestMapping("/nguoi-dung")
public class UserOrderController {

    /** So don hien tren moi trang cua lich su don. */
    private static final int ORDERS_PER_PAGE = 10;

    private final OrderService orderService;
    private final SavedAddressService savedAddressService;
    private final OrderDraftService orderDraftService;
    private final OrderPaymentService orderPaymentService;
    private final ServiceReviewService serviceReviewService;

    public UserOrderController(OrderService orderService, SavedAddressService savedAddressService,
                               OrderDraftService orderDraftService,
                               OrderPaymentService orderPaymentService,
                               ServiceReviewService serviceReviewService) {
        this.orderService = orderService;
        this.savedAddressService = savedAddressService;
        this.orderDraftService = orderDraftService;
        this.orderPaymentService = orderPaymentService;
        this.serviceReviewService = serviceReviewService;
    }

    /** Danh sach phuong thuc thanh toan cho o chon tren form tao don. */
    @ModelAttribute("paymentMethods")
    public OrderPayment.PaymentMethod[] paymentMethods() {
        return OrderPayment.PaymentMethod.values();
    }

    @GetMapping("/trang-chu")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        model.addAttribute("username", username);
        model.addAttribute("orderCount", orderService.findOrdersOfUser(username).size());
        model.addAttribute("addressCount", savedAddressService.countOfUser(username));
        model.addAttribute("unpaidCount", orderPaymentService.countUnpaidOfUser(username));
        return "user/dashboard";
    }

    @GetMapping("/tao-don")
    public String createOrderForm(Authentication authentication, Model model) {
        model.addAttribute("form", orderService.prepareCreateForm(authentication.getName()));
        addSavedAddressesToModel(model, authentication.getName());
        return "user/order-form";
    }

    @PostMapping("/tao-don")
    public String createOrder(@Valid @ModelAttribute("form") OrderCreateRequest form, BindingResult bindingResult,
                               Authentication authentication, Model model) {
        if (bindingResult.hasErrors()) {
            // Ve lai form thi phai nap lai so dia chi, neu khong 2 o chon nhanh se rong.
            addSavedAddressesToModel(model, authentication.getName());
            return "user/order-form";
        }
        Order order;
        try {
            order = orderService.createOrder(form, authentication.getName());
        } catch (IllegalStateException ex) {
            // Hay gap nhat o day la ma giam gia sai/het han - phai ve lai form kem loi
            // de nguoi dung sua, khong de vang ra trang bao loi he thong.
            addSavedAddressesToModel(model, authentication.getName());
            model.addAttribute("error", ex.getMessage());
            return "user/order-form";
        }

        // Tra bang vi dien tu thi dua thang sang trang thanh toan (gia lap) giong cac san that,
        // con COD thi thu tien luc giao nen chi can hien man hinh tao don thanh cong.
        if (form.getPaymentMethod() != null && form.getPaymentMethod().isOnlineWallet()) {
            return "redirect:/nguoi-dung/thanh-toan/" + order.getId();
        }

        model.addAttribute("order", order);
        model.addAttribute("payment", orderPaymentService.findByOrder(order).orElse(null));
        return "user/order-created";
    }

    /**
     * Nut "Them vao gio don" tren chinh form tao don: kiem tra du lieu y het luc tao don that,
     * nhung thay vi sinh van don ngay thi cat vao gio de nguoi dung gom nhieu don roi xac nhan mot the.
     */
    @PostMapping("/tao-don/them-vao-gio")
    public String addToCart(@Valid @ModelAttribute("form") OrderCreateRequest form, BindingResult bindingResult,
                            Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            addSavedAddressesToModel(model, authentication.getName());
            return "user/order-form";
        }
        try {
            orderDraftService.addDraft(form, authentication.getName());
        } catch (IllegalStateException ex) {
            addSavedAddressesToModel(model, authentication.getName());
            model.addAttribute("error", ex.getMessage());
            return "user/order-form";
        }
        redirectAttributes.addFlashAttribute("message",
                "Đã thêm vào giỏ đơn. Nhập tiếp đơn khác hoặc mở giỏ đơn để xác nhận gửi hàng.");
        // Quay lai form trong de nguoi dung nhap tiep don ke tiep cho nhanh.
        return "redirect:/nguoi-dung/tao-don";
    }

    @GetMapping("/don-hang")
    public String orderHistory(@RequestParam(name = "status", required = false) Order.OrderStatus status,
                                @RequestParam(name = "keyword", required = false) String keyword,
                                @RequestParam(name = "page", defaultValue = "0") int page,
                                Authentication authentication, Model model) {
        String username = authentication.getName();

        // Don moi nhat len dau. page am (nguoi dung sua tay tren URL) thi kep ve trang dau.
        Pageable pageable = PageRequest.of(Math.max(page, 0), ORDERS_PER_PAGE,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> orderPage = orderService.searchOrdersOfUser(username, status, keyword, pageable);

        model.addAttribute("orderPage", orderPage);
        model.addAttribute("orders", orderPage.getContent());
        model.addAttribute("payments", orderPaymentService.findPaymentsOfUser(username));
        model.addAttribute("reviews", serviceReviewService.findReviewsOfUser(username));
        model.addAttribute("statusValues", Order.OrderStatus.values());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("keyword", keyword);
        return "user/order-list";
    }

    @PostMapping("/don-hang/{id}/huy")
    public String cancelOrder(@PathVariable Long id, Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            orderService.cancelOrder(id, authentication.getName());
            redirectAttributes.addFlashAttribute("message", "Đã huỷ đơn hàng thành công.");
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/nguoi-dung/don-hang";
    }

    /**
     * Do so dia chi da luu vao model cho 2 o "Chon tu so dia chi" tren form tao don.
     * Chi goi o cac handler cua form tao don, khong dung @ModelAttribute dung chung
     * de cac trang khac (trang chu, lich su don) khoi phai chay them 2 cau truy van thua.
     */
    private void addSavedAddressesToModel(Model model, String username) {
        model.addAttribute("senderAddresses",
                savedAddressService.findByType(username, SavedAddress.AddressType.SENDER));
        model.addAttribute("receiverAddresses",
                savedAddressService.findByType(username, SavedAddress.AddressType.RECEIVER));
    }
}
