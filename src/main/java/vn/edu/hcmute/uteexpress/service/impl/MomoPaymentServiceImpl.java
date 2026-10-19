package vn.edu.hcmute.uteexpress.service.impl;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import jakarta.persistence.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.payment.*;
import vn.edu.hcmute.uteexpress.service.payment.*;

@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class MomoPaymentServiceImpl implements MomoPaymentService {
    private final MomoPaymentRepository payments;
    private final MomoOrderRepository orders;
    private final MomoTicketCodec tickets;
    private final MomoSettings settings;
    private final ApplicationEventPublisher events;
    @PersistenceContext private EntityManager entityManager;

    public MomoPaymentServiceImpl(MomoPaymentRepository payments, MomoOrderRepository orders,
            MomoTicketCodec tickets, MomoSettings settings, ApplicationEventPublisher events) {
        this.payments = payments; this.orders = orders; this.tickets = tickets; this.settings = settings; this.events = events;
    }

    @Override @Transactional(readOnly = true)
    public Checkout createCheckout(long orderId, String username) {
        OrderPayment payment = owned(orderId, username); WalletPaymentRules.validate(payment);
        validateSnapshot(payment);
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) throw new IllegalStateException("Đơn đã thanh toán, không cần tạo QR.");
        Instant now = Instant.now();
        String url = settings.baseUrl() + "/momo/thanh-toan?t=" + tickets.issue(payment, now);
        return new Checkout(url, MomoQrEncoder.encode(url), now.plus(MomoTicketCodec.LIFETIME).getEpochSecond());
    }

    @Override @Transactional(readOnly = true)
    public Status status(long orderId, String username) {
        var payment = owned(orderId, username);
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) {
            requireReceipt(payment);
            return new Status("PAID", "Đã xác nhận thanh toán từ database Aiven.", payment.getTransactionRef(), text(payment.getPaidAt()));
        }
        if (WalletPaymentRules.isClosed(payment.getOrder())) return new Status("CLOSED", "Đơn đã kết thúc, không thể thanh toán.", null, null);
        if (payment.getMethod() != OrderPayment.PaymentMethod.MOMO) return new Status("UNAVAILABLE", "Phương thức thanh toán đã thay đổi.", null, null);
        WalletPaymentRules.validate(payment);
        return new Status("UNPAID", "Đang chờ xác nhận trên trang MoMo mô phỏng.", null, null);
    }

    @Override @Transactional(readOnly = true)
    public Receipt view(String token) {
        var ticket = tickets.verify(token, Instant.now());
        return receipt(bound(ticket), ticket.expiresAt());
    }

    @Override
    public Receipt confirm(String token) {
        var ticket = tickets.verify(token, Instant.now());
        // Cùng khóa Order với hủy/điều phối ở web local, rồi mới đọc bản ghi thanh toán.
        var order = orders.lock(ticket.orderId()).orElseThrow(MomoPaymentServiceImpl::unavailable);
        entityManager.refresh(order);
        entityManager.refresh(order.getSender(), LockModeType.PESSIMISTIC_READ);
        tickets.verify(token, Instant.now()); // Có thể đã chờ khóa đến khi QR hết hạn.
        var payment = bound(ticket);
        entityManager.refresh(payment);
        payment = bound(ticket);
        // Xác nhận lặp lại trả đúng biên nhận đã có, không tạo giao dịch thứ hai.
        if (payment.getStatus() != OrderPayment.PaymentStatus.PAID) {
            WalletPaymentRules.markPaid(payment, Instant.now());
            events.publishEvent(new OrderStatusChangedEvent(order.getId(), order.getTrackingCode(),
                    order.getStatus().name(), payment.getUpdatedAt()));
        }
        return receipt(payment, ticket.expiresAt());
    }

    @Override public void switchLegacyPayment(long orderId, String username) {
        if (orderId <= 0) throw unavailable();
        orders.lock(orderId).orElseThrow(MomoPaymentServiceImpl::unavailable);
        var payment = owned(orderId, username);
        if (payment.getMethod() != OrderPayment.PaymentMethod.VNPAY
                || payment.getStatus() != OrderPayment.PaymentStatus.UNPAID || WalletPaymentRules.isClosed(payment.getOrder()))
            throw new IllegalStateException("Chỉ đổi được đơn VNPay cũ chưa thanh toán và chưa kết thúc.");
        payment.setMethod(OrderPayment.PaymentMethod.MOMO);
        payment.setUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    private OrderPayment owned(long orderId, String username) {
        if (orderId <= 0 || username == null || username.isBlank()) throw unavailable();
        var payment = payments.find(orderId).orElseThrow(MomoPaymentServiceImpl::unavailable);
        var user = payment.getOrder().getSender(); requireActive(user);
        if (!username.equals(user.getUsername())) throw unavailable();
        return payment;
    }

    private OrderPayment bound(MomoTicketCodec.Ticket ticket) {
        var payment = payments.find(ticket.orderId()).orElseThrow(MomoPaymentServiceImpl::unavailable);
        var user = payment.getOrder().getSender(); requireActive(user);
        if (payment.getId() != ticket.paymentId() || user.getId() != ticket.ownerId()
                || user.getAccessVersion() != ticket.accessVersion() || payment.getAmount() == null
                || payment.getAmount().compareTo(BigDecimal.valueOf(ticket.amount())) != 0
                || !MomoTicketCodec.digest(user.getPassword()).equals(ticket.passwordDigest())
                || payment.getMethod() != OrderPayment.PaymentMethod.MOMO) throw unavailable();
        // Cho phép xem lại biên nhận PAID kể cả đơn sau đó đã giao xong.
        if (payment.getStatus() != OrderPayment.PaymentStatus.PAID) WalletPaymentRules.validate(payment);
        validateSnapshot(payment);
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) requireReceipt(payment);
        return payment;
    }

    private static void requireActive(AppUser user) {
        if (user == null || user.getRole() != AppUser.Role.USER || !user.isEnabled() || user.isLockedByAdmin()) throw unavailable();
    }
    private static IllegalArgumentException unavailable() { return new IllegalArgumentException("Không thể truy cập thanh toán này. Hãy mở lại từ tài khoản sở hữu đơn hàng."); }
    private static String text(Object value) { return value == null ? "" : value.toString(); }
    private static void validateSnapshot(OrderPayment payment) {
        var fee = payment.getOrder().getShippingFee();
        var discount = payment.getDiscountAmount() == null ? BigDecimal.ZERO : payment.getDiscountAmount();
        if (!vn.edu.hcmute.uteexpress.util.PaymentReadiness.isValidAmount(fee)
                || !vn.edu.hcmute.uteexpress.util.PaymentReadiness.isValidAmount(discount)
                || discount.compareTo(fee) > 0 || payment.getAmount() == null
                || fee.subtract(discount).compareTo(payment.getAmount()) != 0)
            throw new IllegalStateException("Cước và giảm giá không khớp dữ liệu thanh toán. Hãy liên hệ Manager để kiểm tra.");
    }
    private static void requireReceipt(OrderPayment payment) {
        if (payment.getMethod() == OrderPayment.PaymentMethod.MOMO
                && (payment.getPaidAt() == null || payment.getTransactionRef() == null || payment.getTransactionRef().isBlank()))
            throw new IllegalStateException("Thiếu biên nhận thanh toán. Hãy liên hệ Manager để kiểm tra.");
    }
    private Receipt receipt(OrderPayment payment, long expiresAt) {
        var order = payment.getOrder();
        String phone = order.getReceiverPhone();
        phone = phone == null || phone.length() < 4 ? "—" : "******" + phone.substring(phone.length() - 4);
        return new Receipt(order.getTrackingCode(), order.getSenderName(), order.getReceiverName(), phone,
                order.getServiceType() == null ? "—" : order.getServiceType().getDisplayName(), order.getWeightKg(),
                text(order.getShippingFee()), text(payment.getDiscountAmount() == null ? BigDecimal.ZERO : payment.getDiscountAmount()),
                text(payment.getAmount()), payment.getStatus() == OrderPayment.PaymentStatus.PAID,
                payment.getTransactionRef(), text(payment.getPaidAt()), expiresAt);
    }
}
