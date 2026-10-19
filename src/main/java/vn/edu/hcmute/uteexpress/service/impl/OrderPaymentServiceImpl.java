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
import vn.edu.hcmute.uteexpress.util.PaymentReadiness;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class OrderPaymentServiceImpl implements OrderPaymentService {

    private final OrderPaymentRepository orderPaymentRepository;
    private final OrderRepository orderRepository;
    private final AppUserRepository appUserRepository;
    private final org.springframework.context.ApplicationEventPublisher events;

    public OrderPaymentServiceImpl(OrderPaymentRepository orderPaymentRepository,
                                   OrderRepository orderRepository,
                                   AppUserRepository appUserRepository, org.springframework.context.ApplicationEventPublisher events) {
        this.orderPaymentRepository = orderPaymentRepository;
        this.orderRepository = orderRepository;
        this.appUserRepository = appUserRepository;
        this.events = events;
    }

    @Override
    public OrderPayment createForOrder(Order order, OrderPayment.PaymentMethod method,
                                       PromoCode promoCode, BigDecimal discountAmount) {
        if (method == OrderPayment.PaymentMethod.VNPAY) {
            throw new IllegalArgumentException("VNPay không còn được hỗ trợ. Vui lòng chọn COD hoặc MoMo mô phỏng.");
        }
        BigDecimal fee = order.getShippingFee();
        BigDecimal discount = discountAmount != null ? discountAmount : BigDecimal.ZERO;
        if (!PaymentReadiness.isValidAmount(fee) || !PaymentReadiness.isValidAmount(discount)
                || discount.compareTo(fee) > 0) {
            throw new IllegalArgumentException("Cước và giảm giá phải là số đồng không âm; giảm giá không vượt cước gốc.");
        }

        OrderPayment payment = new OrderPayment();
        payment.setOrder(order);
        payment.setMethod(method != null ? method : OrderPayment.PaymentMethod.COD);
        payment.setPromoCode(promoCode);
        payment.setDiscountAmount(discount);
        // Cuoc goc van nam nguyen o Order.shippingFee; o day la so tien khach thuc tra.
        payment.setAmount(fee.subtract(discount).max(BigDecimal.ZERO));
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
    public void requireReadyForFulfillment(Order order) {
        if (!orderPaymentRepository.findByOrder(order).map(PaymentReadiness::isReady).orElse(false)) {
            throw new IllegalArgumentException("Đơn chưa đủ điều kiện thanh toán: ví phải trả trước, COD phải có thông tin cước hợp lệ.");
        }
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
        // Cùng khóa vận đơn với hủy đơn/điều phối để không thanh toán và hủy đồng thời.
        Order order = lockOrder(orderId);
        if (order.getSender() == null || !order.getSender().getUsername().equals(username)) {
            throw new IllegalStateException("Bạn không có quyền thanh toán đơn này.");
        }
        OrderPayment payment = orderPaymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("Đơn hàng này chưa có thông tin thanh toán"));
        if (payment.getMethod() == null || payment.getStatus() == null || !PaymentReadiness.isValidAmount(payment.getAmount())) {
            throw new IllegalStateException("Thông tin thanh toán không hợp lệ. Hãy liên hệ Manager.");
        }

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

        vn.edu.hcmute.uteexpress.service.payment.WalletPaymentRules.markPaid(payment, java.time.Instant.now());
        events.publishEvent(new vn.edu.hcmute.uteexpress.dto.tracking.OrderStatusChangedEvent(
                order.getId(), order.getTrackingCode(), order.getStatus().name(), payment.getUpdatedAt()));
        return orderPaymentRepository.save(payment);
    }

    @Override
    public OrderPayment markCodCollected(Long orderId) {
        Order order = lockOrder(orderId);
        OrderPayment payment = orderPaymentRepository.findByOrder(order)
                .orElseThrow(() -> new IllegalStateException("Đơn hàng này chưa có thông tin thanh toán"));

        if (payment.getMethod() != OrderPayment.PaymentMethod.COD) {
            throw new IllegalStateException("Đơn này không phải đơn COD");
        }
        if (order.getStatus() != Order.OrderStatus.DELIVERED || !PaymentReadiness.isValidAmount(payment.getAmount())) {
            throw new IllegalStateException("Chỉ ghi thu cước COD hợp lệ khi đã giao thành công.");
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

    private Order lockOrder(Long orderId) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("Mã đơn không hợp lệ.");
        return orderRepository.findLockedById(orderId)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy đơn hàng này"));
    }

    private AppUser requireUser(String username) {
        return appUserRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy người dùng: " + username));
    }

    /**
     * Sinh mã giao dịch giả lập cho ví điện tử. Tiền tố SIM (simulation) để người chấm
     * và cả nhóm nhìn vào biết ngay đây là giao dịch mô phỏng, không phải mã thật
     * do VNPay/MoMo trả về.
     */
    /**
     * Đơn đã dừng hẳn, không còn đường đi tiếp trong quy trình giao hàng.
     *
     * FAILED không tính vào đây: giao thất bại thì shipper vẫn còn giao lại được,
     * đơn chưa coi là chấm dứt.
     */
    private boolean isOrderClosed(Order order) {
        return order != null
                && (order.getStatus() == Order.OrderStatus.CANCELLED
                    || order.getStatus() == Order.OrderStatus.DELIVERED
                    || order.getStatus() == Order.OrderStatus.RETURNED);
    }

}
