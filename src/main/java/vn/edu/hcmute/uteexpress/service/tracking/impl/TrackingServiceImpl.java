package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofService;
import vn.edu.hcmute.uteexpress.service.tracking.TrackingService;

@Service
@Transactional(readOnly = true)
public class TrackingServiceImpl implements TrackingService {

    private final AppUserRepository appUserRepository;
    private final OrderRepository orderRepository;
    private final OrderPaymentService orderPaymentService;
    private final DeliveryProofService deliveryProofService;
    private final ApplicationEventPublisher eventPublisher;

    public TrackingServiceImpl(
            AppUserRepository appUserRepository,
            OrderRepository orderRepository,
            OrderPaymentService orderPaymentService,
            DeliveryProofService deliveryProofService,
            ApplicationEventPublisher eventPublisher) {
        this.appUserRepository = appUserRepository;
        this.orderRepository = orderRepository;
        this.orderPaymentService = orderPaymentService;
        this.deliveryProofService = deliveryProofService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public List<Order> findAssignedOrders(String shipperUsername) {
        AppUser shipper = findShipper(shipperUsername);

        return orderRepository.findByShipper(shipper)
                .stream()
                .sorted(Comparator.comparing(
                        Order::getCreatedAt).reversed())
                .toList();
    }

    @Override
    public Optional<Order> findAssignedOrder(
            Long orderId,
            String shipperUsername) {
        AppUser shipper = findShipper(shipperUsername);

        return orderRepository.findByIdAndShipper(
                orderId, shipper);
    }

    @Override
    @Transactional
    public Order updateStatus(
            Long orderId,
            String shipperUsername,
            Order.OrderStatus newStatus) {
        AppUser shipper = findShipper(shipperUsername);

        Order order = orderRepository
                .findByIdAndShipper(orderId, shipper)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy đơn hoặc đơn không được "
                                + "phân công cho bạn."));

        if (!isValidTransition(order.getStatus(), newStatus)) {
            throw new IllegalStateException(
                    "Không thể chuyển trạng thái từ "
                            + order.getStatus()
                            + " sang "
                            + newStatus);
        }

        if (newStatus == Order.OrderStatus.DELIVERED
                && !deliveryProofService.hasCompleteProof(order)) {
            throw new IllegalStateException(
                    "Phải lưu ảnh bằng chứng và chữ ký người nhận "
                            + "trước khi xác nhận giao thành công.");
        }

        order.setStatus(newStatus);
        Order savedOrder = orderRepository.save(order);

        if (newStatus == Order.OrderStatus.DELIVERED) {
            markCodCollectedIfNecessary(savedOrder);
        }

        publishStatusChanged(savedOrder);

        return savedOrder;
    }

    private void publishStatusChanged(Order order) {
        eventPublisher.publishEvent(
                new OrderStatusChangedEvent(
                        order.getId(),
                        order.getTrackingCode(),
                        order.getStatus().name(),
                        order.getUpdatedAt()));
    }

    private void markCodCollectedIfNecessary(Order order) {
        Optional<OrderPayment> payment =
                orderPaymentService.findByOrder(order);

        if (payment.isPresent()
                && payment.get().getMethod()
                        == OrderPayment.PaymentMethod.COD) {
            orderPaymentService.markCodCollected(order.getId());
        }
    }

    private boolean isValidTransition(
            Order.OrderStatus currentStatus,
            Order.OrderStatus newStatus) {
        if (currentStatus == null || newStatus == null) {
            return false;
        }

        return switch (currentStatus) {
            case PENDING_PICKUP ->
                    newStatus == Order.OrderStatus.PICKED_UP;
            case PICKED_UP ->
                    newStatus == Order.OrderStatus.IN_TRANSIT;
            case IN_TRANSIT ->
                    newStatus == Order.OrderStatus.DELIVERED
                            || newStatus == Order.OrderStatus.FAILED;
            case FAILED ->
                    newStatus == Order.OrderStatus.RETURNED;
            default -> false;
        };
    }

    private AppUser findShipper(String shipperUsername) {
        AppUser shipper = appUserRepository
                .findByUsername(shipperUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản Shipper."));

        if (shipper.getRole() != AppUser.Role.SHIPPER) {
            throw new IllegalStateException(
                    "Tài khoản hiện tại không có vai trò SHIPPER.");
        }

        return shipper;
    }
}
