package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
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
import java.util.List;
import java.util.Optional;

@Service
public class OrderServiceImpl implements OrderService {

    // TODO (TV3 - Manager): thay bang gia cung nay bang bang gia cuoc cau hinh
    // duoc trong CSDL (theo khu vuc/khoi luong) o muc "Manager - quan ly bang gia cuoc".
    private static final BigDecimal BASE_FEE_STANDARD = BigDecimal.valueOf(15000);
    private static final BigDecimal BASE_FEE_EXPRESS = BigDecimal.valueOf(25000);
    private static final BigDecimal BASE_FEE_SUPER_EXPRESS = BigDecimal.valueOf(40000);
    private static final BigDecimal FEE_PER_KG = BigDecimal.valueOf(3000);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OrderRepository orderRepository;
    private final AppUserRepository appUserRepository;
    private final SavedAddressService savedAddressService;
    private final OrderPaymentService orderPaymentService;
    private final PromoCodeService promoCodeService;

    public OrderServiceImpl(OrderRepository orderRepository, AppUserRepository appUserRepository,
                            SavedAddressService savedAddressService,
                            OrderPaymentService orderPaymentService,
                            PromoCodeService promoCodeService) {
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
        this.savedAddressService = savedAddressService;
        this.orderPaymentService = orderPaymentService;
        this.promoCodeService = promoCodeService;
    }

    @Override
    public OrderCreateRequest prepareCreateForm(String username) {
        OrderCreateRequest form = new OrderCreateRequest();
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
    public Order createOrder(OrderCreateRequest request, String senderUsername) {
        AppUser sender = appUserRepository.findByUsername(senderUsername)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung: " + senderUsername));

        Order order = new Order();
        order.setTrackingCode(generateTrackingCode());
        order.setSender(sender);
        order.setSenderName(request.getSenderName());
        order.setSenderAddress(request.getSenderAddress());
        order.setReceiverName(request.getReceiverName());
        order.setReceiverPhone(request.getReceiverPhone());
        order.setReceiverAddress(request.getReceiverAddress());
        order.setWeightKg(request.getWeightKg());
        order.setServiceType(request.getServiceType());
        BigDecimal fee = calculateFee(request.getServiceType(), request.getWeightKg());
        order.setShippingFee(fee);
        order.setStatus(Order.OrderStatus.PENDING_PICKUP);

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
    public Optional<Order> findByTrackingCode(String trackingCode) {
        return orderRepository.findByTrackingCode(trackingCode);
    }

    @Override
    public void cancelOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay don hang: " + orderId));

        if (!order.getSender().getUsername().equals(username)) {
            throw new IllegalStateException("Ban khong co quyen huy don hang nay");
        }
        if (order.getStatus() != Order.OrderStatus.PENDING_PICKUP) {
            throw new IllegalStateException("Chi co the huy don khi con dang cho lay hang (PENDING_PICKUP)");
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Override
    public BigDecimal estimateFee(Order.ServiceType serviceType, double weightKg) {
        return calculateFee(serviceType, weightKg);
    }

    /**
     * Cong thuc tinh cuoc demo don gian: phi co ban theo loai dich vu + phi theo khoi luong.
     * TODO (TV3): thay bang bang gia that theo khu vuc khi lam xong Manager.
     */
    private BigDecimal calculateFee(Order.ServiceType serviceType, double weightKg) {
        BigDecimal baseFee = switch (serviceType) {
            case STANDARD -> BASE_FEE_STANDARD;
            case EXPRESS -> BASE_FEE_EXPRESS;
            case SUPER_EXPRESS -> BASE_FEE_SUPER_EXPRESS;
        };
        BigDecimal weightFee = FEE_PER_KG.multiply(BigDecimal.valueOf(weightKg));
        return baseFee.add(weightFee);
    }

    /** Sinh ma tracking dang UTE + 4 so cuoi timestamp + 4 so ngau nhien, du dung cho demo. */
    private String generateTrackingCode() {
        long timestampPart = System.currentTimeMillis() % 10_000;
        int randomPart = RANDOM.nextInt(10_000);
        return String.format("UTE%04d%04d", timestampPart, randomPart);
    }
}
