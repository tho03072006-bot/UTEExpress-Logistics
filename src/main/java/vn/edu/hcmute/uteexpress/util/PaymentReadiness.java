package vn.edu.hcmute.uteexpress.util;

import java.math.BigDecimal;
import vn.edu.hcmute.uteexpress.entity.Order;
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
                && hasConsistentSnapshot(payment) && hasRequiredReceipt(payment)
                && (payment.getMethod() == OrderPayment.PaymentMethod.COD
                    || payment.getStatus() == OrderPayment.PaymentStatus.PAID);
    }

    /** Cước đã thực thu hợp lệ; đơn miễn phí không phát sinh khoản có thể hoàn. */
    public static boolean isRefundable(OrderPayment payment) {
        return isReady(payment) && payment.getStatus() == OrderPayment.PaymentStatus.PAID
                && payment.getPaidAt() != null && payment.getAmount().signum() > 0;
    }

    /** Hàng không khai giá bị mất/hư hỏng: bồi thường tối đa 4 lần cước, đã gồm hoàn cước (Điều 25 Nghị định 47/2011/NĐ-CP). */
    public static final int COMPENSATION_FEE_MULTIPLE = 4;
    public static final String COMPENSATION_RULE = "Hàng không khai giá được bồi thường tối đa 4 lần cước, đã gồm hoàn cước đã thu "
            + "(Điều 25 Nghị định 47/2011/NĐ-CP); đơn trả cước khi giao mà chưa thu cước thì tối đa 3 lần, vì không thu cước nữa. "
            + "Đơn chưa được lấy hàng hoặc đã hủy trước khi lấy chỉ được hoàn lại cước đã thu.";

    /** Hàng chưa từng vào tay hãng (đang chờ lấy hoặc đã hủy trước khi lấy): không có mất/hư hỏng nào để bồi thường. */
    public static boolean neverInCustody(Order order) {
        return order != null && (order.getStatus() == Order.OrderStatus.PENDING_PICKUP || order.getStatus() == Order.OrderStatus.CANCELLED);
    }

    /**
     * Mức bồi thường tối đa của đơn. Đã thu cước: 4 lần cước. Trả cước khi giao mà chưa thu (hàng mất/hỏng trước khi giao):
     * 3 lần, để người gửi được bù như nhau dù đã trả cước hay chưa. Hàng chưa từng vào tay hãng: chỉ hoàn cước đã thu (chưa thu thì 0).
     * Thiếu chứng từ, số liệu lệch hoặc đơn miễn cước: 0, cần tra soát trước.
     */
    public static BigDecimal compensationLimit(OrderPayment payment) {
        if (!isReady(payment) || payment.getAmount().signum() <= 0) return BigDecimal.ZERO;
        if (neverInCustody(payment.getOrder())) return isRefundable(payment) ? payment.getAmount() : BigDecimal.ZERO;
        if (isRefundable(payment)) return payment.getAmount().multiply(BigDecimal.valueOf(COMPENSATION_FEE_MULTIPLE));
        if (payment.getMethod() == OrderPayment.PaymentMethod.COD && payment.getStatus() == OrderPayment.PaymentStatus.UNPAID)
            return payment.getAmount().multiply(BigDecimal.valueOf(COMPENSATION_FEE_MULTIPLE - 1));
        return BigDecimal.ZERO;
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
        return payment != null
                && (payment.getStatus() != OrderPayment.PaymentStatus.PAID || payment.getPaidAt() != null)
                && (payment.getMethod() != OrderPayment.PaymentMethod.MOMO
                || (payment.getPaidAt() != null && payment.getTransactionRef() != null && !payment.getTransactionRef().isBlank()));
    }
}
