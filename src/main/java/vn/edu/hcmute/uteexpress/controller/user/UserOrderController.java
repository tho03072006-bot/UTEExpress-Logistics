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
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofService;

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
    private final vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService tariffs;
    private final DeliveryProofService deliveryProofService;
    private final vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService catalog;
    private final vn.edu.hcmute.uteexpress.service.routing.SavedAddressRoutingService savedRoutes;
    private final vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing;
    private final java.util.Map<String, long[]> quoteWindows = new java.util.concurrent.ConcurrentHashMap<>();

    public UserOrderController(OrderService orderService, SavedAddressService savedAddressService,
                               OrderDraftService orderDraftService,
                               OrderPaymentService orderPaymentService,
                               ServiceReviewService serviceReviewService,
                               vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService tariffs,
                               DeliveryProofService deliveryProofService,
                               vn.edu.hcmute.uteexpress.service.routing.RoutingCatalogService catalog,
                               vn.edu.hcmute.uteexpress.service.routing.SavedAddressRoutingService savedRoutes,
                               vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing) {
        this.orderService = orderService;
        this.savedAddressService = savedAddressService;
        this.orderDraftService = orderDraftService;
        this.orderPaymentService = orderPaymentService;
        this.serviceReviewService = serviceReviewService;
        this.tariffs=tariffs;
        this.deliveryProofService = deliveryProofService;
        this.catalog = catalog;
        this.savedRoutes = savedRoutes;
        this.routing = routing;
    }

    /** Danh sach phuong thuc thanh toan cho o chon tren form tao don. */
    @ModelAttribute("paymentMethods")
    public OrderPayment.PaymentMethod[] paymentMethods() {
        return new OrderPayment.PaymentMethod[] { OrderPayment.PaymentMethod.COD, OrderPayment.PaymentMethod.MOMO };
    }

    @GetMapping("/trang-chu")
    public String dashboard(Authentication authentication, Model model) {
        String username = authentication.getName();
        model.addAttribute("username", username);
        model.addAttribute("orderCount", orderService.countOrdersOfUser(username));
        model.addAttribute("recentOrders", orderService.recentOrdersOfUser(username));
        model.addAttribute("addressCount", savedAddressService.countOfUser(username));
        model.addAttribute("unpaidCount", orderPaymentService.countUnpaidOfUser(username));
        return "user/dashboard";
    }

    @GetMapping("/tao-don")
    @org.springframework.transaction.annotation.Transactional(readOnly = true) // một giao dịch cho cả trang
    public String createOrderForm(Authentication authentication, Model model) {
        var form = orderService.prepareCreateForm(authentication.getName());
        var selection = savedRoutes.selections(authentication.getName()).get(form.getSenderAddressId());
        if (selection != null) { form.setPickupProvinceCode(selection.provinceCode()); form.setPickupAreaCode(selection.areaCode()); }
        model.addAttribute("form", form);
        addSavedAddressesToModel(model, authentication.getName());
        return "user/order-form";
    }

    @PostMapping("/tao-don")
    public String createOrder(@Valid @ModelAttribute("form") OrderCreateRequest form, BindingResult bindingResult,
                               Authentication authentication, Model model) {
        validateSubmissionToken(form, bindingResult);
        if (bindingResult.hasErrors()) {
            // Ve lai form thi phai nap lai so dia chi, neu khong 2 o chon nhanh se rong.
            addSavedAddressesToModel(model, authentication.getName());
            return "user/order-form";
        }
        Order order;
        try {
            order = orderService.createOrder(form, authentication.getName());
        } catch (IllegalStateException | IllegalArgumentException ex) {
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

        return "redirect:/nguoi-dung/don-hang/" + order.getId() + "/da-tao";
    }

    /**
     * Báo giá theo cùng tuyến/khu vực với form mới. Chỉ để hiển thị; lúc tạo đơn server
     * tính lại từ đầu nên sửa số trên trình duyệt cũng không đổi được tiền.
     */
    @PostMapping(value = "/tao-don/bao-gia", produces = org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
    @org.springframework.web.bind.annotation.ResponseBody
    public org.springframework.http.ResponseEntity<java.util.Map<String, Object>> quote(
            @Valid @ModelAttribute vn.edu.hcmute.uteexpress.dto.OrderQuoteRequest form,
            BindingResult errors, Authentication authentication) {
        var noStore = org.springframework.http.CacheControl.noStore();
        if (errors.hasErrors())
            return org.springframework.http.ResponseEntity.ok().cacheControl(noStore)
                    .body(java.util.Map.of("ok", false, "message", "Nhập hai địa chỉ hợp lệ, dịch vụ và khối lượng từ 0,01–100 kg để xem cước."));
        if (!allowQuote(authentication.getName()))
            return org.springframework.http.ResponseEntity.status(429).cacheControl(noStore)
                    .body(java.util.Map.of("ok", false, "message", "Bạn xem báo giá quá nhanh. Đợi một chút rồi thử lại."));
        try {
            var price = routing.quote(form.toRoutingRequest());
            return org.springframework.http.ResponseEntity.ok().cacheControl(noStore).body(java.util.Map.of("ok", true,
                    "distanceKm", price.distanceKm(), "baseFee", price.baseFee(), "distanceFee", price.distanceFee(),
                    "routeFee", price.routeFee(), "dropOffDiscount", price.dropOffDiscount(), "total", price.total()));
        } catch (IllegalStateException | IllegalArgumentException ex) {
            return org.springframework.http.ResponseEntity.ok().cacheControl(noStore).body(java.util.Map.of("ok", false, "message", ex.getMessage()));
        }
    }

    private boolean allowQuote(String username) {
        long now = System.currentTimeMillis();
        long[] window = quoteWindows.compute(username, (key, value) ->
                value == null || now - value[0] >= 60_000 ? new long[]{now, 1} : new long[]{value[0], value[1] + 1});
        return window[1] <= 40;
    }

    @GetMapping("/don-hang/{id}/da-tao")
    public String createdOrder(@PathVariable Long id,Authentication authentication,Model model) {
        Order order;
        try { order=orderService.findOwnedOrder(id,authentication.getName()); }
        catch(IllegalStateException ex) { throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Không tìm thấy đơn hàng."); }
        model.addAttribute("order",order); model.addAttribute("payment",orderPaymentService.findByOrder(order).orElse(null));
        return "user/order-created";
    }

    /**
     * Nut "Them vao gio don" tren chinh form tao don: kiem tra du lieu y het luc tao don that,
     * nhung thay vi sinh van don ngay thi cat vao gio de nguoi dung gom nhieu don roi xac nhan mot the.
     */
    @PostMapping("/tao-don/them-vao-gio")
    public String addToCart(@Valid @ModelAttribute("form") OrderCreateRequest form, BindingResult bindingResult,
                            Authentication authentication, Model model, RedirectAttributes redirectAttributes) {
        validateSubmissionToken(form, bindingResult);
        if (bindingResult.hasErrors()) {
            addSavedAddressesToModel(model, authentication.getName());
            return "user/order-form";
        }
        try {
            orderDraftService.addDraft(form, authentication.getName());
        } catch (IllegalStateException | IllegalArgumentException ex) {
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

        // Sắp xếp ổn định kể cả nhiều đơn có cùng thời điểm tạo; URL sai bị từ chối thay vì sửa ngầm.
        Pageable pageable = PageRequest.of(page, ORDERS_PER_PAGE,
                Sort.by(Sort.Direction.DESC, "createdAt", "id"));
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

    /**
     * Trang chi tiet mot van don: thong tin gui/nhan, cuoc phi, thanh toan va
     * timeline hanh trinh. Kiem tra quyen so huu nam trong OrderService.
     */
    @GetMapping("/don-hang/{id}")
    public String orderDetail(@PathVariable Long id, Authentication authentication,
                              Model model, RedirectAttributes redirectAttributes) {
        Order order;
        try {
            order = orderService.findOwnedOrder(id, authentication.getName());
        } catch (IllegalStateException ex) {
            redirectAttributes.addFlashAttribute("error", ex.getMessage());
            return "redirect:/nguoi-dung/don-hang";
        }

        model.addAttribute("order", order);
        model.addAttribute("timeline", orderService.buildTimeline(order));
        model.addAttribute("route", routing.summary(order).orElse(null));
        var payment = orderPaymentService.findByOrder(order).orElse(null);
        model.addAttribute("payment", payment);
        model.addAttribute("collection", vn.edu.hcmute.uteexpress.dto.PaymentCollectionView.from(order, payment));
        model.addAttribute("review", serviceReviewService.findByOrderId(order.getId()).orElse(null));
        // Ảnh và chữ ký Shipper lưu khi giao; đơn chưa giao thì chưa có, trang tự ẩn khối này.
        model.addAttribute("deliveryProof", deliveryProofService.findViewableByOrder(order).orElse(null));
        return "user/order-detail";
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
        model.addAttribute("branches", catalog.branches()); model.addAttribute("areas", catalog.areas());
        model.addAttribute("dropOffDiscount", catalog.dropOffDiscount());
        model.addAttribute("savedAreas", savedRoutes.selections(username));
        model.addAttribute("currentTariffs",tariffs.currentTariffs());
        model.addAttribute("senderAddresses",
                savedAddressService.findByType(username, SavedAddress.AddressType.SENDER));
        model.addAttribute("receiverAddresses",
                savedAddressService.findByType(username, SavedAddress.AddressType.RECEIVER));
    }

    private void validateSubmissionToken(OrderCreateRequest form, BindingResult bindingResult) {
        if (!vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard.validToken(form.getSubmissionToken())
                || bindingResult.hasFieldErrors("submissionToken")) {
            form.setSubmissionToken(java.util.UUID.randomUUID().toString());
            if (!bindingResult.hasFieldErrors("submissionToken"))
                bindingResult.rejectValue("submissionToken", "expired", "Biểu mẫu cũ đã hết hiệu lực. Kiểm tra thông tin và gửi lại.");
        }
    }
}
