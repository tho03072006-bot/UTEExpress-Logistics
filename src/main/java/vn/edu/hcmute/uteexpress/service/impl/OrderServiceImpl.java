package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderService;

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

    public OrderServiceImpl(OrderRepository orderRepository, AppUserRepository appUserRepository) {
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
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
        order.setShippingFee(calculateFee(request.getServiceType(), request.getWeightKg()));
        order.setStatus(Order.OrderStatus.PENDING_PICKUP);

        return orderRepository.save(order);
    }

    @Override
    public List<Order> findOrdersOfUser(String username) {
        AppUser sender = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Khong tim thay nguoi dung: " + username));
        return orderRepository.findBySender(sender);
    }

    @Override
    public Optional<Order> findByTrackingCode(String trackingCode) {
        return orderRepository.findByTrackingCode(trackingCode);
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
