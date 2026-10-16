package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.service.*;
import static org.junit.jupiter.api.Assertions.*;

/** Giao dịch thật trên bộ nhớ riêng để kiểm tra khóa, không tạo khách/đơn thử trên cloud. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:payment-concurrency-test;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop"
})
@ActiveProfiles("isolated-logistics")
class PaymentConcurrencyIntegrationTest {
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired OrderPaymentService paymentService;
    @Autowired OrderService orderService;
    Order order;
    String username;

    @BeforeEach void seed() throws Exception {
        assertIsolatedDatabase();
        var customer = new AppUser("concurrent-customer", "test-only-hash", "concurrent@test.invalid");
        customer.setRole(AppUser.Role.USER); customer.setEnabled(true); users.saveAndFlush(customer); username = customer.getUsername();
        order = new Order(); order.setTrackingCode("UTECONCURRENT01"); order.setSender(customer);
        order.setShippingFee(new BigDecimal("18000")); order.setStatus(Order.OrderStatus.PENDING_PICKUP); orders.saveAndFlush(order);
        paymentService.createForOrder(order, OrderPayment.PaymentMethod.MOMO, null, BigDecimal.ZERO);
    }

    @AfterEach void cleanup() throws Exception {
        assertIsolatedDatabase();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            history.deleteAll(); payments.deleteAll(); orders.deleteAll(); users.deleteAll();
        });
    }

    @Test void simultaneousWalletSubmissionsCreateOnlyOneSuccessfulPayment() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2); var start = new CountDownLatch(1);
        Callable<String> task = () -> {
            ready.countDown(); assertTrue(start.await(5, TimeUnit.SECONDS));
            try { return paymentService.confirmWalletPayment(order.getId(), username).getTransactionRef(); }
            catch (IllegalStateException exception) { assertTrue(exception.getMessage().contains("đã được thanh toán")); return "REJECTED"; }
        };
        try {
            var first = executor.submit(task); var second = executor.submit(task);
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter("REJECTED"::equals).count());
            var saved = payments.findByOrder(order).orElseThrow();
            assertEquals(OrderPayment.PaymentStatus.PAID, saved.getStatus());
            assertTrue(results.contains(saved.getTransactionRef())); assertEquals(1, payments.count());
        } finally { start.countDown(); executor.shutdownNow(); }
    }

    @Test void walletRequestWaitingOnCancellationCannotChargeTheCancelledOrder() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        var cancelled = new CountDownLatch(1); var allowCommit = new CountDownLatch(1); var paymentStarted = new CountDownLatch(1);
        try {
            var cancel = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                orderService.cancelOrder(order.getId(), username); cancelled.countDown();
                try { assertTrue(allowCommit.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }));
            assertTrue(cancelled.await(5, TimeUnit.SECONDS));
            var pay = executor.submit(() -> {
                paymentStarted.countDown();
                return assertThrows(IllegalStateException.class, () -> paymentService.confirmWalletPayment(order.getId(), username)).getMessage();
            });
            assertTrue(paymentStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> pay.get(150, TimeUnit.MILLISECONDS));
            allowCommit.countDown(); cancel.get(10, TimeUnit.SECONDS);
            assertTrue(pay.get(10, TimeUnit.SECONDS).contains("đã kết thúc"));
            assertEquals(Order.OrderStatus.CANCELLED, orders.findById(order.getId()).orElseThrow().getStatus());
            assertEquals(OrderPayment.PaymentStatus.UNPAID, payments.findByOrder(order).orElseThrow().getStatus());
        } finally { allowCommit.countDown(); executor.shutdownNow(); }
    }

    private void assertIsolatedDatabase() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:payment-concurrency-test"), "Chỉ được thử đồng thời trên H2 riêng.");
        }
    }
}
