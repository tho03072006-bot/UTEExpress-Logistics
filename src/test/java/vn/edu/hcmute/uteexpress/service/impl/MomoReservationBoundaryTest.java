package vn.edu.hcmute.uteexpress.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;
import vn.edu.hcmute.uteexpress.config.MomoProperties;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderPaymentRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.MomoApiClient;
import vn.edu.hcmute.uteexpress.service.MomoPaymentService;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MomoReservationBoundaryTest {
    @Test void timeoutRetainsCommittedReferenceAndReusesItOnRetry() {
        var orders = mock(OrderRepository.class); var payments = mock(OrderPaymentRepository.class);
        var users = mock(AppUserRepository.class); var api = mock(MomoApiClient.class);
        AppUser owner = new AppUser(); owner.setUsername("owner");
        Order order = new Order(); order.setId(12L); order.setSender(owner); order.setTrackingCode("UTE0012");
        order.setStatus(Order.OrderStatus.PENDING_PICKUP);
        OrderPayment payment = new OrderPayment(); payment.setOrder(order); payment.setMethod(OrderPayment.PaymentMethod.MOMO);
        payment.setAmount(new BigDecimal("30000"));
        when(orders.findByIdForUpdate(12L)).thenReturn(Optional.of(order));
        when(payments.findByOrderForUpdate(order)).thenReturn(Optional.of(payment));
        List<String> events = new ArrayList<>();
        when(payments.saveAndFlush(payment)).thenAnswer(invocation -> { events.add("save"); return payment; });
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> { events.add("begin"); return new SimpleTransactionStatus(); });
        doAnswer(invocation -> { events.add("commit"); return null; }).when(transactions).commit(any());
        ProxyFactory proxy = new ProxyFactory(new OrderPaymentServiceImpl(payments, orders, users));
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var ledger = (OrderPaymentService) proxy.getProxy();
        var properties = new MomoProperties(true, MomoProperties.TEST_ENDPOINT, "TEST", "access", "secret", "http://localhost:8080");
        var service = new MomoPaymentService(properties, api, ledger);
        List<String> references = new ArrayList<>();
        when(api.create(any(), eq(true))).thenAnswer(invocation -> {
            var attempt = (vn.edu.hcmute.uteexpress.dto.MomoPaymentAttempt) invocation.getArgument(0);
            references.add(attempt.reference()); events.add("http-timeout");
            // Provider may have received the request despite this timeout; the ledger must retain its ID.
            throw new IllegalStateException("MoMo timeout");
        });
        assertThatThrownBy(() -> service.start(12L, "owner", true)).hasMessage("MoMo timeout");
        assertThat(payment.getStatus()).isEqualTo(OrderPayment.PaymentStatus.UNPAID);
        assertThatThrownBy(() -> service.start(12L, "owner", true)).hasMessage("MoMo timeout");
        assertThat(references).hasSize(2); assertThat(references.get(0)).isEqualTo(references.get(1));
        assertThat(events).containsExactly("begin", "save", "commit", "http-timeout", "begin", "commit", "http-timeout");
        verify(payments, times(1)).saveAndFlush(payment);
        verify(payments, never()).save(any());
        verify(transactions, never()).rollback(any());
    }

    @Test void signedIpnCanSettleBeforeCreateHttpResponseReturns() {
        var orders = mock(OrderRepository.class); var payments = mock(OrderPaymentRepository.class);
        var users = mock(AppUserRepository.class); var api = mock(MomoApiClient.class);
        AppUser owner = new AppUser(); owner.setUsername("owner");
        Order order = new Order(); order.setId(12L); order.setSender(owner); order.setTrackingCode("UTE0012");
        order.setStatus(Order.OrderStatus.PENDING_PICKUP);
        OrderPayment payment = new OrderPayment(); payment.setOrder(order); payment.setMethod(OrderPayment.PaymentMethod.MOMO);
        payment.setAmount(new BigDecimal("30000"));
        when(orders.findByIdForUpdate(12L)).thenReturn(Optional.of(order));
        when(payments.findByOrderForUpdate(order)).thenReturn(Optional.of(payment));
        when(payments.saveAndFlush(payment)).thenReturn(payment); when(payments.save(payment)).thenReturn(payment);
        List<String> events = new ArrayList<>();
        var transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenAnswer(invocation -> { events.add("begin"); return new SimpleTransactionStatus(); });
        doAnswer(invocation -> { events.add("commit"); return null; }).when(transactions).commit(any());
        ProxyFactory proxy = new ProxyFactory(new OrderPaymentServiceImpl(payments, orders, users));
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        var ledger = (OrderPaymentService) proxy.getProxy();
        var properties = new MomoProperties(true, MomoProperties.TEST_ENDPOINT, "TEST", "access", "secret", "http://localhost:8080");
        var service = new MomoPaymentService(properties, api, ledger);
        when(api.create(any(), eq(true))).thenAnswer(invocation -> {
            var attempt = (vn.edu.hcmute.uteexpress.dto.MomoPaymentAttempt) invocation.getArgument(0);
            assertThat(events).containsExactly("begin", "commit");
            assertThat(payment.getTransactionRef()).isEqualTo(attempt.reference());
            Map<String, String> callback = new HashMap<>();
            callback.put("partnerCode", "TEST"); callback.put("orderId", attempt.reference()); callback.put("requestId", attempt.reference());
            callback.put("amount", "30000"); callback.put("extraData", ""); callback.put("message", "Successful.");
            callback.put("orderInfo", attempt.orderInfo()); callback.put("orderType", "momo_wallet"); callback.put("payType", "qr");
            callback.put("responseTime", "1791768000000"); callback.put("resultCode", "0"); callback.put("transId", "1234567890");
            callback.put("signature", properties.sign(MomoPaymentService.callbackRawSignature("access", callback)));
            events.add("ipn-before-http-response");
            assertThat(service.handleIpn(callback).state()).isEqualTo(vn.edu.hcmute.uteexpress.dto.MomoPaymentResult.State.PAID);
            assertThat(payment.getStatus()).isEqualTo(OrderPayment.PaymentStatus.PAID);
            events.add("http-response");
            return new vn.edu.hcmute.uteexpress.dto.MomoCheckout(12L, attempt.reference(), 30000,
                    "https://test-payment.momo.vn/v2/gateway/pay?t=test", "momo://test");
        });
        service.start(12L, "owner", true);
        assertThat(events).containsExactly("begin", "commit", "ipn-before-http-response", "begin", "commit", "http-response");
        assertThat(payment.getTransactionRef()).endsWith(".1234567890");
    }
}
