package vn.edu.hcmute.uteexpress.service.payment;

import java.time.*;
import java.util.UUID;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.util.PaymentReadiness;

/** Cùng quy tắc cho web local và ứng dụng Render, không có phiên bản nghiệp vụ thứ hai. */
public final class WalletPaymentRules {
    private WalletPaymentRules() { }
    public static boolean isClosed(Order order) {
        return order.getStatus() == null || switch (order.getStatus()) {
            case CANCELLED, RETURNED, DELIVERED -> true;
            default -> false;
        };
    }
    public static void validate(OrderPayment payment) {
        if (payment.getStatus() == null || !PaymentReadiness.isValidAmount(payment.getAmount()))
            throw new IllegalStateException("Thông tin thanh toán không hợp lệ. Hãy liên hệ Manager.");
        if (isClosed(payment.getOrder())) throw new IllegalStateException("Đơn này đã kết thúc nên không thanh toán được nữa.");
        if (payment.getMethod() != OrderPayment.PaymentMethod.MOMO)
            throw new IllegalStateException("Chỉ hỗ trợ ví MoMo mô phỏng. COD thu khi giao; VNPay không còn được hỗ trợ.");
    }
    public static void markPaid(OrderPayment payment, Instant now) {
        validate(payment);
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) throw new IllegalStateException("Đơn này đã được thanh toán rồi.");
        payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.setTransactionRef("SIM-MOMO-" + UUID.randomUUID().toString().replace("-", ""));
        var vietnamTime = LocalDateTime.ofInstant(now, ZoneId.of("Asia/Ho_Chi_Minh"));
        payment.setPaidAt(vietnamTime); payment.setUpdatedAt(vietnamTime);
    }
}
