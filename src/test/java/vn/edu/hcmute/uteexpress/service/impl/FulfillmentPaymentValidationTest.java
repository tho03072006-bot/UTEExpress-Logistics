package vn.edu.hcmute.uteexpress.service.impl;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FulfillmentPaymentValidationTest {
    @ParameterizedTest @CsvSource({"18000,1000,18000", "18000,-1,18000", "-1,0,18000", "18000,1.5,18000", "18000,0,0", "18000,18001,0"})
    void inconsistentFeeDiscountAndAmountMustNotBeFulfilled(String fee, String discount, String amount) {
        var order=new Order();order.setShippingFee(new BigDecimal(fee));
        var payment=payment(order,discount,amount);
        assertThrows(IllegalArgumentException.class,()->service(payment).requireReadyForFulfillment(order));
    }
    @Test void missingShippingFeeMustNotBeFulfilled() {
        var order=new Order();var payment=payment(order,"0","18000");
        assertThrows(IllegalArgumentException.class,()->service(payment).requireReadyForFulfillment(order));
    }
    @Test void validDiscountAndFullyDiscountedCodRemainSupported() {
        var order=new Order();order.setShippingFee(new BigDecimal("18000"));
        assertDoesNotThrow(()->service(payment(order,"2000","16000")).requireReadyForFulfillment(order));
        assertDoesNotThrow(()->service(payment(order,"18000","0")).requireReadyForFulfillment(order));
    }
    @Test void momoMarkedPaidWithoutReceiptCannotBypassFulfillmentChecks() {
        var order=new Order();order.setShippingFee(new BigDecimal("18000"));
        var payment=payment(order,"0","18000");payment.setMethod(OrderPayment.PaymentMethod.MOMO);payment.setStatus(OrderPayment.PaymentStatus.PAID);
        assertThrows(IllegalArgumentException.class,()->service(payment).requireReadyForFulfillment(order));
    }
    @Test void paidMomoWithMatchingSnapshotAndReceiptCanBeFulfilled() {
        var order=new Order();order.setShippingFee(new BigDecimal("18000"));
        var payment=payment(order,"0","18000");payment.setMethod(OrderPayment.PaymentMethod.MOMO);payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.setPaidAt(java.time.LocalDateTime.now());payment.setTransactionRef("SIM-MOMO-TEST-RECEIPT");
        assertDoesNotThrow(()->service(payment).requireReadyForFulfillment(order));
    }
    private OrderPayment payment(Order order,String discount,String amount) {
        var payment=new OrderPayment();payment.setOrder(order);payment.setDiscountAmount(new BigDecimal(discount));payment.setAmount(new BigDecimal(amount));
        payment.setMethod(OrderPayment.PaymentMethod.COD);payment.setStatus(OrderPayment.PaymentStatus.UNPAID);return payment;
    }
    private OrderPaymentServiceImpl service(OrderPayment payment) {
        var payments=mock(OrderPaymentRepository.class);when(payments.findByOrder(payment.getOrder())).thenReturn(Optional.of(payment));
        return new OrderPaymentServiceImpl(payments,mock(OrderRepository.class),mock(AppUserRepository.class),mock(org.springframework.context.ApplicationEventPublisher.class));
    }
}
