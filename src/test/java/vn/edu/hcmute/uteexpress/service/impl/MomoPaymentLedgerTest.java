package vn.edu.hcmute.uteexpress.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderPaymentRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MomoPaymentLedgerTest {
    @Mock OrderRepository orders;
    @Mock OrderPaymentRepository payments;
    @Mock AppUserRepository users;
    OrderPaymentServiceImpl service;
    Order order;
    OrderPayment payment;

    @BeforeEach
    void setup() {
        service = new OrderPaymentServiceImpl(payments, orders, users);
        AppUser sender = new AppUser(); sender.setId(1L); sender.setUsername("owner");
        order = new Order(); order.setId(12L); order.setSender(sender); order.setTrackingCode("UTE0012");
        order.setStatus(Order.OrderStatus.PENDING_PICKUP);
        payment = new OrderPayment(); payment.setOrder(order); payment.setAmount(BigDecimal.valueOf(30000));
        payment.setMethod(OrderPayment.PaymentMethod.MOMO);
    }

    private void locked() {
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByOrderForUpdate(order)).thenReturn(Optional.of(payment));
    }

    @Test void reservingAndRetryUseOnePersistedId() {
        locked();
        var first = service.reserveMomoAttempt(12L, "owner", true);
        var retry = service.reserveMomoAttempt(12L, "owner", true);
        assertThat(retry.reference()).isEqualTo(first.reference());
        assertThat(first.amount()).isEqualTo(30000);
        assertThat(payment.getStatus()).isEqualTo(OrderPayment.PaymentStatus.UNPAID);
        verify(payments, times(1)).saveAndFlush(payment);
        var sequence = inOrder(orders, payments);
        sequence.verify(orders).findByIdForUpdate(12L);
        sequence.verify(payments).findByOrderForUpdate(order);
        sequence.verify(payments).saveAndFlush(payment);
    }

    @Test void anotherUserCannotReserveOrQuery() {
        when(orders.findByIdForUpdate(12L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.reserveMomoAttempt(12L, "stranger", true)).hasMessageContaining("quyền");
        verifyNoInteractions(payments);
        when(orders.findById(12L)).thenReturn(Optional.of(order));
        assertThatThrownBy(() -> service.findOwnedMomoAttempt(12L, "stranger")).hasMessageContaining("quyền");
        verifyNoInteractions(payments);
    }

    @Test void closedOrdersCannotStartCheckout() {
        locked(); order.setStatus(Order.OrderStatus.CANCELLED);
        assertThatThrownBy(() -> service.reserveMomoAttempt(12L, "owner", true)).hasMessageContaining("kết thúc");
        verify(payments, never()).saveAndFlush(any());
    }

    @Test void wrongReferenceOrAmountCannotSettle() {
        locked(); payment.setTransactionRef("UTE-12-Q-aabbccdd");
        assertThatThrownBy(() -> service.settleVerifiedMomo(12L, "UTE-12-Q-ffffffff", 30000, "1234"))
                .hasMessageContaining("không khớp");
        assertThatThrownBy(() -> service.settleVerifiedMomo(12L, "UTE-12-Q-aabbccdd", 30001, "1234"))
                .hasMessageContaining("không khớp");
        assertThatThrownBy(() -> service.settleVerifiedMomo(12L, "UTE-12-Q-aabbccdd", 30000, "0"))
                .hasMessageContaining("không khớp");
        assertThat(payment.getStatus()).isEqualTo(OrderPayment.PaymentStatus.UNPAID);
        verify(payments, never()).save(any());
    }

    @Test void settlementIsIdempotentAndRejectsASecondProviderTransaction() {
        locked(); payment.setTransactionRef("UTE-12-Q-aabbccdd");
        when(payments.save(payment)).thenReturn(payment);
        service.settleVerifiedMomo(12L, "UTE-12-Q-aabbccdd", 30000, "1234");
        var originalDate = payment.getPaidAt();
        service.settleVerifiedMomo(12L, "UTE-12-Q-aabbccdd", 30000, "1234");
        assertThat(payment.getTransactionRef()).isEqualTo("UTE-12-Q-aabbccdd.1234");
        assertThat(payment.getPaidAt()).isEqualTo(originalDate);
        verify(payments, times(1)).save(payment);
        assertThatThrownBy(() -> service.settleVerifiedMomo(12L, "UTE-12-Q-aabbccdd", 30000, "1235"))
                .hasMessageContaining("khác lần");
    }

    @Test void lateSuccessfulIpnRecordsMoneyEvenAfterCancellation() {
        locked(); payment.setTransactionRef("UTE-12-Q-aabbccdd"); order.setStatus(Order.OrderStatus.CANCELLED);
        when(payments.save(payment)).thenReturn(payment);
        service.settleVerifiedMomo(12L, "UTE-12-Q-aabbccdd", 30000, "1234");
        assertThat(payment.getStatus()).isEqualTo(OrderPayment.PaymentStatus.PAID);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CANCELLED);
    }

    @Test void maximumLongReferencesFitExistingColumnAndCanBeQueriedAfterPayment() {
        order.setId(Long.MAX_VALUE); locked();
        var attempt = service.reserveMomoAttempt(Long.MAX_VALUE, "owner", false);
        assertThat(attempt.reference()).hasSize(30);
        when(payments.save(payment)).thenReturn(payment);
        service.settleVerifiedMomo(Long.MAX_VALUE, attempt.reference(), 30000, Long.toString(Long.MAX_VALUE));
        assertThat(payment.getTransactionRef()).hasSize(50);
        when(orders.findById(Long.MAX_VALUE)).thenReturn(Optional.of(order));
        when(payments.findByOrder(order)).thenReturn(Optional.of(payment));
        var query = service.findOwnedMomoAttempt(Long.MAX_VALUE, "owner");
        assertThat(query.reference()).isEqualTo(attempt.reference());
        assertThat(query.paid()).isTrue();
    }

    @Test void channelAndAmountSnapshotCannotChangeOnRetry() {
        locked(); service.reserveMomoAttempt(12L, "owner", true);
        assertThatThrownBy(() -> service.reserveMomoAttempt(12L, "owner", false)).hasMessageContaining("giao dịch MoMo");
        payment.setAmount(new BigDecimal("30000.1"));
        assertThatThrownBy(() -> service.reserveMomoAttempt(12L, "owner", true)).hasMessageContaining("chỉ hỗ trợ");
    }

    @Test void fullDiscountIsServerCalculatedWaiverWithoutGatewayAttempt() {
        order.setShippingFee(new BigDecimal("10000"));
        when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var waived = service.createForOrder(order, OrderPayment.PaymentMethod.MOMO, null, new BigDecimal("10000"));
        assertThat(waived.getAmount()).isZero();
        assertThat(waived.getStatus()).isEqualTo(OrderPayment.PaymentStatus.PAID);
        assertThat(waived.getTransactionRef()).isEqualTo("WAIVED-12");
        assertThat(waived.getPaidAt()).isNotNull();
        verifyNoInteractions(orders);
        verify(payments, never()).saveAndFlush(any());
    }
}
