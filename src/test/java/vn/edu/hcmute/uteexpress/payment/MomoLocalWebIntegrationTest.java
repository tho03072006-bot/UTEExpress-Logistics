package vn.edu.hcmute.uteexpress.payment;

import java.math.BigDecimal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.security.admin.PublicAccountPrincipal;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:momo-local-tests;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.hibernate.ddl-auto=create-drop",
    "MOMO_SIM_SECRET=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=", "MOMO_SIMULATOR_URL=https://uteexpress-momo-test.onrender.com"
})
@ActiveProfiles("isolated-logistics") @AutoConfigureMockMvc @Transactional
class MomoLocalWebIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    AppUser owner, other, shipper;
    Order order;
    OrderPayment payment;
    @BeforeEach void seed() {
        owner = account("momo-owner", AppUser.Role.USER); other = account("momo-other", AppUser.Role.USER); shipper = account("momo-shipper", AppUser.Role.SHIPPER);
        order = new Order(); order.setTrackingCode("UTEMOMOLOCAL01"); order.setSender(owner); order.setReceiverName("Người nhận thử nghiệm");
        order.setShippingFee(new BigDecimal("18000")); order.setWeightKg(1.0); orders.saveAndFlush(order);
        payment = new OrderPayment(); payment.setOrder(order); payment.setMethod(OrderPayment.PaymentMethod.MOMO); payment.setAmount(new BigDecimal("18000")); payments.saveAndFlush(payment);
    }
    @Test void ownerSeesOnlyQrAndRegenerateControlWithAutomaticStatusChecks() throws Exception {
        var result = mvc.perform(get("/nguoi-dung/thanh-toan/" + order.getId()).with(user(new PublicAccountPrincipal(owner))))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("src=\"data:image/png;base64,")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-status-url=\"/api/payments/momo/" + order.getId() + "/status\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-momo-check"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Mở MoMo mô phỏng"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("https://uteexpress-momo-test.onrender.com"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Xác nhận đã thanh toán"))))
                .andReturn();
        String html = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String qrPanel = html.substring(html.indexOf("data-momo-payment"), html.indexOf("</section>", html.indexOf("data-momo-payment")));
        assertEquals(1L, java.util.regex.Pattern.compile("<a\\s").matcher(qrPanel).results().count());
        assertFalse(qrPanel.contains("<button"));
        assertTrue(qrPanel.contains("href=\"/nguoi-dung/thanh-toan/" + order.getId() + "\""));
        assertTrue(qrPanel.contains("data-momo-refresh"));
        // Removing the clickable link must not remove the actual Render address encoded in the QR.
        var checkout = (vn.edu.hcmute.uteexpress.service.payment.MomoPaymentService.Checkout) result.getModelAndView().getModel().get("checkout");
        byte[] bytes = java.util.Base64.getDecoder().decode(checkout.qrDataUrl().substring("data:image/png;base64,".length()));
        var bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
                new com.google.zxing.client.j2se.BufferedImageLuminanceSource(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(bytes)))));
        assertEquals(checkout.url(), new com.google.zxing.MultiFormatReader().decode(bitmap,
                java.util.Map.of(com.google.zxing.DecodeHintType.TRY_HARDER, true)).getText());
        assertTrue(checkout.url().startsWith("https://uteexpress-momo-test.onrender.com/momo/thanh-toan?t="));
    }
    @Test void onlyOwnerCanPollFreshDatabaseState() throws Exception {
        String url = "/api/payments/momo/" + order.getId() + "/status";
        mvc.perform(get(url).with(user(new PublicAccountPrincipal(owner)))).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.state").value("UNPAID")).andExpect(jsonPath("$.render.state").value("DISABLED"));
        mvc.perform(get(url).with(user(new PublicAccountPrincipal(other)))).andExpect(status().isNotFound());
        mvc.perform(get(url).with(user(new PublicAccountPrincipal(shipper)))).andExpect(status().isForbidden());
        mvc.perform(get(url).accept("application/json")).andExpect(status().isUnauthorized());
        payment.setStatus(OrderPayment.PaymentStatus.PAID); payment.setTransactionRef("SIM-MOMO-TEST"); payment.setPaidAt(java.time.LocalDateTime.now()); payments.flush();
        mvc.perform(get(url).with(user(new PublicAccountPrincipal(owner)))).andExpect(jsonPath("$.state").value("PAID"));
    }
    @Test void removedLocalConfirmationCannotMarkPaid() throws Exception {
        mvc.perform(post("/nguoi-dung/thanh-toan/" + order.getId() + "/xac-nhan").with(user(new PublicAccountPrincipal(owner))).with(csrf()))
                .andExpect(status().isNotFound());
        assertEquals(OrderPayment.PaymentStatus.UNPAID, payment.getStatus());
    }
    @Test void invalidReceiptIsADataConflictWithoutLeakingItOrMisreportingAccessDenial() throws Exception {
        payment.setStatus(OrderPayment.PaymentStatus.PAID); payments.flush();
        mvc.perform(get("/api/payments/momo/" + order.getId() + "/status").with(user(new PublicAccountPrincipal(owner))))
                .andExpect(status().isConflict()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.state").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.transactionRef").doesNotExist())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Manager")));
        assertEquals(OrderPayment.PaymentStatus.PAID, payment.getStatus());
    }
    @Test void legacyConversionRequiresOwnerAndCsrf() throws Exception {
        payment.setMethod(OrderPayment.PaymentMethod.VNPAY); payments.flush();
        String url = "/nguoi-dung/thanh-toan/" + order.getId() + "/doi-sang-momo";
        mvc.perform(post(url).with(user(new PublicAccountPrincipal(owner)))).andExpect(status().isForbidden());
        mvc.perform(post(url).with(user(new PublicAccountPrincipal(other))).with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals(OrderPayment.PaymentMethod.VNPAY, payment.getMethod());
        mvc.perform(post(url).with(user(new PublicAccountPrincipal(owner))).with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals(OrderPayment.PaymentMethod.MOMO, payment.getMethod());
    }
    @Test void disabledOwnerSessionGets401NotAnHtmlLoginResponse() throws Exception {
        var principal = new PublicAccountPrincipal(owner); owner.setLockedByAdmin(true); users.flush();
        mvc.perform(get("/api/payments/momo/" + order.getId() + "/status").with(user(principal)))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }
    @Test void newOrderDtoRejectsVnpayButAcceptsCodAndMomo() {
        var request = new vn.edu.hcmute.uteexpress.dto.OrderCreateRequest(); request.setPaymentMethod(OrderPayment.PaymentMethod.VNPAY);
        assertFalse(request.isPaymentMethodSupported()); request.setPaymentMethod(OrderPayment.PaymentMethod.MOMO); assertTrue(request.isPaymentMethodSupported());
        request.setPaymentMethod(OrderPayment.PaymentMethod.COD); assertTrue(request.isPaymentMethodSupported());
    }
    private AppUser account(String name, AppUser.Role role) { var user = new AppUser(name, "test-hash", name + "@test.invalid"); user.setRole(role); user.setEnabled(true); return users.saveAndFlush(user); }
}
