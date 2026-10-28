package vn.edu.hcmute.uteexpress.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import static org.junit.jupiter.api.Assertions.*;
import static vn.edu.hcmute.uteexpress.dto.PaymentCollectionView.State.*;

class PaymentCollectionViewTest {
    private OrderPayment payment() {
        var order=new Order();order.setShippingFee(new BigDecimal("20000"));
        var payment=new OrderPayment();payment.setOrder(order);payment.setAmount(new BigDecimal("18000"));
        payment.setDiscountAmount(new BigDecimal("2000"));payment.setMethod(OrderPayment.PaymentMethod.COD);return payment;
    }
    @Test void onlyValidUnpaidCashShowsAnAmountToCollect() {
        var payment=payment();var view=PaymentCollectionView.from(payment.getOrder(),payment);
        assertEquals(COD_DUE,view.state());assertEquals(new BigDecimal("18000"),view.amount());
        payment.setMethod(OrderPayment.PaymentMethod.MOMO);
        assertEquals(WALLET_PENDING,PaymentCollectionView.from(payment.getOrder(),payment).state());
        payment.setMethod(OrderPayment.PaymentMethod.VNPAY);
        assertEquals(LEGACY_WALLET,PaymentCollectionView.from(payment.getOrder(),payment).state());
    }
    @Test void missingOrInconsistentDataNeverLooksLikeFreeDelivery() {
        var order=payment().getOrder();assertEquals(REVIEW,PaymentCollectionView.from(order,null).state());
        for(var amount:List.of(new BigDecimal("-1"),new BigDecimal("18000.5"),new BigDecimal("9999999999999"),new BigDecimal("18001"))) {
            var payment=payment();payment.setAmount(amount);var view=PaymentCollectionView.from(payment.getOrder(),payment);
            assertEquals(REVIEW,view.state());assertNull(view.amount());
        }
        var payment=payment();payment.setAmount(null);assertEquals(REVIEW,PaymentCollectionView.from(payment.getOrder(),payment).state());
    }
    @Test void paidWalletNeedsBothTimestampAndTransactionReference() {
        var payment=payment();payment.setMethod(OrderPayment.PaymentMethod.MOMO);payment.setStatus(OrderPayment.PaymentStatus.PAID);
        assertEquals(REVIEW,PaymentCollectionView.from(payment.getOrder(),payment).state());
        payment.setPaidAt(LocalDateTime.now());assertEquals(REVIEW,PaymentCollectionView.from(payment.getOrder(),payment).state());
        payment.setTransactionRef("SIM-MOMO-TEST");assertEquals(PAID,PaymentCollectionView.from(payment.getOrder(),payment).state());
    }
    @Test void paidCashWithoutTimestampStillNeedsReview() {
        var payment=payment();payment.setStatus(OrderPayment.PaymentStatus.PAID);
        assertEquals(REVIEW,PaymentCollectionView.from(payment.getOrder(),payment).state());
        payment.setPaidAt(LocalDateTime.now());assertEquals(PAID,PaymentCollectionView.from(payment.getOrder(),payment).state());
    }
    @Test void closedUnpaidOrdersCannotTellTheCourierToCollectCash() {
        for(var state:List.of(Order.OrderStatus.CANCELLED,Order.OrderStatus.RETURNED,Order.OrderStatus.DELIVERED)) {
            var payment=payment();payment.getOrder().setStatus(state);
            assertEquals(state==Order.OrderStatus.DELIVERED?REVIEW:CLOSED,PaymentCollectionView.from(payment.getOrder(),payment).state());
        }
    }
    @Test void fullyDiscountedValidOrdersAreExplicitlyFree() {
        var payment=payment();payment.setAmount(BigDecimal.ZERO);payment.setDiscountAmount(payment.getOrder().getShippingFee());
        var view=PaymentCollectionView.from(payment.getOrder(),payment);assertEquals(FREE,view.state());assertEquals(BigDecimal.ZERO,view.amount());
    }
    @Test void zeroAmountWalletStillNeedsItsExistingConfirmationFlow() {
        var payment=payment(); payment.setAmount(BigDecimal.ZERO); payment.setDiscountAmount(payment.getOrder().getShippingFee());
        payment.setMethod(OrderPayment.PaymentMethod.MOMO);
        var view=PaymentCollectionView.from(payment.getOrder(),payment);
        assertEquals(WALLET_PENDING,view.state()); assertEquals(0,view.amount().signum());
        assertTrue(view.message().contains("xác nhận"));
        payment.setMethod(OrderPayment.PaymentMethod.VNPAY);
        assertEquals(LEGACY_WALLET,PaymentCollectionView.from(payment.getOrder(),payment).state());
        payment.setMethod(OrderPayment.PaymentMethod.MOMO); payment.setStatus(OrderPayment.PaymentStatus.PAID);
        payment.setPaidAt(LocalDateTime.now()); payment.setTransactionRef("SIM-MOMO-ZERO");
        assertEquals(FREE,PaymentCollectionView.from(payment.getOrder(),payment).state());
    }

    @Test void anotherOrdersPaymentOrMissingMethodCannotBeUsedForTheLabel() {
        var payment=payment();assertEquals(REVIEW,PaymentCollectionView.from(payment().getOrder(),payment).state());
        payment.setMethod(null);assertEquals(REVIEW,PaymentCollectionView.from(payment.getOrder(),payment).state());
        assertEquals(REVIEW,PaymentCollectionView.from(null,payment).state());
    }
}
