package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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

        // Ba trang thai ket thuc bat thuong: don dung lai giua duong, khong di het 4 moc.
        boolean cancelled = current == Order.OrderStatus.CANCELLED;
        boolean failed = current == Order.OrderStatus.FAILED;
        boolean returned = current == Order.OrderStatus.RETURNED;

        List<OrderTimelineStep> steps = new ArrayList<>();

        // Moc 1 luon da xong - co don nghia la da tao don.
        steps.add(new OrderTimelineStep("Chờ lấy hàng",
                "Đơn đã được tạo, đang chờ shipper tới lấy hàng.",
                true, current == Order.OrderStatus.PENDING_PICKUP, order.getCreatedAt()));

        if (cancelled) {
            // Don bi huy khi con dang cho lay hang nen khong co moc nao khac nua.
            steps.add(new OrderTimelineStep("Đã hủy",
                    "Đơn đã được hủy, shipper sẽ không tới lấy hàng.",
                    true, true, order.getUpdatedAt()));
            return steps;
        }

        int rank = statusRank(current);

        steps.add(new OrderTimelineStep("Đã lấy hàng",
                "Shipper đã nhận hàng từ người gửi.",
                rank >= 1, current == Order.OrderStatus.PICKED_UP, timeOf(order, current, Order.OrderStatus.PICKED_UP)));

        steps.add(new OrderTimelineStep("Đang giao",
                "Hàng đang trên đường tới người nhận.",
                rank >= 2, current == Order.OrderStatus.IN_TRANSIT, timeOf(order, current, Order.OrderStatus.IN_TRANSIT)));

        if (failed) {
            steps.add(new OrderTimelineStep("Giao thất bại",
                    "Không giao được cho người nhận. Shipper sẽ liên hệ để xử lý tiếp.",
                    true, true, order.getUpdatedAt()));
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
    public void cancelOrder(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này."));

        if (!order.getSender().getUsername().equals(username)) {
            throw new IllegalStateException("Bạn không có quyền huỷ đơn hàng này.");
        }
        if (order.getStatus() != Order.OrderStatus.PENDING_PICKUP) {
            throw new IllegalStateException("Chỉ huỷ được đơn khi shipper chưa tới lấy hàng.");
        }

        order.setStatus(Order.OrderStatus.CANCELLED);
        orderRepository.save(order);
    }

    @Override
    public BigDecimal estimateFee(Order.ServiceType serviceType, double weightKg) {
        return calculateFee(serviceType, weightKg);
    }

    @Override
    public Map<Order.ServiceType, BigDecimal> estimateAllServices(double weightKg) {
        Map<Order.ServiceType, BigDecimal> bangCuoc = new LinkedHashMap<>();
        for (Order.ServiceType serviceType : Order.ServiceType.values()) {
            bangCuoc.put(serviceType, calculateFee(serviceType, weightKg));
        }
        return bangCuoc;
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
