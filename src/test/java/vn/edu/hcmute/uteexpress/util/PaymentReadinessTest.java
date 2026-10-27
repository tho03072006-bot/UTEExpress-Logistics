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

    @Test void compensationLimitIsFourTimesPaidFeeOrThreeTimesUncollectedCodFee() {
        var payment = payment(); payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.getOrder().setStatus(vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.DELIVERED);
        assertEquals(new BigDecimal("72000"), PaymentReadiness.compensationLimit(payment), "Đã thu 18.000 đ: tối đa 4 lần, gồm hoàn cước");
        payment = payment(); payment.getOrder().setStatus(vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PICKED_UP);
        assertEquals(new BigDecimal("54000"), PaymentReadiness.compensationLimit(payment), "Trả cước khi giao chưa thu: tối đa 3 lần");
        payment = payment(); payment.setMethod(OrderPayment.PaymentMethod.MOMO);
        assertEquals(0, PaymentReadiness.compensationLimit(payment).signum(), "Ví chưa trả thì đơn chưa được giao đi, không có cơ sở bồi thường");
        payment = payment(); payment.setAmount(new BigDecimal("20000"));
        assertEquals(0, PaymentReadiness.compensationLimit(payment).signum(), "Số liệu lệch cước đã chốt phải tra soát trước");
        payment = payment(); payment.setAmount(BigDecimal.ZERO); payment.setDiscountAmount(payment.getOrder().getShippingFee());
        assertEquals(0, PaymentReadiness.compensationLimit(payment).signum(), "Đơn miễn cước");
        assertEquals(0, PaymentReadiness.compensationLimit(null).signum());
    }

    @Test void goodsNeverPickedUpOnlyGetThePaidFeeBack() {
        for (var status : new vn.edu.hcmute.uteexpress.entity.Order.OrderStatus[]{
                vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.PENDING_PICKUP, vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.CANCELLED}) {
            var paidWallet = payment(); paidWallet.setMethod(OrderPayment.PaymentMethod.MOMO); paidWallet.setStatus(OrderPayment.PaymentStatus.PAID);
            paidWallet.getOrder().setStatus(status);
            assertEquals(new BigDecimal("18000"), PaymentReadiness.compensationLimit(paidWallet), status + ": chỉ hoàn lại 18.000 đ đã trả");
            var unpaidCod = payment(); unpaidCod.getOrder().setStatus(status);
            assertEquals(0, PaymentReadiness.compensationLimit(unpaidCod).signum(), status + ": chưa thu cước thì không có gì để hoàn");
            assertTrue(PaymentReadiness.neverInCustody(unpaidCod.getOrder()));
        }
        var inTransit = payment(); inTransit.getOrder().setStatus(vn.edu.hcmute.uteexpress.entity.Order.OrderStatus.IN_TRANSIT);
        assertFalse(PaymentReadiness.neverInCustody(inTransit.getOrder()));
    }

    private OrderPayment payment() {
        var value = new OrderPayment(); value.setAmount(new BigDecimal("18000"));
        var order = new vn.edu.hcmute.uteexpress.entity.Order(); order.setShippingFee(value.getAmount()); value.setOrder(order);
        value.setPaidAt(java.time.LocalDateTime.of(2026,10,15,12,0)); value.setTransactionRef("VALID-RECEIPT");
        value.setMethod(OrderPayment.PaymentMethod.COD); value.setStatus(OrderPayment.PaymentStatus.UNPAID); return value;
    }
}
