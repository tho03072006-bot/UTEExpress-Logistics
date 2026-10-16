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
        var payment = payment(); payment.setAmount(new BigDecimal(amount));
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

    private OrderPayment payment() {
        var value = new OrderPayment(); value.setAmount(new BigDecimal("18000"));
        value.setMethod(OrderPayment.PaymentMethod.COD); value.setStatus(OrderPayment.PaymentStatus.UNPAID); return value;
    }
}
