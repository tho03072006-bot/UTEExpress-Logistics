package vn.edu.hcmute.uteexpress.dto.tracking;
import java.math.BigDecimal;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
public record ShipperMoneyView(boolean available, OrderPayment.PaymentMethod method,
        OrderPayment.PaymentStatus status, BigDecimal amount, BigDecimal discount, BigDecimal due) {
    public static ShipperMoneyView from(OrderPayment payment) {
        if (payment == null || payment.getMethod() == null || payment.getStatus() == null
                || !vn.edu.hcmute.uteexpress.util.PaymentReadiness.isValidAmount(payment.getAmount()))
            return new ShipperMoneyView(false, null, null, null, null, null);
        boolean collect = payment.getMethod() == OrderPayment.PaymentMethod.COD && payment.getStatus() == OrderPayment.PaymentStatus.UNPAID;
        return new ShipperMoneyView(true, payment.getMethod(), payment.getStatus(), payment.getAmount(),
                payment.getDiscountAmount() == null ? BigDecimal.ZERO : payment.getDiscountAmount(), collect ? payment.getAmount() : BigDecimal.ZERO);
    }
    public boolean isPrepaid() { return available && status == OrderPayment.PaymentStatus.PAID; }
    public boolean isCod() { return available && method == OrderPayment.PaymentMethod.COD; }
    public boolean isFulfillmentReady() {
        return available && method != null && status != null
                && vn.edu.hcmute.uteexpress.util.PaymentReadiness.isValidAmount(amount)
                && (isCod() || isPrepaid());
    }
}
