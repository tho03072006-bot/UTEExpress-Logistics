package vn.edu.hcmute.uteexpress.service.payment;

import vn.edu.hcmute.uteexpress.entity.OrderPayment;

public interface MomoPaymentService {
    record Checkout(String url, String qrDataUrl, long expiresAt) { }
    record Status(String state, String message, String transactionRef, String paidAt) { }
    record Receipt(String trackingCode, String senderName, String receiverName, String receiverPhone,
                   String service, Double weightKg, String originalAmount, String discount, String amount,
                   boolean paid, String transactionRef, String paidAt, long expiresAt) { }
    Checkout createCheckout(long orderId, String username);
    Status status(long orderId, String username);
    Receipt view(String ticket);
    Receipt confirm(String ticket);
    /** paidNow = chính lần bấm này ghi nhận thanh toán; false nghĩa là đơn đã được trả từ trước (thiết bị khác đã bấm). */
    record Confirmation(Receipt receipt, boolean paidNow) { }
    Confirmation confirmOnce(String ticket);
    void switchLegacyPayment(long orderId, String username);
}
