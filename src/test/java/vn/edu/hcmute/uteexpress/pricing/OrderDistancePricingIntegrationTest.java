package vn.edu.hcmute.uteexpress.pricing;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository;
import vn.edu.hcmute.uteexpress.service.*;
import vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService;
import vn.edu.hcmute.uteexpress.support.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Hồi quy bảng cước sau khi ghép luồng tỉnh/khu vực: dùng chung tuyến cho báo giá, đơn và giỏ. Chỉ H2. */
@SpringBootTest(classes=IsolatedLogisticsTestConfiguration.class, properties={
    "spring.datasource.url=jdbc:h2:mem:order-distance;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.mail.order-updates.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("isolated-logistics")
class OrderDistancePricingIntegrationTest {
    static final AtomicInteger SEQUENCE=new AtomicInteger();
    @Autowired ApplicationContext context;
    @Autowired MockMvc mvc;
    @Autowired OrderService orderService;
    @Autowired OrderDraftService drafts;
    @Autowired OrderRoutingService routing;
    @Autowired OrderRouteRepository routes;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @MockBean(name="isolatedGeocoder") FixedAddressGeocoder geocoder;
    AppUser customer;

    @BeforeEach void seed() {
        RoutingFixtures.seedH2(context);
        String name="routeprice"+SEQUENCE.incrementAndGet();
        var user=new AppUser(name,encoder.encode("OriginalPass123"),name+"@test.invalid");
        user.setEnabled(true);user.setRole(AppUser.Role.USER);user.setFullName("Nguyễn Hữu Tài");user.setPhone("0901234567");
        customer=users.saveAndFlush(user);
    }
    OrderCreateRequest form(boolean inter,Order.ServiceType service) {
        var request=orderService.prepareCreateForm(customer.getUsername());
        request.setSenderName("Nguyễn Hữu Tài");request.setSenderAddress("1 Võ Văn Ngân");
        request.setReceiverName("Nguyễn Văn Lộc");request.setReceiverPhone("0332523252");request.setReceiverAddress("54 Nguyễn Văn Linh");
        request.setWeightKg(1d);request.setServiceType(service);RoutingFixtures.fill(request);
        if(inter){request.setDeliveryProvinceCode("48");request.setDeliveryAreaCode("DAD-HAICHAU");}
        return request;
    }
    MockHttpServletRequestBuilder quote(String weight) {
        return post("/nguoi-dung/tao-don/bao-gia").param("senderAddress","1 Võ Văn Ngân").param("receiverAddress","54 Nguyễn Văn Linh")
            .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC")
            .param("deliveryProvinceCode","48").param("deliveryAreaCode","DAD-HAICHAU")
            .param("serviceType","STANDARD").param("weightKg",weight);
    }
    @Test void interprovincialOrderAndPaymentUseRouteQuoteNotClientPrices() {
        var request=form(true,Order.ServiceType.STANDARD);var expected=routing.quote(request);
        var order=orderService.createOrder(request,customer.getUsername());
        assertEquals(0,expected.total().compareTo(order.getShippingFee()));
        assertTrue(expected.routeFee().signum()>0);assertEquals(0,expected.distanceFee().signum());
        assertEquals(0,expected.total().compareTo(payments.findByOrder(order).orElseThrow().getAmount()));
        var route=routes.findByOrder_Id(order.getId()).orElseThrow();
        assertTrue(route.isInterprovincial());
        assertEquals(expected.pickupArea().getBranch().getId(),route.getOriginBranch().getId());
        assertEquals(expected.deliveryArea().getBranch().getId(),route.getDestBranch().getId());
        verifyNoInteractions(geocoder);
    }
    @Test void sameAreaHasNoDistanceOrRouteSurcharge() {
        var request=form(false,Order.ServiceType.EXPRESS);var price=routing.quote(request);
        var order=orderService.createOrder(request,customer.getUsername());
        assertEquals(0,price.distanceFee().signum());assertEquals(0,price.routeFee().signum());
        assertEquals(0,new BigDecimal("28000").compareTo(order.getShippingFee()));
    }
    @Test void superExpressCannotCrossBranchesButWorksInsideSameArea() {
        long before=orders.count();
        var error=assertThrows(IllegalArgumentException.class,()->orderService.createOrder(form(true,Order.ServiceType.SUPER_EXPRESS),customer.getUsername()));
        assertTrue(error.getMessage().contains("Hỏa tốc"));
        assertEquals(before,orders.count());assertNotNull(orderService.createOrder(form(false,Order.ServiceType.SUPER_EXPRESS),customer.getUsername()).getId());
    }
    @Test void unknownAreaAndProvinceMismatchNeverCreateOrdersOrDrafts() {
        long before=orders.count();var request=form(false,Order.ServiceType.STANDARD);
        request.setDeliveryAreaCode("UNKNOWN");assertThrows(IllegalArgumentException.class,()->orderService.createOrder(request,customer.getUsername()));
        request.setDeliveryAreaCode("SGN-THUDUC");request.setDeliveryProvinceCode("48");
        assertThrows(IllegalArgumentException.class,()->drafts.addDraft(request,customer.getUsername()));
        assertEquals(before,orders.count());assertEquals(0,drafts.countDrafts(customer.getUsername()));verifyNoInteractions(geocoder);
    }
    @Test void cartKeepsBothAreasAndReusesExactlyTheSameRoutePrice() {
        var request=form(true,Order.ServiceType.STANDARD);var expected=routing.quote(request);
        var draft=drafts.addDraft(request,customer.getUsername());
        assertEquals(0,expected.total().compareTo(draft.getEstimatedFee()));
        var order=drafts.confirmDraft(draft.getId(),customer.getUsername());
        assertEquals(0,expected.total().compareTo(order.getShippingFee()));assertTrue(routes.findByOrder_Id(order.getId()).orElseThrow().isInterprovincial());
    }
    @Test void quoteNeedsLoginAndCsrfAndNeverTrustsBrowserFeeFields() throws Exception {
        mvc.perform(quote("1").with(user(customer.getUsername()).roles("USER"))).andExpect(status().isForbidden());
        mvc.perform(quote("1").with(csrf())).andExpect(status().is4xxClientError());
        mvc.perform(quote("1").with(user(customer.getUsername()).roles("USER")).with(csrf()).param("total","1").param("routeFee","0"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true)).andExpect(jsonPath("$.routeFee",greaterThan(0)))
            .andExpect(header().string("Cache-Control",containsString("no-store")));
        mvc.perform(quote("0.001").with(user(customer.getUsername()).roles("USER")).with(csrf()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));
    }
    @Test void orderFormRendersUnsupportedAreaErrorWithoutCreatingOrder() throws Exception {
        var request=form(false,Order.ServiceType.STANDARD);long before=orders.count();
        mvc.perform(post("/nguoi-dung/tao-don").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("submissionToken",request.getSubmissionToken()).param("senderName",request.getSenderName()).param("senderAddress",request.getSenderAddress())
                .param("receiverName",request.getReceiverName()).param("receiverAddress",request.getReceiverAddress()).param("receiverPhone",request.getReceiverPhone())
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC").param("deliveryProvinceCode","79").param("deliveryAreaCode","UNKNOWN")
                .param("weightKg","1").param("serviceType","STANDARD").param("paymentMethod","COD"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("chưa phục vụ khu vực")));
        assertEquals(before,orders.count());
    }
    @Test void orderDetailShowsBranchesInsteadOfMixingLegacyDistanceAndRoutePrices() throws Exception {
        var order=orderService.createOrder(form(true,Order.ServiceType.STANDARD),customer.getUsername());
        mvc.perform(get("/nguoi-dung/don-hang/"+order.getId()).with(user(customer.getUsername()).roles("USER")))
            .andExpect(status().isOk()).andExpect(content().string(containsString("Đà Nẵng")))
            .andExpect(content().string(not(containsString("km đường chim bay"))));
    }
}
