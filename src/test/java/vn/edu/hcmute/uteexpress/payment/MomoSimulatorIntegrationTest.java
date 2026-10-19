package vn.edu.hcmute.uteexpress.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.persistence.*;
import vn.edu.hcmute.momosimulator.*;
import vn.edu.hcmute.uteexpress.config.MomoConfiguration;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.payment.*;
import vn.edu.hcmute.uteexpress.service.impl.MomoPaymentServiceImpl;
import vn.edu.hcmute.uteexpress.service.payment.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** App Render: persistence/service/security/template thật trên H2 riêng, không ghi Aiven. */
@SpringBootTest(classes = MomoSimulatorIntegrationTest.TestApplication.class, properties = {
    "spring.config.name=momo-simulator", "spring.datasource.url=jdbc:h2:mem:momo-render-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.open-in-view=false",
    "MOMO_SIM_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "MOMO_SIMULATOR_URL=https://uteexpress-momo-test.onrender.com"
})
@AutoConfigureMockMvc
class MomoSimulatorIntegrationTest {
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration
    @EnableJpaRepositories(basePackageClasses = MomoPaymentRepository.class)
    @Import({MomoConfiguration.class, MomoTicketCodec.class, MomoPaymentServiceImpl.class, SimulatorController.class, SimulatorErrorHandler.class})
    static class TestApplication {
        @Bean PersistenceManagedTypes managedTypes() { return PersistenceManagedTypes.of(AppUser.class.getName(), Order.class.getName(), OrderPayment.class.getName(), PromoCode.class.getName()); }
        @Bean SecurityFilterChain security(HttpSecurity http) throws Exception { return new SimulatorConfiguration().simulatorSecurity(http); }
    }
    @Autowired MomoPaymentService service;
    @Autowired MomoTicketCodec codec;
    @Autowired MomoPaymentRepository payments;
    @Autowired MomoOrderRepository orders;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @PersistenceContext EntityManager em;
    long orderId;
    String token;
    TransactionTemplate transaction;

