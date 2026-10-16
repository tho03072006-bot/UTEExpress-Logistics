package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.time.*;
import org.springframework.data.domain.*;
import vn.edu.hcmute.uteexpress.dto.tracking.DeliveryFailureReason;
import vn.edu.hcmute.uteexpress.entity.OrderStatusHistory;
import vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsAccess;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsInput;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent;
import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusText;
import vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent;
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
    private final vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService operations;

    public TrackingServiceImpl(
            AppUserRepository appUserRepository,
            OrderRepository orderRepository,
            OrderPaymentService orderPaymentService,
            DeliveryProofService deliveryProofService,
            ApplicationEventPublisher eventPublisher,
            OrderStatusHistoryRepository history, Clock clock,
            vn.edu.hcmute.uteexpress.service.manager.OperationsCaseService operations) {
        this.appUserRepository = appUserRepository;
        this.orderRepository = orderRepository;
        this.orderPaymentService = orderPaymentService;
        this.deliveryProofService = deliveryProofService;
        this.eventPublisher = eventPublisher;
        this.history = history;
        this.clock = clock;
        this.operations = operations;
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
        String escaped = LogisticsInput.workFilter(keyword, from, to, page);
        var request = PageRequest.of(page, 10, Sort.by("createdAt", "id").descending());
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
                            + OrderStatusText.of(order.getStatus())
                            + " sang "
                            + OrderStatusText.of(newStatus) + ".");
        }

        if (newStatus == Order.OrderStatus.PICKED_UP || newStatus == Order.OrderStatus.IN_TRANSIT
                || newStatus == Order.OrderStatus.DELIVERED) {
            orderPaymentService.requireReadyForFulfillment(order);
        }

        LocalDateTime now = now();
        String normalizedNote = LogisticsInput.text(note, 0, 500, "Ghi chú");
        if (newStatus == Order.OrderStatus.FAILED && failureReason == null)
            throw new IllegalArgumentException("Hãy chọn lý do giao thất bại.");
        if (failureReason == DeliveryFailureReason.OTHER && normalizedNote.length() < 5)
            throw new IllegalArgumentException("Lý do khác cần ghi chú ít nhất 5 ký tự.");
        if (nextAttemptAt != null && (newStatus != Order.OrderStatus.FAILED
                || !nextAttemptAt.isAfter(now) || nextAttemptAt.isAfter(now.plusDays(30))))
            throw new IllegalArgumentException("Hẹn giao lại phải sau hiện tại và trong 30 ngày, khi báo giao thất bại.");
        if (order.getStatus() == Order.OrderStatus.FAILED && newStatus == Order.OrderStatus.IN_TRANSIT && !canRetry(order))
            throw new IllegalStateException("Chưa đủ điều kiện giao lại: kiểm tra lịch hẹn, giới hạn số lần và hồ sơ duyệt với Manager.");
        if (newStatus == Order.OrderStatus.DELIVERED && !hasCurrentProof(order)) {
            throw new IllegalStateException(
                    "Phải lưu ảnh bằng chứng và chữ ký người nhận "
                            + "trước khi xác nhận giao thành công.");
        }

        Order.OrderStatus previousStatus = order.getStatus();
        operations.beforeStatusChange(order, shipperUsername, newStatus);
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
        switch (newStatus) {
            case DELIVERED -> publishSenderUpdate(savedOrder, SenderOrderUpdateEvent.Kind.DELIVERED, null, null);
            case FAILED -> publishSenderUpdate(savedOrder, SenderOrderUpdateEvent.Kind.DELIVERY_FAILED,
                    failureReason.getLabel(), nextAttemptAt);
            case RETURNED -> publishSenderUpdate(savedOrder, SenderOrderUpdateEvent.Kind.RETURNED, null, null);
            default -> { }
        }

        return savedOrder;
    }

    @Override
    @Transactional
    public OrderStatusHistory reportPickupFailure(Long orderId, String shipperUsername,
            DeliveryFailureReason reason, String note, LocalDateTime nextAttemptAt) {
        AppUser shipper = findShipper(shipperUsername);
        Order order = orderRepository.findLockedByIdAndShipper(orderId, shipper)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hoặc đơn không được phân công cho bạn."));
        if (order.getStatus() != Order.OrderStatus.PENDING_PICKUP)
            throw new IllegalStateException("Chỉ ghi nhận lấy hàng thất bại khi đơn đang chờ lấy hàng.");
        LocalDateTime now = now();
        String normalizedNote = LogisticsInput.text(note, 0, 500, "Ghi chú");
        if (reason == null) throw new IllegalArgumentException("Hãy chọn lý do lấy hàng thất bại.");
        if (reason == DeliveryFailureReason.OTHER && normalizedNote.length() < 5)
            throw new IllegalArgumentException("Lý do khác cần ghi chú ít nhất 5 ký tự.");
        if (nextAttemptAt != null && (!nextAttemptAt.isAfter(now) || nextAttemptAt.isAfter(now.plusDays(30))))
            throw new IllegalArgumentException("Hẹn lấy hàng lại phải sau hiện tại và trong 30 ngày tới.");
        if (history.countPickupFailures(order) >= MAX_PICKUP_FAILURES)
            throw new IllegalStateException("Đơn đã " + MAX_PICKUP_FAILURES
                    + " lần lấy hàng không thành công; hãy trao đổi với Manager để hủy hoặc phân công lại.");
        var latest = history.latestPickupFailures(List.of(order.getId()));
        if (!latest.isEmpty() && latest.get(0).getOccurredAt().isAfter(now.minusMinutes(1)))
            throw new IllegalStateException("Bạn vừa ghi nhận lấy hàng thất bại cho đơn này. Hãy đợi 1 phút rồi thử lại.");
        var entry = history.save(new OrderStatusHistory(order, shipper, Order.OrderStatus.PENDING_PICKUP,
                Order.OrderStatus.PENDING_PICKUP, reason, normalizedNote, nextAttemptAt, now));
        publishSenderUpdate(order, SenderOrderUpdateEvent.Kind.PICKUP_FAILED, reason.getPickupLabel(), nextAttemptAt);
        return entry;
    }

    @Override
    public long pickupFailures(Order order) {
        return history.countPickupFailures(order);
    }

    @Override
    public boolean canReportPickupFailure(Order order) {
        return order.getStatus() == Order.OrderStatus.PENDING_PICKUP
                && history.countPickupFailures(order) < MAX_PICKUP_FAILURES;
    }

    @Override
    public boolean canRetry(Order order) {
        return operations.canRetry(order);
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

    private void publishSenderUpdate(Order order, SenderOrderUpdateEvent.Kind kind, String reason, LocalDateTime next) {
        AppUser sender = order.getSender();
        if (sender == null) return;
        String name = sender.getFullName() == null || sender.getFullName().isBlank() ? order.getSenderName() : sender.getFullName();
        eventPublisher.publishEvent(new SenderOrderUpdateEvent(sender.getEmail(), name, order.getTrackingCode(), kind, reason, next));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock.withZone(ZoneId.of("Asia/Ho_Chi_Minh")));
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
        AppUser shipper = LogisticsAccess
                .findUser(appUserRepository, shipperUsername)
                .orElseThrow(() -> new IllegalStateException(
                        "Không tìm thấy tài khoản Shipper."));

        if (shipper.getRole() != AppUser.Role.SHIPPER || !shipper.isEnabled() || shipper.isLockedByAdmin()) {
            throw new IllegalStateException(
                    "Tài khoản hiện tại không có vai trò SHIPPER.");
        }

        return shipper;
    }
}
