package vn.edu.hcmute.uteexpress.pricing;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.service.*;
import vn.edu.hcmute.uteexpress.service.geo.GeoPoint;
import vn.edu.hcmute.uteexpress.support.FixedAddressGeocoder;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Cước có phụ phí khoảng cách đường chim bay; tọa độ giả (không gọi ArcGIS), H2 riêng. */
@SpringBootTest(classes = vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:order-distance;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.mail.order-updates.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("isolated-logistics")
class OrderDistancePricingIntegrationTest {
    static final AtomicInteger SEQUENCE = new AtomicInteger();
    static final String THU_DUC = "1 Võ Văn Ngân, Thủ Đức, Hồ Chí Minh", AN_DINH = "112 Tân Hòa, An Định, Vĩnh Long",
            QUAN_10 = "268 Lý Thường Kiệt, Quận 10, Hồ Chí Minh";
    @Autowired MockMvc mvc;
    @Autowired FixedAddressGeocoder geocoder;
    @Autowired OrderService orderService;
    @Autowired OrderDraftService drafts;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    AppUser customer;

    @BeforeEach void seed() {
        geocoder.clear();
        geocoder.register(THU_DUC, new GeoPoint(10.849742, 106.770242));
        geocoder.register(AN_DINH, new GeoPoint(10.07521, 106.372626));
        geocoder.register(QUAN_10, new GeoPoint(10.771727, 106.657845));
        String name = "distance" + SEQUENCE.incrementAndGet();
        var user = new AppUser(name, encoder.encode("OriginalPass123"), name + "@test.invalid");
        user.setEnabled(true); user.setRole(AppUser.Role.USER); user.setFullName("Nguyễn Hữu Tài"); user.setPhone("0901234567");
        customer = users.saveAndFlush(user);
    }
    @AfterEach void reset() { geocoder.clear(); }

    OrderCreateRequest form(String from, String to, Order.ServiceType service) {
        var form = orderService.prepareCreateForm(customer.getUsername()); // có mã phiên biểu mẫu như form thật
        form.setSenderName("Nguyễn Hữu Tài"); form.setSenderAddress(from);
        form.setReceiverName("Nguyễn Văn Lộc"); form.setReceiverPhone("0332523252"); form.setReceiverAddress(to);
        form.setWeightKg(1d); form.setServiceType(service); form.setPaymentMethod(OrderPayment.PaymentMethod.COD);
        return form;
    }

    @Test void farOrderStoresDistanceAndChargesServiceFeePlusDistanceFee() {
        var order = orderService.createOrder(form(THU_DUC, AN_DINH, Order.ServiceType.STANDARD), customer.getUsername());
        var saved = orders.findById(order.getId()).orElseThrow();
        // Thủ Đức -> An Định (Vĩnh Long) khoảng 96 km đường chim bay.
        assertTrue(saved.getDistanceKm().doubleValue() > 94 && saved.getDistanceKm().doubleValue() < 98, saved.getDistanceKm().toPlainString());
        var expectedDistanceFee = FeeEstimateService.distanceFee(saved.getDistanceKm().doubleValue(), Order.ServiceType.STANDARD);
        assertEquals(0, expectedDistanceFee.compareTo(saved.getDistanceFee()));
        assertTrue(saved.getDistanceFee().compareTo(new BigDecimal("40000")) > 0);
        // Tiêu chuẩn 1 kg ở bảng giá test: 15.000 + 3.000.
        assertEquals(0, new BigDecimal("18000").add(saved.getDistanceFee()).compareTo(saved.getShippingFee()));
        assertEquals(0, new BigDecimal("18000").compareTo(saved.getServiceFee()));
        var payment = payments.findByOrder(saved).orElseThrow();
        assertEquals(0, saved.getShippingFee().compareTo(payment.getAmount()), "Tiền thu phải gồm cả phụ phí khoảng cách");
    }
    @Test void nearbyOrderUnderFiveKilometresHasNoDistanceFee() {
        var order = orderService.createOrder(form(THU_DUC, THU_DUC, Order.ServiceType.EXPRESS), customer.getUsername());
        var saved = orders.findById(order.getId()).orElseThrow();
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getDistanceKm()));
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.getDistanceFee()));
        assertEquals(0, new BigDecimal("28000").compareTo(saved.getShippingFee()));
    }
    @Test void superExpressBeyondThirtyKilometresIsRejectedWithoutCreatingOrder() {
        long before = orders.count();
        var error = assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(form(THU_DUC, AN_DINH, Order.ServiceType.SUPER_EXPRESS), customer.getUsername()));
        assertTrue(error.getMessage().contains("Hỏa tốc chỉ nhận đơn trong bán kính 30 km"));
        assertEquals(before, orders.count());
        // Trong thành phố (Thủ Đức -> Quận 10, khoảng 15 km) thì Hỏa tốc vẫn nhận, có phụ phí x1,6.
        var city = orders.findById(orderService.createOrder(form(THU_DUC, QUAN_10, Order.ServiceType.SUPER_EXPRESS), customer.getUsername()).getId()).orElseThrow();
        assertTrue(city.getDistanceKm().doubleValue() > 10 && city.getDistanceKm().doubleValue() < 20);
        assertEquals(0, FeeEstimateService.distanceFee(city.getDistanceKm().doubleValue(), Order.ServiceType.SUPER_EXPRESS).compareTo(city.getDistanceFee()));
    }
    @Test void unknownAddressBlocksOrderAndCartWithClearMessage() {
        long before = orders.count();
        var error = assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(form(THU_DUC, "KHONG-TIM-THAY xã gì đó", Order.ServiceType.STANDARD), customer.getUsername()));
        assertTrue(error.getMessage().contains("địa chỉ giao hàng"), error.getMessage());
        var pickup = assertThrows(IllegalStateException.class,
                () -> drafts.addDraft(form("KHONG-TIM-THAY", AN_DINH, Order.ServiceType.STANDARD), customer.getUsername()));
        assertTrue(pickup.getMessage().contains("địa chỉ lấy hàng"), pickup.getMessage());
        var outage = assertThrows(IllegalStateException.class,
                () -> orderService.createOrder(form(THU_DUC, "BAN-DO-LOI", Order.ServiceType.STANDARD), customer.getUsername()));
        assertTrue(outage.getMessage().contains("dịch vụ bản đồ"));
        assertEquals(before, orders.count());
        assertEquals(0, drafts.countDrafts(customer.getUsername()));
    }
    @Test void cartEstimateIncludesDistanceFee() {
        var draft = drafts.addDraft(form(THU_DUC, AN_DINH, Order.ServiceType.STANDARD), customer.getUsername());
        assertTrue(draft.getEstimatedFee().compareTo(new BigDecimal("58000")) > 0, draft.getEstimatedFee().toPlainString());
    }
    @Test void liveQuoteEndpointNeedsLoginAndCsrfAndNeverTrustsBrowserNumbers() throws Exception {
        var request = post("/nguoi-dung/tao-don/bao-gia").param("senderAddress", THU_DUC).param("receiverAddress", AN_DINH)
                .param("serviceType", "STANDARD").param("weightKg", "1");
        mvc.perform(request.with(user(customer.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(csrf()).param("senderAddress", THU_DUC).param("receiverAddress", AN_DINH)
                .param("serviceType", "STANDARD").param("weightKg", "1")).andExpect(status().is4xxClientError());
        mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("senderAddress", THU_DUC).param("receiverAddress", AN_DINH).param("serviceType", "STANDARD").param("weightKg", "1")
                .param("distanceFee", "0").param("total", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.distanceKm", greaterThan(94.0))).andExpect(jsonPath("$.baseFee").value(18000))
            .andExpect(jsonPath("$.distanceFee", greaterThan(40000))).andExpect(header().string("Cache-Control", containsString("no-store")));
        mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("senderAddress", "KHONG-TIM-THAY").param("receiverAddress", AN_DINH).param("serviceType", "STANDARD").param("weightKg", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false)).andExpect(jsonPath("$.message", containsString("địa chỉ lấy hàng")));
        mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("senderAddress", THU_DUC).param("receiverAddress", AN_DINH).param("serviceType", "STANDARD").param("weightKg", "500"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));
    }
    @Test void orderFormShowsLocationErrorInsteadOfCrashing() throws Exception {
        var form = orderService.prepareCreateForm(customer.getUsername());
        long before = orders.count();
        mvc.perform(post("/nguoi-dung/tao-don").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("submissionToken", form.getSubmissionToken()).param("senderName", "Nguyễn Hữu Tài").param("senderAddress", THU_DUC)
                .param("receiverName", "Nguyễn Văn Lộc").param("receiverAddress", "KHONG-TIM-THAY ấp 3").param("receiverPhone", "0332523252")
                .param("weightKg", "1").param("serviceType", "STANDARD").param("paymentMethod", "COD"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Không xác định được vị trí địa chỉ giao hàng")));
        assertEquals(before, orders.count());
    }
    @Test void orderDetailShowsDistanceBreakdown() throws Exception {
        var order = orderService.createOrder(form(THU_DUC, AN_DINH, Order.ServiceType.STANDARD), customer.getUsername());
        mvc.perform(get("/nguoi-dung/don-hang/" + order.getId()).with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("phụ phí khoảng cách")))
            .andExpect(content().string(containsString("km đường chim bay")));
    }
}
