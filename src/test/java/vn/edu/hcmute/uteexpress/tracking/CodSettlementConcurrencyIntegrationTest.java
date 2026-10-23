package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.service.tracking.*;
import vn.edu.hcmute.uteexpress.support.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Mặc định REPEATABLE_READ giống MySQL; ghi/commit chỉ trên H2 in-memory đã có guard. */
@SpringBootTest(classes=IsolatedLogisticsTestConfiguration.class, properties={
    "spring.datasource.url=jdbc:h2:mem:cod-race-audit;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.hikari.transaction-isolation=TRANSACTION_REPEATABLE_READ",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.mail.order-updates.enabled=false"})
@ActiveProfiles("isolated-logistics")
class CodSettlementConcurrencyIntegrationTest {
    static final AtomicInteger SEQUENCE=new AtomicInteger();
    @Autowired ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired CodSettlementRepository settlements;
    @Autowired CodSettlementService cod;
    @SpyBean LogisticsAccess access;
    AppUser shipper, customer;

    @BeforeEach void seed() {
        RoutingFixtures.seedH2(context);
        shipper=account(AppUser.Role.SHIPPER); customer=account(AppUser.Role.USER);
    }
    AppUser account(AppUser.Role role) {
        String name="codrace"+SEQUENCE.incrementAndGet(); var user=new AppUser(name,"test-only-hash",name+"@test.invalid");
        user.setEnabled(true);user.setRole(role);return users.saveAndFlush(user);
    }
    Order delivered() {
        var order=new Order();order.setSender(customer);order.setShipper(shipper);order.setTrackingCode("UTECR"+SEQUENCE.incrementAndGet());
        order.setStatus(Order.OrderStatus.DELIVERED);order.setServiceType(Order.ServiceType.STANDARD);order.setWeightKg(1d);
        order.setShippingFee(new BigDecimal("18000"));order.setSenderName("Người Gửi");order.setReceiverName("Người Nhận");order.setReceiverAddress("Địa chỉ thử");
        orders.saveAndFlush(order);
        var payment=new OrderPayment();payment.setOrder(order);payment.setMethod(OrderPayment.PaymentMethod.COD);
        payment.setStatus(OrderPayment.PaymentStatus.PAID);payment.setAmount(new BigDecimal("18000"));payment.setPaidAt(LocalDateTime.now());payments.saveAndFlush(payment);
        history.saveAndFlush(new OrderStatusHistory(order,shipper,Order.OrderStatus.IN_TRANSIT,Order.OrderStatus.DELIVERED,null,"Đã giao",null,LocalDateTime.now()));
        return order;
    }

    @Test void simultaneousSubmissionsForDifferentOrdersStillCreateOnlyOnePendingCashForm() throws Exception {
        var first=delivered();var second=delivered();
        var barrier=new CyclicBarrier(2);
        doAnswer(call->{var result=call.callRealMethod();barrier.await(10,TimeUnit.SECONDS);return result;}).when(access).shipper(anyString());
        var executor=Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(()->submit(first));var b=executor.submit(()->submit(second));
            assertEquals(1,a.get(15,TimeUnit.SECONDS)+b.get(15,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();reset(access);}
        assertEquals(1,settlements.findByShipper(shipper,org.springframework.data.domain.PageRequest.of(0,10)).getTotalElements());
    }
    int submit(Order order) {
        try {cod.submit(shipper.getUsername(),List.of(order.getId()),new BigDecimal("18000"),"Nộp đủ cước đã thu");return 1;}
        catch(IllegalArgumentException ex){assertTrue(ex.getMessage().contains("phiếu chờ"));return 0;}
    }
}
