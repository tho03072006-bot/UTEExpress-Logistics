package vn.edu.hcmute.uteexpress.util;

import java.math.BigDecimal;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;

/** Một quy tắc chung cho điều phối, Shipper và giao diện; thiếu dữ liệu thì không cho giao. */
public final class PaymentReadiness {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999");

    private PaymentReadiness() { }

    public static boolean isValidAmount(BigDecimal amount) {
        return amount != null && amount.signum() >= 0 && amount.compareTo(MAX_AMOUNT) <= 0
                && amount.stripTrailingZeros().scale() <= 0;
    }

    public static boolean isReady(OrderPayment payment) {
        return payment != null && payment.getMethod() != null && payment.getStatus() != null
                && isValidAmount(payment.getAmount())
                && (payment.getMethod() == OrderPayment.PaymentMethod.COD
                    || payment.getStatus() == OrderPayment.PaymentStatus.PAID);
    }

    /** Cước đã chốt trừ giảm giá phải đúng số cần thu; không tin riêng trạng thái PAID hoặc một số tiền dương. */
    public static boolean hasConsistentSnapshot(OrderPayment payment) {
        if (payment == null || payment.getOrder() == null) return false;
        BigDecimal fee = payment.getOrder().getShippingFee();
        BigDecimal discount = payment.getDiscountAmount() == null ? BigDecimal.ZERO : payment.getDiscountAmount();
        return isValidAmount(fee) && isValidAmount(discount) && isValidAmount(payment.getAmount())
                && discount.compareTo(fee) <= 0 && fee.subtract(discount).compareTo(payment.getAmount()) == 0;
    }

    public static boolean hasRequiredReceipt(OrderPayment payment) {
        return payment != null && (payment.getMethod() != OrderPayment.PaymentMethod.MOMO
                || (payment.getPaidAt() != null && payment.getTransactionRef() != null && !payment.getTransactionRef().isBlank()));
    }
}
