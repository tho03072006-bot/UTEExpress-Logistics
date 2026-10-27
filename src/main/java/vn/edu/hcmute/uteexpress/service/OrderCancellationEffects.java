package vn.edu.hcmute.uteexpress.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.OrderPaymentRepository;
import vn.edu.hcmute.uteexpress.service.tracking.OrderComplaintService;
import vn.edu.hcmute.uteexpress.util.PaymentReadiness;

/**
 * Việc kèm theo mỗi lần hủy đơn trước khi lấy hàng, dù người gửi hay Manager/Admin hủy:
 * trả lại lượt mã giảm giá (đơn không được phục vụ thì không tính lượt) và, nếu đã trả ví, mở yêu cầu hoàn tiền
 * để tiền không nằm im ở trạng thái "Đã thanh toán" của một đơn đã hủy.
 */
@Service
public class OrderCancellationEffects {
    private final OrderPaymentRepository payments;
    private final PromoCodeService promoCodes;
    private final OrderComplaintService complaints;

    public OrderCancellationEffects(OrderPaymentRepository payments, PromoCodeService promoCodes, OrderComplaintService complaints) {
        this.payments = payments; this.promoCodes = promoCodes; this.complaints = complaints;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void afterCancel(Order order, String actor) {
        var payment = payments.findByOrder(order).orElse(null);
        if (payment == null) return;
        promoCodes.releaseUsage(payment.getPromoCode());
        if (PaymentReadiness.isRefundable(payment)) complaints.openRefundRequest(order, payment.getAmount(), actor);
    }
}
