package vn.edu.hcmute.uteexpress.service.impl;

import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;
import vn.edu.hcmute.uteexpress.entity.PromoCode;
import vn.edu.hcmute.uteexpress.repository.AppUserRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.service.OrderPaymentService;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.PromoCodeService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Checks the real Spring transaction interceptor rather than calling the annotated method directly. */
class OrderCreationTransactionTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final OrderPaymentService payments = mock(OrderPaymentService.class);
    private final PromoCodeService promos = mock(PromoCodeService.class);
    private final RecordingTransactions transactions = new RecordingTransactions();
    private final OrderService service = createProxy();

    @Test void paymentFailureRollsBackTheWholeCreateTransaction() {
        prepareOrder();
        when(payments.createForOrder(any(), any(), any(), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("payment unavailable");
        });
        assertThatThrownBy(() -> service.createOrder(request(), "sender"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("payment unavailable");
        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
        verify(orders).save(any());
        verify(promos, never()).markUsed(any());
    }

    @Test void promoFailureAfterPaymentAlsoRollsBackTheOuterTransaction() {
        PromoCode promo = preparePromo();
        when(payments.createForOrder(any(), any(), eq(promo), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return new OrderPayment();
        });
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            throw new IllegalStateException("quota exhausted");
        }).when(promos).markUsed(promo);
        OrderCreateRequest request = request();
        request.setPromoCode("SAVE10");
        assertThatThrownBy(() -> service.createOrder(request, "sender")).hasMessageContaining("quota exhausted");
        assertThat(transactions.commits).isZero();
        assertThat(transactions.rollbacks).isEqualTo(1);
        verify(payments).createForOrder(any(), any(), eq(promo), any());
    }

    @Test void validOrderPaymentAndPromoShareOneCommit() {
        PromoCode promo = preparePromo();
        when(payments.createForOrder(any(), any(), eq(promo), any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return new OrderPayment();
        });
        doAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return null;
        }).when(promos).markUsed(promo);
        OrderCreateRequest request = request();
        request.setPromoCode("save10");
        Order created = service.createOrder(request, "sender");
        assertThat(created.getShippingFee()).isEqualByComparingTo("18000").hasScaleOf(0);
        assertThat(transactions.commits).isEqualTo(1);
        assertThat(transactions.rollbacks).isZero();
        verify(users).findByUsernameForUpdate("sender");
    }

    @Test void invalidDirectServiceRequestCannotWriteAnything() {
        OrderCreateRequest request = request();
        request.setReceiverPhone("abc");
        assertThatThrownBy(() -> service.createOrder(request, "sender")).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(orders, users, payments, promos);
    }

    @Test void cancellationChecksLatestStatusFromTheLockedRow() {
        AppUser owner = new AppUser();
        owner.setUsername("sender");
        Order order = new Order();
        order.setSender(owner);
        order.setStatus(Order.OrderStatus.PICKED_UP);
        when(orders.findByIdForUpdate(7L)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            return Optional.of(order);
        });
        assertThatThrownBy(() -> service.cancelOrder(7L, "sender")).hasMessageContaining("chưa tới lấy");
        verify(orders, never()).findById(any());
        verify(orders, never()).save(any());
    }

    private PromoCode preparePromo() {
        prepareOrder();
        PromoCode promo = new PromoCode();
        promo.setCode("SAVE10");
        when(promos.requireUsableCode(eq("SAVE10"), any())).thenReturn(promo);
        when(promos.calculateDiscount(eq(promo), any())).thenReturn(BigDecimal.valueOf(1000));
        return promo;
    }

    private void prepareOrder() {
        AppUser sender = new AppUser();
        sender.setUsername("sender");
        when(users.findByUsernameForUpdate("sender")).thenReturn(Optional.of(sender));
        when(orders.save(any())).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            Order order = call.getArgument(0);
            order.setId(10L);
            return order;
        });
    }

    private OrderService createProxy() {
        ProxyFactory factory = new ProxyFactory(new OrderServiceImpl(orders, users, null, payments, promos));
        factory.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return (OrderService) factory.getProxy();
    }

    private static OrderCreateRequest request() {
        OrderCreateRequest request = new OrderCreateRequest();
        request.setSenderName("Người gửi");
        request.setSenderAddress("1 Võ Văn Ngân");
        request.setReceiverName("Người nhận");
        request.setReceiverAddress("2 Nguyễn Trãi");
        request.setReceiverPhone("0901234567");
        request.setWeightKg(1.0);
        request.setServiceType(Order.ServiceType.STANDARD);
        return request;
    }

    private static class RecordingTransactions extends AbstractPlatformTransactionManager {
        int commits;
        int rollbacks;
        @Override protected Object doGetTransaction() { return new Object(); }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {}
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
    }
}
