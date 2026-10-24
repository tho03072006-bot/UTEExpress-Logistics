package vn.edu.hcmute.uteexpress.util;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.hcmute.uteexpress.dto.tracking.ShipperMoneyView;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import static org.junit.jupiter.api.Assertions.*;

class PaymentReadinessTest {
    @ParameterizedTest @CsvSource({"COD,UNPAID,true", "COD,PAID,true", "MOMO,UNPAID,false", "VNPAY,UNPAID,false", "MOMO,PAID,true", "VNPAY,PAID,true"})
    void serviceAndViewUseTheSameReadinessRule(OrderPayment.PaymentMethod method, OrderPayment.PaymentStatus status, boolean expected) {
        var payment = payment(); payment.setMethod(method); payment.setStatus(status);
        assertEquals(expected, PaymentReadiness.isReady(payment));
        assertEquals(expected, ShipperMoneyView.from(payment).isFulfillmentReady());
    }

    @ParameterizedTest @CsvSource({"-1,false", "0,true", "0.0,true", "1.5,false", "999999999999,true", "1000000000000,false"})
    void amountMustBeWholeNonNegativeVndWithinDatabasePrecision(String amount, boolean valid) {
        var payment = payment(); payment.setAmount(new BigDecimal(amount)); payment.getOrder().setShippingFee(payment.getAmount());
        assertEquals(valid, PaymentReadiness.isValidAmount(payment.getAmount()));
        assertEquals(valid, PaymentReadiness.isReady(payment));
        assertEquals(valid, ShipperMoneyView.from(payment).isFulfillmentReady());
    }

    @Test void missingPaymentOrRequiredFieldsFailClosedWithoutNullPointerErrors() {
        assertFalse(PaymentReadiness.isReady(null)); assertFalse(ShipperMoneyView.from(null).isFulfillmentReady());
        var payment = payment(); payment.setAmount(null); assertFalse(PaymentReadiness.isReady(payment));
        payment = payment(); payment.setMethod(null); assertFalse(PaymentReadiness.isReady(payment));
        payment = payment(); payment.setStatus(null); assertFalse(PaymentReadiness.isReady(payment));
    }

    @Test void shipperButtonsMustBeDisabledWhenBackendRejectsAnInconsistentPayment() {
        var payment=payment();var order=new vn.edu.hcmute.uteexpress.entity.Order();order.setShippingFee(new BigDecimal("18000"));payment.setOrder(order);
        payment.setDiscountAmount(new BigDecimal("1000"));
        assertFalse(ShipperMoneyView.from(payment).isFulfillmentReady());
    }

    @Test void shipperCannotSeeMomoAsPrepaidWithoutItsReceipt() {
        var payment=payment();var order=new vn.edu.hcmute.uteexpress.entity.Order();order.setShippingFee(new BigDecimal("18000"));payment.setOrder(order);
        payment.setMethod(OrderPayment.PaymentMethod.MOMO);payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.setPaidAt(null); payment.setTransactionRef(null);
        assertFalse(ShipperMoneyView.from(payment).isFulfillmentReady());
    }

    @Test void compensationNeedsActuallyPaidPositiveAndConsistentAmount() {
        var payment = payment(); assertFalse(PaymentReadiness.isRefundable(payment));
        payment.setStatus(OrderPayment.PaymentStatus.PAID); assertTrue(PaymentReadiness.isRefundable(payment));
        payment.setPaidAt(null); assertFalse(PaymentReadiness.isRefundable(payment));
        payment = payment(); payment.setStatus(OrderPayment.PaymentStatus.PAID); payment.setAmount(BigDecimal.ZERO);
        payment.setDiscountAmount(payment.getOrder().getShippingFee()); assertFalse(PaymentReadiness.isRefundable(payment));
        payment = payment(); payment.setStatus(OrderPayment.PaymentStatus.PAID); payment.setAmount(new BigDecimal("20000"));
        assertFalse(PaymentReadiness.isRefundable(payment)); assertFalse(PaymentReadiness.isRefundable(null));
    }

    @Test void compensationRequiresWalletReceiptAndAllowsVerifiedMomoPayment() {
        var payment = payment(); payment.setMethod(OrderPayment.PaymentMethod.MOMO); payment.setStatus(OrderPayment.PaymentStatus.PAID);
        assertTrue(PaymentReadiness.isRefundable(payment));
        payment.setTransactionRef("   "); assertFalse(PaymentReadiness.isRefundable(payment));
    }

    private OrderPayment payment() {
        var value = new OrderPayment(); value.setAmount(new BigDecimal("18000"));
        var order = new vn.edu.hcmute.uteexpress.entity.Order(); order.setShippingFee(value.getAmount()); value.setOrder(order);
        value.setPaidAt(java.time.LocalDateTime.of(2026,10,15,12,0)); value.setTransactionRef("VALID-RECEIPT");
        value.setMethod(OrderPayment.PaymentMethod.COD); value.setStatus(OrderPayment.PaymentStatus.UNPAID); return value;
    }
}