    @BeforeEach void seed() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:mem:momo-render-tests"));
        }
        transaction = new TransactionTemplate(manager);
        transaction.executeWithoutResult(status -> {
            var user = new AppUser("momo-owner", "test-hash", "momo-owner@test.invalid"); user.setEnabled(true); em.persist(user);
            var order = new Order(); order.setSender(user); order.setTrackingCode("UTEMOMOTEST01"); order.setShippingFee(new BigDecimal("20000"));
            order.setReceiverName("Người nhận thử nghiệm"); order.setSenderName("Người gửi thử nghiệm"); order.setReceiverPhone("0901234567");
            order.setWeightKg(1.5); em.persist(order); em.flush(); orderId = order.getId();
            var payment = new OrderPayment(); payment.setOrder(order); payment.setMethod(OrderPayment.PaymentMethod.MOMO);
            payment.setAmount(new BigDecimal("18000")); payment.setDiscountAmount(new BigDecimal("2000")); em.persist(payment); em.flush();
            token = codec.issue(payment, Instant.now());
        });
    }
    @AfterEach void clean() { transaction.executeWithoutResult(status -> { em.createQuery("delete from OrderPayment").executeUpdate(); em.createQuery("delete from Order").executeUpdate(); em.createQuery("delete from AppUser").executeUpdate(); }); }

    @Test void receiptAndPaymentAreReadFromSharedDatabase() {
        var receipt = service.view(token); assertEquals("18000", receipt.amount()); assertFalse(receipt.paid());
        assertEquals("******4567", receipt.receiverPhone());
        var paid = service.confirm(token); assertTrue(paid.paid()); assertTrue(paid.transactionRef().startsWith("SIM-MOMO-"));
        assertEquals("PAID", service.status(orderId, "momo-owner").state());
        assertEquals(paid.transactionRef(), service.confirm(token).transactionRef());
    }
    @RepeatedTest(64) void qrDecodesToExactConfiguredRenderCheckoutUrl() throws Exception {
        var checkout = service.createCheckout(orderId, "momo-owner");
        byte[] bytes = Base64.getDecoder().decode(checkout.qrDataUrl().substring("data:image/png;base64,".length()));
        var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.client.j2se.BufferedImageLuminanceSource(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)))));
        assertEquals(checkout.url(), new com.google.zxing.MultiFormatReader().decode(bitmap,
                Map.of(com.google.zxing.DecodeHintType.TRY_HARDER, true,
                        com.google.zxing.DecodeHintType.POSSIBLE_FORMATS, List.of(com.google.zxing.BarcodeFormat.QR_CODE))).getText());
        assertTrue(checkout.url().startsWith("https://uteexpress-momo-test.onrender.com/momo/thanh-toan?t="));
    }
    @Test void otherAccountCannotReadOrCreatePaymentQr() {
        assertThrows(IllegalArgumentException.class, () -> service.status(orderId, "someone-else"));
        assertThrows(IllegalArgumentException.class, () -> service.createCheckout(orderId, "someone-else"));
    }
    @ParameterizedTest @EnumSource(value = Order.OrderStatus.class, names = {"CANCELLED", "RETURNED", "DELIVERED"})
    void closedOrderCannotBeCharged(Order.OrderStatus closed) {
        change(p -> p.getOrder().setStatus(closed));
        assertThrows(IllegalStateException.class, () -> service.confirm(token));
        assertEquals("UNPAID", jdbc.queryForObject("select status from order_payment where order_id=?", String.class, orderId));
    }
    @Test void changedAmountCannotUseOldTicket() { change(p -> p.setAmount(new BigDecimal("19000"))); assertThrows(IllegalArgumentException.class, () -> service.confirm(token)); }
    @Test void inconsistentDiscountAndOriginalFeeCannotBeCharged() {
        change(p -> p.setDiscountAmount(new BigDecimal("99999")));
        assertThrows(IllegalStateException.class, () -> service.confirm(token));
        assertThrows(IllegalStateException.class, () -> service.createCheckout(orderId, "momo-owner"));
    }
    @Test void zeroFeePromotionCanProduceAZeroAmountDemoReceipt() {
        change(p -> { p.setAmount(BigDecimal.ZERO); p.setDiscountAmount(new BigDecimal("20000")); });
        String freeTicket = transaction.execute(status -> codec.issue(payments.find(orderId).orElseThrow(), Instant.now()));
        assertEquals("0", service.confirm(freeTicket).amount());
    }
    @Test void paidFlagWithoutReceiptIsNotReportedAsSuccessful() {
        change(p -> p.setStatus(OrderPayment.PaymentStatus.PAID));
        assertThrows(IllegalStateException.class, () -> service.status(orderId, "momo-owner"));
        assertThrows(IllegalStateException.class, () -> service.view(token));
    }
    @Test void changedPasswordRevokesTicket() { change(p -> p.getOrder().getSender().setPassword("changed-hash")); assertThrows(IllegalArgumentException.class, () -> service.confirm(token)); }
    @Test void changedAccessVersionRevokesTicket() { change(p -> p.getOrder().getSender().revokeSessions()); assertThrows(IllegalArgumentException.class, () -> service.confirm(token)); }
    @Test void disabledAccountCannotPay() { change(p -> p.getOrder().getSender().setEnabled(false)); assertThrows(IllegalArgumentException.class, () -> service.confirm(token)); }
    @Test void lockedAccountCannotPay() { change(p -> p.getOrder().getSender().setLockedByAdmin(true)); assertThrows(IllegalArgumentException.class, () -> service.confirm(token)); }
    @Test void changedRoleCannotPay() { change(p -> p.getOrder().getSender().setRole(AppUser.Role.SHIPPER)); assertThrows(IllegalArgumentException.class, () -> service.confirm(token)); }
    @Test void codCannotUseWalletTicket() { change(p -> p.setMethod(OrderPayment.PaymentMethod.COD)); assertThrows(IllegalArgumentException.class, () -> service.confirm(token)); }
    @Test void explicitLegacyConversionKeepsAmountAndHistory() {
        change(p -> p.setMethod(OrderPayment.PaymentMethod.VNPAY));
        assertThrows(IllegalArgumentException.class, () -> service.switchLegacyPayment(orderId, "someone-else"));
        service.switchLegacyPayment(orderId, "momo-owner"); assertEquals("UNPAID", service.status(orderId, "momo-owner").state());
        assertEquals("18000", service.view(token).amount());
        service.confirm(token); assertThrows(IllegalStateException.class, () -> service.switchLegacyPayment(orderId, "momo-owner"));
    }
    @Test void paidReceiptStillReadableAfterDelivery() {
        service.confirm(token); change(p -> p.getOrder().setStatus(Order.OrderStatus.DELIVERED));
        assertTrue(service.view(token).paid()); assertTrue(service.confirm(token).paid());
    }
    @Test void expiredQrDoesNotWritePayment() {
        String expired = transaction.execute(status -> codec.issue(payments.find(orderId).orElseThrow(), Instant.now().minusSeconds(601)));
        assertThrows(IllegalArgumentException.class, () -> service.confirm(expired));
    }
    @Test void simultaneousConfirmationIsIdempotent() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        Callable<String> pay = () -> { start.await(); return service.confirm(token).transactionRef(); };
        try { var a = pool.submit(pay); var b = pool.submit(pay); start.countDown(); assertEquals(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)); }
        finally { start.countDown(); pool.shutdownNow(); }
    }
    @Test void cancellationCommittedBeforeWaitingConfirmationWins() throws Exception {
        var pool = Executors.newFixedThreadPool(2); var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        try {
            var cancel = pool.submit(() -> transaction.executeWithoutResult(status -> {
                orders.lock(orderId).orElseThrow().setStatus(Order.OrderStatus.CANCELLED); locked.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); } catch (InterruptedException exception) { throw new RuntimeException(exception); }
            }));
            assertTrue(locked.await(5, TimeUnit.SECONDS)); var pay = pool.submit(() -> assertThrows(IllegalStateException.class, () -> service.confirm(token)));
            assertThrows(TimeoutException.class, () -> pay.get(150, TimeUnit.MILLISECONDS)); release.countDown(); cancel.get(10, TimeUnit.SECONDS); pay.get(10, TimeUnit.SECONDS);
        } finally { release.countDown(); pool.shutdownNow(); }
        assertEquals("UNPAID", jdbc.queryForObject("select status from order_payment where order_id=?", String.class, orderId));
    }
    @Test void simulatorRequiresSessionAndCsrfAndRendersRealTemplate() throws Exception {
        var opened = mvc.perform(get("/momo/thanh-toan").param("t", token)).andExpect(status().is3xxRedirection()).andReturn();
        String page = opened.getResponse().getRedirectedUrl(); var session = (MockHttpSession) opened.getRequest().getSession(false);
        assertFalse(page.contains(token));
        var rendered = mvc.perform(get(page).session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("UTEMOMOTEST01"))).andReturn();
        if (Boolean.getBoolean("momo.export-preview")) java.nio.file.Files.writeString(java.nio.file.Path.of("target/momo-checkout-preview.html"),
                rendered.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(get(page)).andExpect(status().isBadRequest());
        mvc.perform(post(page + "/xac-nhan").session(session).param("simulationAcknowledged", "true")).andExpect(status().isForbidden());
        mvc.perform(post(page + "/xac-nhan").session(session).with(csrf())).andExpect(status().isBadRequest());
        mvc.perform(post(page + "/xac-nhan").session(session).with(csrf()).param("simulationAcknowledged", "true")
                .param("amount", "1").param("orderId", "999")).andExpect(status().is3xxRedirection());
        assertEquals("PAID", service.status(orderId, "momo-owner").state());
        mvc.perform(get(page).session(session)).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Thanh toán thành công")));
    }
    @Test void closingSessionDoesNotPretendToCancelOrPayOrder() throws Exception {
        var opened = mvc.perform(get("/momo/thanh-toan").param("t", token)).andReturn();
        String page = opened.getResponse().getRedirectedUrl(); var session = (MockHttpSession) opened.getRequest().getSession(false);
        mvc.perform(post(page + "/huy").session(session).with(csrf())).andExpect(status().isOk());
        assertEquals("UNPAID", service.status(orderId, "momo-owner").state());
    }
    @Test void standaloneAppDoesNotExposeMainWebsite() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk());
        mvc.perform(get("/admin/tai-khoan")).andExpect(status().isForbidden());
        mvc.perform(get("/nguoi-dung/don-hang")).andExpect(status().isForbidden());
        mvc.perform(get("/api/payments/momo/" + orderId + "/status")).andExpect(status().isForbidden());
        mvc.perform(get("/momo/thanh-toan").param("t", "forged")).andExpect(status().isBadRequest());
    }
    private void change(java.util.function.Consumer<OrderPayment> mutation) { transaction.executeWithoutResult(status -> mutation.accept(payments.find(orderId).orElseThrow())); }
}
