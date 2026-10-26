package vn.edu.hcmute.uteexpress.dto;

import java.math.BigDecimal;
import java.util.Objects;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.util.PaymentReadiness;

/** Chỉ dẫn thu cước trên trang người gửi và nhãn kiện; thiếu dữ liệu không được coi là miễn thu. */
public record PaymentCollectionView(State state, BigDecimal amount, String message) {
    public enum State { REVIEW, CLOSED, FREE, PAID, COD_DUE, WALLET_PENDING, LEGACY_WALLET }

    public static PaymentCollectionView from(Order order, OrderPayment payment) {
        if (order == null || order.getStatus() == null || payment == null || payment.getMethod() == null || payment.getStatus() == null
                || !PaymentReadiness.hasConsistentSnapshot(payment) || payment.getOrder() != order
                    && (order.getId() == null || !Objects.equals(order.getId(), payment.getOrder().getId()))) return review();
        BigDecimal amount = payment.getAmount();
        if (payment.getStatus() == OrderPayment.PaymentStatus.PAID) {
            if (payment.getPaidAt() == null || !PaymentReadiness.hasRequiredReceipt(payment)) return review();
            return amount.signum() == 0 ? free() : new PaymentCollectionView(State.PAID, amount, "Đã thanh toán · không thu lại");
        }
        if (order.getStatus() == Order.OrderStatus.CANCELLED || order.getStatus() == Order.OrderStatus.RETURNED)
            return new PaymentCollectionView(State.CLOSED, null, "Đơn đã hủy/hoàn · không thu cước");
        if (order.getStatus() == Order.OrderStatus.DELIVERED) return review();
        if (amount.signum() == 0 && payment.getMethod() == OrderPayment.PaymentMethod.COD) return free();
        return switch (payment.getMethod()) {
            case COD -> new PaymentCollectionView(State.COD_DUE, amount, "Thu cước người nhận");
            case MOMO -> new PaymentCollectionView(State.WALLET_PENDING, amount, amount.signum() == 0
                    ? "Miễn cước · chờ xác nhận MoMo" : "MoMo chưa thanh toán · không thu tiền mặt");
            case VNPAY -> new PaymentCollectionView(State.LEGACY_WALLET, amount, "Ví cũ chưa thanh toán · cần chuyển sang MoMo");
        };
    }
    private static PaymentCollectionView review() { return new PaymentCollectionView(State.REVIEW, null, "Chưa xác định cước · cần tra soát"); }
    private static PaymentCollectionView free() { return new PaymentCollectionView(State.FREE, BigDecimal.ZERO, "Miễn cước · không thu tiền"); }
}
