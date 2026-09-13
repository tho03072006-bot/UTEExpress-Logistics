package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderPaymentRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class OrderPaymentServiceImpl implements OrderPaymentService {

    /** Tiền tố mã giao dịch giả lập, để nhìn là biết ngay không phải mã của cổng thật. */
    private static final String MOCK_TRANSACTION_PREFIX = "SIM";

    private static final SecureRandom RANDOM = new SecureRandom();

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
    public OrderPayment createForOrder(Order order, OrderPayment.PaymentMethod method) {
        OrderPayment payment = new OrderPayment();
        payment.setOrder(order);
        payment.setMethod(method != null ? method : OrderPayment.PaymentMethod.COD);
        payment.setAmount(order.getShippingFee() != null ? order.getShippingFee() : BigDecimal.ZERO);
        // Moi don deu bat dau o trang thai chua thanh toan: COD thi thu luc giao hang,
        // vi dien tu thi cho nguoi dung di qua trang thanh toan gia lap.
        payment.setStatus(OrderPayment.PaymentStatus.UNPAID);
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
        if (!order.getSender().getUsername().equals(username)) {
            throw new IllegalStateException("Bạn không có quyền xem thông tin thanh toán của đơn này");
        }
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

        payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.setTransactionRef(generateMockTransactionRef());
        payment.setPaidAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());
        return orderPaymentRepository.save(payment);
    }

    @Override
    public OrderPayment markCodCollected(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này"));
        OrderPayment payment = orderPaymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("Đơn hàng này chưa có thông tin thanh toán"));

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
     * Sinh mã giao dịch giả lập cho ví điện tử. Tiền tố SIM (simulation) để người chấm
     * và cả nhóm nhìn vào biết ngay đây là giao dịch mô phỏng, không phải mã thật
     * do VNPay/MoMo trả về.
     */
    private String generateMockTransactionRef() {
        long timestampPart = System.currentTimeMillis() % 1_000_000;
        int randomPart = RANDOM.nextInt(1_000);
        return String.format("%s%06d%03d", MOCK_TRANSACTION_PREFIX, timestampPart, randomPart);
    }
}
