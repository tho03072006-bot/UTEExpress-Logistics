package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.time.*;
import org.springframework.data.domain.*;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.entity.OrderStatusHistory;
import vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsInput;

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
    private final OrderStatusHistoryRepository history;
    private final Clock clock;

    public TrackingServiceImpl(
            AppUserRepository appUserRepository,
            OrderRepository orderRepository,
            OrderPaymentService orderPaymentService,
            DeliveryProofService deliveryProofService,
            ApplicationEventPublisher eventPublisher,
            OrderStatusHistoryRepository history, Clock clock) {
        this.appUserRepository = appUserRepository;
        this.orderRepository = orderRepository;
        this.orderPaymentService = orderPaymentService;
        this.deliveryProofService = deliveryProofService;
        this.eventPublisher = eventPublisher;
        this.history = history;
        this.clock = clock;
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
    public Page<Order> searchAssignedOrders(String username, String keyword, Order.OrderStatus status,
            LocalDate from, LocalDate to, int page) {
        AppUser shipper = findShipper(username);
        String term = LogisticsInput.text(keyword, 0, 100, "Từ khóa");
        if (from != null && to != null && from.isAfter(to))
            throw new IllegalArgumentException("Từ ngày không được sau đến ngày.");
        for (LocalDate date : new LocalDate[]{from, to}) {
            if (date != null && (date.getYear() < 2000 || date.getYear() > 2100))
                throw new IllegalArgumentException("Ngày lọc phải nằm trong năm 2000–2100.");
        }
        String escaped = term.isEmpty() ? null : term.replace("!", "!!").replace("%", "!%").replace("_", "!_");
        var request = PageRequest.of(LogisticsInput.page(page), 10, Sort.by("createdAt", "id").descending());
        var result = orderRepository.searchOrdersOfShipper(shipper, status, escaped,
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(), request);
        if (result.getTotalPages() > 0 && page >= result.getTotalPages()) {
            result = orderRepository.searchOrdersOfShipper(shipper, status, escaped,
                    from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(),
                    request.withPage(result.getTotalPages() - 1));
        }
        return result;
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
    public Optional<Order> findAssignedOrderByCode(String code, String username) {
        return orderRepository.findByTrackingCodeAndShipper(code, findShipper(username));
    }

    @Override
    @Transactional
    public Order updateStatus(
            Long orderId,
            String shipperUsername,
            Order.OrderStatus newStatus) {
        return updateStatus(orderId, shipperUsername, newStatus, null, "", null);
    }

    @Override
    @Transactional
    public Order updateStatus(Long orderId, String shipperUsername, Order.OrderStatus newStatus,
            DeliveryFailureReason failureReason, String note, LocalDateTime nextAttemptAt) {
        AppUser shipper = findShipper(shipperUsername);

        Order order = orderRepository
                .findLockedByIdAndShipper(orderId, shipper)
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

        LocalDateTime now = LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
        String normalizedNote = LogisticsInput.text(note, 0, 500, "Ghi chú");
        if (newStatus == Order.OrderStatus.FAILED && failureReason == null)
            throw new IllegalArgumentException("Hãy chọn lý do giao thất bại.");
        if (failureReason == DeliveryFailureReason.OTHER && normalizedNote.length() < 5)
            throw new IllegalArgumentException("Lý do khác cần ghi chú ít nhất 5 ký tự.");
        if (nextAttemptAt != null && (newStatus != Order.OrderStatus.FAILED
                || !nextAttemptAt.isAfter(now) || nextAttemptAt.isAfter(now.plusDays(30))))
            throw new IllegalArgumentException("Hẹn giao lại phải sau hiện tại và trong 30 ngày, khi báo giao thất bại.");
        if (order.getStatus() == Order.OrderStatus.FAILED && newStatus == Order.OrderStatus.IN_TRANSIT && !canRetry(order))
            throw new IllegalStateException("Đơn đã giao thất bại 3 lần; cần hoàn trả hoặc trao đổi với Manager.");
        if (newStatus == Order.OrderStatus.DELIVERED && !hasCurrentProof(order)) {
            throw new IllegalStateException(
                    "Phải lưu ảnh bằng chứng và chữ ký người nhận "
                            + "trước khi xác nhận giao thành công.");
        }

        Order.OrderStatus previousStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(now);
        Order savedOrder = orderRepository.save(order);

        if (newStatus == Order.OrderStatus.DELIVERED) {
            markCodCollectedIfNecessary(savedOrder);
        }

        history.save(new OrderStatusHistory(savedOrder, shipper, previousStatus, newStatus,
                newStatus == Order.OrderStatus.FAILED ? failureReason : null,
                normalizedNote, nextAttemptAt, now));

        publishStatusChanged(savedOrder);

        return savedOrder;
    }

    @Override
    public boolean canRetry(Order order) {
        return order.getStatus() == Order.OrderStatus.FAILED
                && history.countByOrderAndStatus(order, Order.OrderStatus.FAILED) < 3;
    }

    @Override
    public boolean hasCurrentProof(Order order) {
        if (!deliveryProofService.hasCompleteProof(order)) return false;
        var retry = history.findFirstByOrderAndPreviousStatusAndStatusOrderByOccurredAtDescIdDesc(
                order, Order.OrderStatus.FAILED, Order.OrderStatus.IN_TRANSIT);
        return retry.isEmpty() || deliveryProofService.findByOrder(order)
                .filter(proof -> proof.getUpdatedAt() != null
                        && !proof.getUpdatedAt().isBefore(retry.get().getOccurredAt())).isPresent();
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
                    newStatus == Order.OrderStatus.RETURNED || newStatus == Order.OrderStatus.IN_TRANSIT;
            default -> false;
        };
    }

    private AppUser findShipper(String shipperUsername) {
        AppUser shipper = appUserRepository
                .findByUsername(shipperUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản Shipper."));

        if (shipper.getRole() != AppUser.Role.SHIPPER || !shipper.isEnabled()) {
            throw new IllegalStateException(
                    "Tài khoản hiện tại không có vai trò SHIPPER.");
        }

        return shipper;
    }
}
