package vn.edu.hcmute.uteexpress.service.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.service.*;

/** Transaction ghi ngắn, dùng chung cho tạo đơn trực tiếp và xác nhận giỏ; không gọi mạng. */
@Service
@Transactional
public class OrderCreationWriter {
    private final OrderRepository orderRepository;
    private final AppUserRepository users;
    private final OrderPaymentService orderPaymentService;
    private final PromoCodeService promoCodeService;
    private final OrderStatusHistoryRepository history;
    private final Clock clock;
    private final Validator validator;
    private final OrderSubmissionGuard submissions;
    private final OrderPricingService pricingService;
    private final EntityManager em;
    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    public OrderCreationWriter(OrderRepository orderRepository, AppUserRepository users, OrderPaymentService orderPaymentService,
            PromoCodeService promoCodeService, OrderStatusHistoryRepository history, Clock clock, Validator validator,
            OrderSubmissionGuard submissions, OrderPricingService pricingService, EntityManager em) {
        this.orderRepository=orderRepository; this.users=users; this.orderPaymentService=orderPaymentService;
        this.promoCodeService=promoCodeService; this.history=history; this.clock=clock; this.validator=validator;
        this.submissions=submissions; this.pricingService=pricingService; this.em=em;
    }

    public Order findExisting(OrderCreateRequest request, String username) {
        validate(request);
        return existing(request, requireSender(username));
    }
    private void validate(OrderCreateRequest request) {
        if (request == null) throw new IllegalArgumentException("Thiếu dữ liệu đơn hàng.");
        var errors=validator.validate(request);
        if (!errors.isEmpty()) throw new IllegalArgumentException(errors.iterator().next().getMessage());
    }
    private AppUser requireSender(String username) {
        var sender=users.findLockedByUsername(username).orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản."));
        // OSIV có thể đã đọc tài khoản trước khi tra bản đồ: luôn làm mới sau khi khóa.
        em.refresh(sender, LockModeType.PESSIMISTIC_WRITE);
        if (!sender.isEnabled() || sender.isLockedByAdmin() || sender.getRole()!=AppUser.Role.USER)
            throw new org.springframework.security.access.AccessDeniedException("Chỉ người gửi đang hoạt động được tạo đơn.");
        return sender;
    }
    private Order existing(OrderCreateRequest request, AppUser sender) {
        if (request.getSubmissionToken()==null) return null; // lời gọi nội bộ cũ
        var submission=submissions.findExisting(request, sender, OrderFormSubmission.Purpose.DIRECT);
        var existing=orderRepository.findByCreationToken(request.getSubmissionToken());
        if (existing.isPresent()) {
            var order=existing.get();
            if (!order.getSender().getId().equals(sender.getId()) || !OrderSubmissionGuard.fingerprint(request).equals(order.getCreationHash()))
                throw OrderSubmissionGuard.reused();
            return order;
        }
        if (submission.isPresent()) throw OrderSubmissionGuard.reused();
        return null;
    }

    public Order createResolved(OrderCreateRequest request, String senderUsername, OrderPricingService.ResolvedRoute route) {
        validate(request);
        AppUser sender = requireSender(senderUsername);
        Order existing = existing(request, sender);
        if (existing != null) return existing;
        String requestHash = OrderSubmissionGuard.fingerprint(request);
        var submission = request.getSubmissionToken() == null ? null
                : submissions.reserve(request, sender, OrderFormSubmission.Purpose.DIRECT);

        Order order = new Order();
        order.setCreationToken(request.getSubmissionToken()); order.setCreationHash(requestHash);
        order.setTrackingCode(generateTrackingCode());
        order.setSender(sender);
        order.setSenderName(request.getSenderName());
        order.setSenderAddress(request.getSenderAddress());
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        order.setWeightKg(request.getWeightKg());
        order.setServiceType(request.getServiceType());
        // Cước = cước dịch vụ + phụ phí khoảng cách đường chim bay; địa chỉ không xác định được thì báo lại trên form.
        var pricing = pricingService.priceResolved(route, request.getSenderAddress(), request.getReceiverAddress(),
                request.getServiceType(), request.getWeightKg());
        BigDecimal fee = pricing.total();
        order.setShippingFee(fee);
        order.setDistanceKm(pricing.distanceKm());
        order.setDistanceFee(pricing.distanceFee());
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

    private String generateTrackingCode() {
        return String.format("UTE%04d%04d", System.currentTimeMillis() % 10_000, RANDOM.nextInt(10_000));
    }
}
