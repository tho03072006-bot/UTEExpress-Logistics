package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.entity.PromoCode;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderPaymentRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.dto.MomoPaymentAttempt;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;

@Service
@Transactional
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderRepository orderRepository;
    private final AppUserRepository appUserRepository;

    public OrderPaymentServiceImpl(OrderPaymentRepository orderPaymentRepository,
                                   OrderRepository orderRepository,
                                   AppUserRepository appUserRepository) {
        this.orderPaymentRepository = orderPaymentRepository;
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
    }

    @Override
    public OrderPayment createForOrder(Order order, OrderPayment.PaymentMethod method,
                                       PromoCode promoCode, BigDecimal discountAmount) {
        BigDecimal fee = order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO;
        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        if (fee.signum() < 0 || discount.signum() < 0 || discount.compareTo(fee) > 0
                || fee.stripTrailingZeros().scale() > 0 || discount.stripTrailingZeros().scale() > 0) {
            throw new IllegalStateException("Cước và giảm giá phải là số đồng hợp lệ.");
        }
        if (method == OrderPayment.PaymentMethod.VNPAY) {
            throw new IllegalStateException("VNPay chưa được tích hợp. Vui lòng chọn COD hoặc MoMo TEST.");
        }

        OrderPayment payment = new OrderPayment();
        payment.setOrder(order);
        payment.setMethod(method != null ? method : OrderPayment.PaymentMethod.COD);
        payment.setPromoCode(promoCode);
        payment.setDiscountAmount(discount);
        // Cuoc goc van nam nguyen o Order.shippingFee; o day la so tien khach thuc tra.
        payment.setAmount(fee.subtract(discount).max(BigDecimal.ZERO));
        // Moi don deu bat dau o trang thai chua thanh toan: COD thi thu luc giao hang,
        // MoMo chi duoc ghi PAID sau khi server xac minh voi cong thanh toan TEST.
        payment.setStatus(OrderPayment.PaymentStatus.UNPAID);
        if (payment.getAmount().signum() == 0) {
            // Server-calculated full fee waiver; no gateway request and no client confirmation.
            payment.setStatus(OrderPayment.PaymentStatus.PAID);
            payment.setPaidAt(LocalDateTime.now());
            payment.setTransactionRef("WAIVED-" + order.getId());
        } else if (payment.getMethod() == OrderPayment.PaymentMethod.MOMO) {
            gatewayAmount(payment);
        }
        return orderPaymentRepository.save(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderPayment> findByOrder(Order order) {
        return orderPaymentRepository.findByOrder(order);
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnpaidOfUser(String username) {
        return orderPaymentRepository.countByOrderSenderAndStatus(
                requireUser(username), OrderPayment.PaymentStatus.UNPAID);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<Long, OrderPayment> findPaymentsOfUser(String username) {
        AppUser owner = requireUser(username);
        Map<Long, OrderPayment> byOrderId = new HashMap<>();
        for (OrderPayment payment : orderPaymentRepository.findByOrderSender(owner)) {
            byOrderId.put(payment.getOrder().getId(), payment);
        }
        return byOrderId;
    }

    @Override
    @Transactional(readOnly = true)
    public OrderPayment findOwnedPayment(Long orderId, String username) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này"));
        requireOwner(order, username);
        return orderPaymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("Đơn hàng này chưa có thông tin thanh toán"));
    }

    @Override
    public OrderPayment confirmWalletPayment(Long orderId, String username) {
        OrderPayment payment = findOwnedPayment(orderId, username);

        if (!payment.getMethod().isOnlineWallet()) {
            throw new IllegalStateException(
                    "Đơn này thanh toán khi nhận hàng (COD), không cần trả trước qua ví.");
        }
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) {
            throw new IllegalStateException("Đơn này đã được thanh toán rồi.");
        }
        // Đơn đã dừng hẳn thì không bao giờ được giao nữa, thu tiền lúc này là
        // lấy tiền của khách cho một dịch vụ không còn thực hiện.
        if (isOrderClosed(payment.getOrder())) {
            throw new IllegalStateException(
                    "Đơn này đã kết thúc nên không thanh toán được nữa.");
        }

        throw new IllegalStateException("Cần xác minh kết quả từ cổng MoMo. Nút xác nhận không thể tự ghi đã thanh toán.");
    }

    @Override
    public MomoPaymentAttempt reserveMomoAttempt(Long orderId, String username, boolean qr) {
        Order order = lockedOrder(orderId);
        requireOwner(order, username);
        OrderPayment payment = lockedPayment(order);
        requireMomo(payment);
        if (isOrderClosed(order)) throw new IllegalStateException("Đơn này đã kết thúc nên không thanh toán được nữa.");
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) {
            throw new IllegalStateException("Đơn này đã được thanh toán rồi.");
        }
        long amount = gatewayAmount(payment);
        String ref = gatewayReference(payment);
        String channel = qr ? "Q" : "H";
        if (ref == null || !ref.startsWith("UTE-")) {
            // Một reference bền vững cho mỗi khoản cước: retry không sinh thêm giao dịch thu tiền.
            String prefix = "UTE-" + orderId + "-" + channel + "-";
            // Reserve 20 chars for '.' + provider transId after settlement; no schema change.
            int suffixLength = Math.min(16, 30 - prefix.length());
            if (suffixLength < 4) throw new IllegalStateException("Mã đơn vượt giới hạn giao dịch MoMo.");
            ref = prefix + UUID.randomUUID().toString().replace("-", "").substring(0, suffixLength);
            payment.setTransactionRef(ref);
            payment.setUpdatedAt(LocalDateTime.now());
            orderPaymentRepository.saveAndFlush(payment);
        } else if (!ref.startsWith("UTE-" + orderId + "-" + channel + "-")) {
            throw new IllegalStateException("Đơn đã có giao dịch MoMo. Hãy tiếp tục giao dịch hiện tại hoặc kiểm tra trạng thái.");
        }
        return new MomoPaymentAttempt(orderId, ref, amount, orderInfo(order), false);
    }

    @Override
    @Transactional(readOnly = true)
    public MomoPaymentAttempt findOwnedMomoAttempt(Long orderId, String username) {
        OrderPayment payment = findOwnedPayment(orderId, username);
        requireMomo(payment);
        String reference = gatewayReference(payment);
        if (reference == null || !reference.startsWith("UTE-" + orderId + "-")) {
            throw new IllegalStateException("Đơn chưa tạo giao dịch MoMo. Hãy chọn cách thanh toán trước.");
        }
        return new MomoPaymentAttempt(orderId, reference, gatewayAmount(payment),
                orderInfo(payment.getOrder()), payment.getStatus() == OrderPayment.PaymentStatus.PAID);
    }

    @Override
    public OrderPayment settleVerifiedMomo(Long orderId, String reference, long amount, String providerTransactionId) {
        Order order = lockedOrder(orderId);
        OrderPayment payment = lockedPayment(order);
        requireMomo(payment);
        if (!Objects.equals(gatewayReference(payment), reference) || gatewayAmount(payment) != amount
                || !validProviderTransaction(providerTransactionId)) {
            throw new IllegalStateException("Kết quả MoMo không khớp giao dịch hoặc số tiền cước.");
        }
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) {
            if (!Objects.equals(payment.getTransactionRef(), reference + "." + providerTransactionId)) {
                throw new IllegalStateException("Mã giao dịch MoMo khác lần thanh toán đã xác nhận.");
            }
            return payment;
        }
        // Lưu sự thật tiền đã nhận, kể cả IPN tới sau khi khách hủy đơn. Manager đối soát hoàn phí riêng.
        // Lưu cả requestId và transId trong cột hiện có; requestId vẫn dùng để query/retry.
        payment.setTransactionRef(reference + "." + providerTransactionId);
        payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        return orderPaymentRepository.save(payment);
    }

    @Override
    public OrderPayment markCodCollected(Long orderId) {
        Order order = lockedOrder(orderId);
        OrderPayment payment = lockedPayment(order);

        if (payment.getMethod() != OrderPayment.PaymentMethod.COD) {
            throw new IllegalStateException("Đơn này không phải đơn COD");
        }
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) {
            return payment;
        }

        payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        return orderPaymentRepository.save(payment);
    }

    // ----- Phần dùng chung trong service -----

    private AppUser requireUser(String username) {
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng: " + username));
    }

    /**
     * Đơn đã dừng hẳn, không còn đường đi tiếp trong quy trình giao hàng.
     *
     * FAILED không tính vào đây: giao thất bại thì shipper vẫn còn giao lại được,
     * đơn chưa coi là chấm dứt.
     */
    private boolean isOrderClosed(Order order) {
        return order != null
                && (order.getStatus() == Order.OrderStatus.CANCELLED
                    || order.getStatus() == Order.OrderStatus.RETURNED);
    }

    private Order lockedOrder(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này"));
    }

    private OrderPayment lockedPayment(Order order) {
        return orderPaymentRepository.findByOrderForUpdate(order)
                .orElseThrow(() -> new IllegalStateException("Đơn hàng này chưa có thông tin thanh toán"));
    }

    private void requireOwner(Order order, String username) {
        if (order.getSender() == null || !Objects.equals(order.getSender().getUsername(), username)) {
            throw new IllegalStateException("Bạn không có quyền thanh toán đơn này.");
        }
    }

    private void requireMomo(OrderPayment payment) {
        if (payment.getMethod() != OrderPayment.PaymentMethod.MOMO) {
            throw new IllegalStateException("Đơn này không dùng phương thức MoMo.");
        }
    }

    private long gatewayAmount(OrderPayment payment) {
        try {
            long amount = payment.getAmount().longValueExact();
            if (amount >= 1000 && amount <= 50_000_000) return amount;
        } catch (ArithmeticException | NullPointerException ignored) { }
        throw new IllegalStateException("MoMo chỉ hỗ trợ cước từ 1.000 đến 50.000.000 đồng.");
    }

    private String orderInfo(Order order) {
        return "Cuoc van chuyen UTEExpress " + order.getTrackingCode();
    }

    private String gatewayReference(OrderPayment payment) {
        String reference = payment.getTransactionRef();
        return reference == null ? null : reference.split("\\.", 2)[0];
    }

    private boolean validProviderTransaction(String transaction) {
        try { return transaction != null && transaction.matches("[1-9][0-9]{0,18}") && Long.parseLong(transaction) > 0; }
        catch (NumberFormatException ex) { return false; }
    }
}
