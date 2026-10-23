package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.HomeStatistics;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.dto.OrderTimelineStep;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.entity.PromoCode;
import vn.edu.hcmute.uteexpress.entity.SavedAddress;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.PromoCodeService;
import vn.edu.hcmute.uteexpress.service.SavedAddressService;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OrderServiceImpl implements OrderService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final AppUserRepository appUserRepository;
    private final SavedAddressService savedAddressService;
    private final OrderPaymentService orderPaymentService;
    private final PromoCodeService promoCodeService;
    private final vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository history;
    private final java.time.Clock clock;
    private final vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService tariffService;
    private final jakarta.validation.Validator validator;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard submissions;
    private final vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing;

    public OrderServiceImpl(OrderRepository orderRepository, AppUserRepository appUserRepository,
                            SavedAddressService savedAddressService,
                            OrderPaymentService orderPaymentService,
                            PromoCodeService promoCodeService,
                            vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository history,
                            java.time.Clock clock,
                            vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService tariffService,
                            jakarta.validation.Validator validator, org.springframework.context.ApplicationEventPublisher events,
                            vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard submissions,
                            vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing) {
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
        this.savedAddressService = savedAddressService;
        this.orderPaymentService = orderPaymentService;
        this.promoCodeService = promoCodeService;
        this.history = history;
        this.clock = clock;
        this.tariffService = tariffService;
        this.validator = validator;
        this.events = events;
        this.submissions = submissions;
        this.routing = routing;
    }

    @Override
    public OrderCreateRequest prepareCreateForm(String username) {
        OrderCreateRequest form = new OrderCreateRequest();
        form.setSubmissionToken(java.util.UUID.randomUUID().toString());
        form.setServiceType(Order.ServiceType.STANDARD);
        form.setPaymentMethod(OrderPayment.PaymentMethod.COD);

        // Dia chi lay hang cua nguoi gui gan nhu khong doi giua cac don nen dien san cho tien.
        savedAddressService.findDefaultOfType(username, SavedAddress.AddressType.SENDER)
                .ifPresent(address -> {
                    form.setSenderAddressId(address.getId());
                    form.setSenderName(address.getContactName());
                    form.setSenderAddress(address.getAddressLine());
                });
        return form;
    }

    @Override
    @Transactional
    public Order createOrder(OrderCreateRequest request, String senderUsername) {
        if (request == null) { throw new IllegalArgumentException("Thiếu dữ liệu đơn hàng."); }
        var errors = validator.validate(request);
        if (!errors.isEmpty()) { throw new IllegalArgumentException(errors.iterator().next().getMessage()); }
        // Khóa người gửi trước khi kiểm tra token: hai request đồng thời cùng tài khoản
        // không thể cùng vượt qua bước "chưa có" rồi tạo hai đơn trên database chung.
        AppUser sender = appUserRepository.findLockedByUsername(senderUsername)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung: " + senderUsername));

        if (!sender.isEnabled() || sender.isLockedByAdmin() || sender.getRole()!=AppUser.Role.USER)
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người gửi đang hoạt động được tạo đơn.");
        String requestHash = vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard.fingerprint(request);
        // HTTP bắt buộc có token; giữ tương thích lời gọi nội bộ cũ không truyền token.
        var submission = request.getSubmissionToken() == null ? null
            : submissions.reserve(request, sender, vn.edu.hcmute.uteexpress.entity.OrderFormSubmission.Purpose.DIRECT);
        if (request.getSubmissionToken()!=null) {
            var existing=orderRepository.findByCreationToken(request.getSubmissionToken());
            if(existing.isPresent()) {
                if(!existing.get().getSender().getId().equals(sender.getId()) || !requestHash.equals(existing.get().getCreationHash()))
                    throw new IllegalStateException("Phiên biểu mẫu đã được sử dụng cho dữ liệu khác. Hãy mở lại trang tạo đơn.");
                submission.bindResource(existing.get().getId());
                return existing.get();
            }
            if (submission.getResourceId() != null) throw vn.edu.hcmute.uteexpress.service.OrderSubmissionGuard.reused();
        }

        var routeQuote = routing.quote(request);
        Order order = new Order();
        order.setCreationToken(request.getSubmissionToken()); order.setCreationHash(requestHash);
        order.setTrackingCode(generateTrackingCode());
        order.setSender(sender);
        order.setSenderName(request.getSenderName());
        order.setSenderAddress(routing.fullAddress(request.getSenderAddress(), routeQuote.pickupArea()));
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(routing.fullAddress(request.getReceiverAddress(), routeQuote.deliveryArea()));
        order.setWeightKg(request.getWeightKg());
        order.setServiceType(request.getServiceType());
        BigDecimal fee = routeQuote.total();
        order.setShippingFee(fee);
        order.setStatus(Order.OrderStatus.PENDING_PICKUP);
        var now = java.time.LocalDateTime.now(clock);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);

        // Kiem tra ma giam gia TRUOC khi luu don: ma sai thi nem loi ngay, khong tao don do dang.
        PromoCode promoCode = null;
        BigDecimal discount = BigDecimal.ZERO;
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            promoCode = promoCodeService.requireUsableCode(request.getPromoCode(), fee);
            discount = promoCodeService.calculateDiscount(promoCode, fee);
        }

        Order savedOrder = orderRepository.save(order);
        routing.attach(savedOrder, routeQuote);
        // Moi van don deu co dung mot ban ghi thanh toan, tao ngay tai day de khong bao gio
        // ton tai don "khong biet tra bang gi" trong CSDL.
        orderPaymentService.createForOrder(savedOrder, request.getPaymentMethod(), promoCode, discount);
        if (promoCode != null) {
            promoCodeService.markUsed(promoCode);
        }
        history.save(new vn.edu.hcmute.uteexpress.entity.OrderStatusHistory(savedOrder, sender,
                Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.PENDING_PICKUP, null,
                "Người gửi tạo vận đơn.", null, now));
        if (submission != null) submission.bindResource(savedOrder.getId());
        return savedOrder;
    }

    @Override
    public List<Order> findOrdersOfUser(String username) {
        return findOrdersOfUser(username, null);
    }

    @Override
    public List<Order> findOrdersOfUser(String username, Order.OrderStatus statusFilter) {
        AppUser sender = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung: " + username));
        if (statusFilter == null) {
            return orderRepository.findBySender(sender);
        }
        return orderRepository.findBySenderAndStatus(sender, statusFilter);
    }

    @Override
    public Page<Order> searchOrdersOfUser(String username, Order.OrderStatus statusFilter,
                                          String keyword, Pageable pageable) {
        AppUser sender = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung: " + username));

        // O tim kiem de trong thi coi nhu khong tim, khong phai tim chuoi rong.
        String trimmed = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return orderRepository.searchOrdersOfSender(sender, statusFilter, trimmed, pageable);
    }

    @Override
    public Optional<Order> findByTrackingCode(String trackingCode) {
        return orderRepository.findByTrackingCode(trackingCode);
    }
    @Override @Transactional(readOnly=true)
    public Optional<Order> findOwnedOrderByCode(String trackingCode,String username) {
        return orderRepository.findByTrackingCodeAndSenderUsername(trackingCode,username);
    }
    @Override public boolean canCancelOrder(Order order,String username) {
        return order!=null && order.getSender()!=null && order.getSender().getUsername().equals(username)
                && order.getStatus()==Order.OrderStatus.PENDING_PICKUP;
    }

    @Override
    public Order findOwnedOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này."));
        if (!order.getSender().getUsername().equals(username)) {
            // Cung mot cau bao loi voi truong hop khong ton tai - xem giai thich o OrderService.
            throw new IllegalStateException("Không tìm thấy đơn hàng này.");
        }
        return order;
    }

    @Override
    public List<OrderTimelineStep> buildTimeline(Order order) {
        Order.OrderStatus current = order.getStatus();

        // Ba trang thai ket thuc bat thuong: don dung lai giua duong, khong di het cac moc.
        boolean cancelled = current == Order.OrderStatus.CANCELLED;
        boolean failed = current == Order.OrderStatus.FAILED;
        boolean returned = current == Order.OrderStatus.RETURNED;
        // Don cu chua co tuyen thi giu dung 4 moc nhu truoc.
        var route = routing.routeOf(order.getId()).orElse(null);
        boolean dropOff = route != null && route.isDropOff();
        var origin = route == null ? null : route.getOriginBranch();

        List<OrderTimelineStep> steps = new ArrayList<>();

        // Moc 1 luon da xong - co don nghia la da tao don.
        steps.add(dropOff
                ? new OrderTimelineStep("Chờ bạn mang hàng tới chi nhánh",
                        "Mang hàng tới quầy " + origin.getName() + ": " + origin.getHubAddress() + ".",
                        true, current == Order.OrderStatus.PENDING_PICKUP, order.getCreatedAt())
                : new OrderTimelineStep("Chờ lấy hàng",
                        "Đơn đã được tạo, đang chờ shipper tới lấy hàng.",
                        true, current == Order.OrderStatus.PENDING_PICKUP, order.getCreatedAt()));

        if (cancelled) {
            // Don bi huy khi con dang cho lay hang nen khong co moc nao khac nua.
            steps.add(new OrderTimelineStep("Đã hủy",
                    dropOff ? "Đơn đã được hủy, chi nhánh sẽ không nhận hàng." : "Đơn đã được hủy, shipper sẽ không tới lấy hàng.",
                    true, true, order.getUpdatedAt()));
            return steps;
        }

        int rank = statusRank(current);
        int stage = route == null ? -1 : stageRank(route.getStage());
        boolean hubs = route != null && route.isInterprovincial();
        boolean pickedUpHere = current == Order.OrderStatus.PICKED_UP && (!hubs || stage == 0);

        steps.add(dropOff
                ? new OrderTimelineStep("Đã nhận tại quầy " + origin.getName(),
                        origin.getName() + " đã nhận hàng bạn mang tới.",
                        rank >= 1, pickedUpHere, pickedUpHere ? order.getUpdatedAt() : null)
                : new OrderTimelineStep("Đã lấy hàng",
                        "Shipper đã nhận hàng từ người gửi.",
                        rank >= 1, pickedUpHere, pickedUpHere ? order.getUpdatedAt() : null));

        if (hubs) {
            // Lien tinh: hang qua kho gui, chuyen trung chuyen roi kho nhan truoc khi giao.
            var dest = route.getDestBranch();
            boolean active = current == Order.OrderStatus.PICKED_UP;
            steps.add(hubStep("Tại kho gửi", "Hàng nằm trong kho " + origin.getName() + ", chờ xếp chuyến.",
                    rank >= 2 || stage >= 1, active && stage == 1, order));
            steps.add(hubStep("Đang trung chuyển", "Hàng đang trên chuyến từ " + origin.getName() + " tới " + dest.getName() + ".",
                    rank >= 2 || stage >= 2, active && stage == 2, order));
            steps.add(hubStep("Tới kho nhận", "Hàng đã tới kho " + dest.getName() + ", chờ shipper giao.",
                    rank >= 2 || stage >= 3, active && stage >= 3, order));
        }

        steps.add(new OrderTimelineStep("Đang giao",
                "Hàng đang trên đường tới người nhận.",
                rank >= 2, current == Order.OrderStatus.IN_TRANSIT, timeOf(order, current, Order.OrderStatus.IN_TRANSIT)));

        boolean returnLeg = route != null && route.isReturnLeg();
        if (failed || (returned && returnLeg)) {
            steps.add(new OrderTimelineStep("Giao thất bại",
                    "Không giao được cho người nhận. Shipper sẽ liên hệ để xử lý tiếp.",
                    true, failed && !returnLeg, failed && !returnLeg ? order.getUpdatedAt() : null));
            if (returnLeg) {
                // B9: hàng hoàn đi chuyến trung chuyển ngược về chi nhánh gửi.
                var dest = route.getDestBranch();
                int back = returned ? 4 : switch (route.getStage()) { case DEST_HUB -> 1; case LINEHAUL -> 2; default -> 3; };
                steps.add(hubStep("Hàng hoàn về kho " + dest.getName(), "Hàng không giao được đã về kho " + dest.getName()
                        + ", chờ chuyến về " + origin.getName() + ".", back >= 1, !returned && back == 1, order));
                steps.add(hubStep("Hàng hoàn đang trung chuyển", "Hàng hoàn đang trên chuyến từ " + dest.getName() + " về " + origin.getName() + ".",
                        back >= 2, !returned && back == 2, order));
                steps.add(hubStep("Hàng hoàn về kho " + origin.getName(), "Hàng hoàn đã về kho " + origin.getName() + ", chi nhánh sẽ trả hàng cho bạn.",
                        back >= 3, !returned && back == 3, order));
                if (returned) steps.add(new OrderTimelineStep("Đã hoàn trả", "Hàng đã được chuyển trả về cho người gửi.", true, true, order.getUpdatedAt()));
            }
        } else if (returned) {
            steps.add(new OrderTimelineStep("Đã hoàn trả",
                    "Hàng đã được chuyển trả về cho người gửi.",
                    true, true, order.getUpdatedAt()));
        } else {
            steps.add(new OrderTimelineStep("Giao thành công",
                    "Người nhận đã nhận được hàng.",
                    rank >= 3, current == Order.OrderStatus.DELIVERED, timeOf(order, current, Order.OrderStatus.DELIVERED)));
        }

        return steps;
    }

    private static OrderTimelineStep hubStep(String label, String description, boolean done, boolean current, Order order) {
        return new OrderTimelineStep(label, description, done, current, current ? order.getUpdatedAt() : null);
    }

    /** Thu tu chang tren tuyen; LAST_MILE xep sau kho nhan. */
    private static int stageRank(vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage stage) {
        return switch (stage) {
            case PICKUP -> 0;
            case ORIGIN_HUB -> 1;
            case LINEHAUL -> 2;
            case DEST_HUB -> 3;
            case LAST_MILE -> 4;
        };
    }

    /**
     * Thu tu cua trang thai tren luong binh thuong, dung de biet moc nao da di qua.
     * Cac trang thai ket thuc bat thuong khong nam tren truc nay nen tra ve 0.
     */
    private int statusRank(Order.OrderStatus status) {
        return switch (status) {
            case PENDING_PICKUP -> 0;
            case PICKED_UP -> 1;
            case IN_TRANSIT -> 2;
            case DELIVERED -> 3;
            default -> 0;
        };
    }

    /**
     * Chi moc dang dung moi biet chac thoi diem (updated_at). Cac moc da qua truoc do khong
     * co du lieu vi he thong chua luu lich su doi trang thai - tra null de giao dien de trong.
     */
    private java.time.LocalDateTime timeOf(Order order, Order.OrderStatus current,
                                           Order.OrderStatus step) {
        return current == step ? order.getUpdatedAt() : null;
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId, String username) {
        Order order = orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này."));

        if (!order.getSender().getUsername().equals(username)) {
            throw new IllegalStateException("Bạn không có quyền huỷ đơn hàng này.");
        }
        if (!canCancelOrder(order,username)) {
            throw new IllegalStateException("Chỉ huỷ được đơn khi shipper chưa tới lấy hàng.");
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        order.setUpdatedAt(java.time.LocalDateTime.now(clock));
        orderRepository.save(order);
        history.save(new vn.edu.hcmute.uteexpress.entity.OrderStatusHistory(order, order.getSender(),
                Order.OrderStatus.PENDING_PICKUP, Order.OrderStatus.CANCELLED, null,
                "Người gửi hủy vận đơn trước khi lấy hàng.", null, java.time.LocalDateTime.now(clock)));
        events.publishEvent(new vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent(
                order.getId(), order.getTrackingCode(), order.getStatus().name(), order.getUpdatedAt()));
    }

    @Override
    public BigDecimal estimateFee(Order.ServiceType serviceType, double weightKg) {
        return calculateFee(serviceType, weightKg);
    }

    @Override
    @Transactional(readOnly = true)
    public HomeStatistics getHomeStatistics() {
        long deliveredOrders = orderRepository.countByStatus(Order.OrderStatus.DELIVERED);
        long ordersInTransit = orderRepository.countByStatusIn(
                List.of(Order.OrderStatus.PICKED_UP, Order.OrderStatus.IN_TRANSIT));
        long activeCustomers = appUserRepository.countByRoleAndEnabledTrue(AppUser.Role.USER);

        // Ti le tinh tren cac don DA KET THUC, khong tinh don dang chay: don moi tao
        // chua giao khong the coi la that bai, gop vao se lam ti le tut xuong vo ly.
        long returned = orderRepository.countByStatus(Order.OrderStatus.RETURNED);
        var successRate = vn.edu.hcmute.uteexpress.util.DeliverySuccessRate.calculate(deliveredOrders, returned);

        return new HomeStatistics(deliveredOrders, activeCustomers, ordersInTransit, successRate);
    }

    @Override
    public Map<Order.ServiceType, BigDecimal> estimateAllServices(double weightKg) {
        Map<Order.ServiceType, BigDecimal> bangCuoc = new LinkedHashMap<>();
        for (Order.ServiceType serviceType : Order.ServiceType.values()) {
            bangCuoc.put(serviceType, calculateFee(serviceType, weightKg));
        }
        return bangCuoc;
    }

    /** Cước chốt từ cấu hình Aiven lúc tạo đơn; không tính lại đơn cũ khi đổi giá. */
    private BigDecimal calculateFee(Order.ServiceType serviceType, double weightKg) {
        return tariffService.quote(serviceType, weightKg);
    }

    /** Sinh ma tracking dang UTE + 4 so cuoi timestamp + 4 so ngau nhien, du dung cho demo. */
    private String generateTrackingCode() {
        long timestampPart = System.currentTimeMillis() % 10_000;
        int randomPart = RANDOM.nextInt(10_000);
        return String.format("UTE%04d%04d", timestampPart, randomPart);
    }
}
