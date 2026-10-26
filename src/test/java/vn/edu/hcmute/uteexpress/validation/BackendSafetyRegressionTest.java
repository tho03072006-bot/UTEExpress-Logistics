package vn.edu.hcmute.uteexpress.validation;

import com.zaxxer.hikari.HikariDataSource;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.*;
import vn.edu.hcmute.uteexpress.dto.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.ShippingTariffRepository;
import vn.edu.hcmute.uteexpress.security.*;
import vn.edu.hcmute.uteexpress.service.*;
import vn.edu.hcmute.uteexpress.service.geo.AddressGeocoder.GeocodingUnavailableException;
import vn.edu.hcmute.uteexpress.support.FixedAddressGeocoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Hồi quy transaction, quyền sở hữu, alias đăng nhập và đầu vào; chỉ ghi H2, không gọi bản đồ thật. */
@SpringBootTest(classes=vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class, properties={
    "spring.datasource.url=jdbc:h2:mem:backend-safety;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
    "spring.datasource.hikari.maximum-pool-size=5", "spring.datasource.hikari.connection-timeout=1000",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop", "app.mail.order-updates.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("isolated-logistics")
class BackendSafetyRegressionTest {
    static final AtomicInteger SEQUENCE=new AtomicInteger();
    @Autowired MockMvc mvc;
    @Autowired org.springframework.context.ApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderFormSubmissionRepository submissions;
    @Autowired OrderPaymentRepository payments;
    @Autowired PromoCodeRepository promoCodes;
    @Autowired ShippingTariffRepository tariffs;
    @Autowired OrderService orderService;
    @Autowired OrderDraftService drafts;
    @Autowired OrderPricingService pricing;
    @Autowired PasswordEncoder encoder;
    @Autowired jakarta.validation.Validator validator;
    @Autowired HikariDataSource dataSource;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PublicAuthRateGuard rateGuard;
    @MockBean(name="isolatedGeocoder") FixedAddressGeocoder geocoder;
    AppUser customer;

    @BeforeEach void seed() {
        assertTrue(dataSource.getJdbcUrl().startsWith("jdbc:h2:"));
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
        customer=account(AppUser.Role.USER);
        when(geocoder.locate(anyString())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "Không được gọi bản đồ trong transaction");
            return FixedAddressGeocoder.DEFAULT;
        });
    }
    AppUser account(AppUser.Role role) {
        String name="safety"+SEQUENCE.incrementAndGet();
        var user=new AppUser(name,encoder.encode("OriginalPass123"),name+"@test.invalid");
        user.setEnabled(true); user.setRole(role); user.setFullName("Người Kiểm Thử"); user.setPhone("0901234567");
        return users.saveAndFlush(user);
    }
    OrderCreateRequest form() { return form(customer); }
    OrderCreateRequest form(AppUser owner) {
        var request=orderService.prepareCreateForm(owner.getUsername());
        request.setSenderName("Người Gửi"); request.setSenderAddress("Địa chỉ gửi kiểm thử");
        request.setReceiverName("Người Nhận"); request.setReceiverAddress("Địa chỉ nhận kiểm thử");
        request.setReceiverPhone("0901234567"); request.setWeightKg(1d);
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.fill(request);
        return request;
    }

    @Test void concurrentRouteQuotesNeverCallExternalMapsOrWritePartialOrders() throws Exception {
        long before=orders.count(), tokens=submissions.count();
        when(geocoder.locate(anyString())).thenThrow(new GeocodingUnavailableException());
        var executor=Executors.newFixedThreadPool(5); var tasks=new ArrayList<Future<?>>();
        try {
            for(int i=0;i<5;i++)tasks.add(executor.submit(()->mvc.perform(get("/api/routing/quote")
                    .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC")
                    .param("deliveryProvinceCode","48").param("deliveryAreaCode","DAD-HAICHAU").param("weightKg","1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].interprovincial").value(true))));
            for(var task:tasks)task.get(10,TimeUnit.SECONDS);
            assertEquals(0,dataSource.getHikariPoolMXBean().getActiveConnections());
            try(var connection=dataSource.getConnection()){assertTrue(connection.isValid(1));}
        } finally {executor.shutdownNow();}
        verifyNoInteractions(geocoder);
        assertEquals(before,orders.count()); assertEquals(tokens,submissions.count());
    }
    @Test void httpOrderCreatesRouteEvenWhenExternalMapIsUnavailable() throws Exception {
        var request=form();long before=orders.count();
        when(geocoder.locate(anyString())).thenThrow(new GeocodingUnavailableException());
        mvc.perform(post("/nguoi-dung/tao-don").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("submissionToken",request.getSubmissionToken()).param("senderName",request.getSenderName())
                .param("senderAddress",request.getSenderAddress()).param("receiverName",request.getReceiverName())
                .param("receiverAddress",request.getReceiverAddress()).param("receiverPhone",request.getReceiverPhone())
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC")
                .param("deliveryProvinceCode","79").param("deliveryAreaCode","SGN-THUDUC")
                .param("weightKg","1").param("serviceType","STANDARD").param("paymentMethod","COD"))
            .andExpect(status().is3xxRedirection());
        assertEquals(before+1,orders.count());verifyNoInteractions(geocoder);
    }
    @Test void addConfirmOneAndConfirmAllNeverNeedExternalMaps() {
        var first=drafts.addDraft(form(),customer.getUsername());
        var order=drafts.confirmDraft(first.getId(),customer.getUsername());
        assertNotNull(payments.findByOrder(order).orElseThrow());
        drafts.addDraft(form(),customer.getUsername()); drafts.addDraft(form(),customer.getUsername());
        assertEquals(2,drafts.confirmAllDrafts(customer.getUsername()).size());
        assertEquals(0,drafts.countDrafts(customer.getUsername()));
    }
    @Test void httpQuoteAndCartConfirmationReleaseViewConnections() throws Exception {
        var first=drafts.addDraft(form(),customer.getUsername());drafts.addDraft(form(),customer.getUsername());
        when(geocoder.locate(anyString())).thenAnswer(call -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(0,dataSource.getHikariPoolMXBean().getActiveConnections());
            return FixedAddressGeocoder.DEFAULT;
        });
        mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("senderAddress","Địa chỉ gửi thử").param("receiverAddress","Địa chỉ nhận thử")
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC")
                .param("deliveryProvinceCode","79").param("deliveryAreaCode","SGN-THUDUC")
                .param("weightKg","1").param("serviceType","STANDARD"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        mvc.perform(post("/nguoi-dung/gio-don/"+first.getId()+"/xac-nhan").with(user(customer.getUsername()).roles("USER")).with(csrf()))
            .andExpect(redirectedUrl("/nguoi-dung/gio-don"));
        mvc.perform(post("/nguoi-dung/gio-don/xac-nhan").with(user(customer.getUsername()).roles("USER")).with(csrf()))
            .andExpect(redirectedUrl("/nguoi-dung/don-hang"));
        assertEquals(0,drafts.countDrafts(customer.getUsername()));
    }
    @Test void fullCartRejectsNewSubmissionBeforeMapButStillAcceptsRetry() {
        var request=form();var first=drafts.addDraft(request,customer.getUsername());
        for(int i=1;i<30;i++)drafts.addDraft(form(),customer.getUsername());
        clearInvocations(geocoder);
        assertThrows(IllegalStateException.class,()->drafts.addDraft(form(),customer.getUsername()));
        assertEquals(first.getId(),drafts.addDraft(request,customer.getUsername()).getId());
        verifyNoInteractions(geocoder);
    }
    @Test void completedSubmissionRetryDoesNotDependOnMapAvailability() {
        var direct=form(); var order=orderService.createOrder(direct,customer.getUsername());
        var cart=form(); var draft=drafts.addDraft(cart,customer.getUsername());
        clearInvocations(geocoder); when(geocoder.locate(anyString())).thenThrow(new GeocodingUnavailableException());
        assertEquals(order.getId(),orderService.createOrder(direct,customer.getUsername()).getId());
        assertEquals(draft.getId(),drafts.addDraft(cart,customer.getUsername()).getId());
        verifyNoInteractions(geocoder);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void lockedAccountCannotCreateDirectOrderOrDraft(boolean cart) {
        var request=form(); long before=orders.count();
        var owner=users.findById(customer.getId()).orElseThrow(); owner.setLockedByAdmin(true); users.saveAndFlush(owner);
        assertThrows(AccessDeniedException.class,()->{if(cart)drafts.addDraft(request,customer.getUsername());else orderService.createOrder(request,customer.getUsername());});
        assertEquals(before,orders.count()); assertEquals(0,drafts.countDrafts(customer.getUsername()));
        assertTrue(submissions.findByToken(request.getSubmissionToken()).isEmpty());
    }
    @Test void currentTariffIsReReadWhenConfirmingCart() {
        var tariff=tariffs.findByServiceType(Order.ServiceType.STANDARD).orElseThrow(); var original=tariff.getBaseFee();
        var draft=drafts.addDraft(form(),customer.getUsername());
        tariff.setBaseFee(original.add(BigDecimal.valueOf(1000)));tariffs.saveAndFlush(tariff);
        try {assertEquals(0,draft.getEstimatedFee().add(BigDecimal.valueOf(1000))
                .compareTo(drafts.confirmDraft(draft.getId(),customer.getUsername()).getShippingFee()));}
        finally {var latest=tariffs.findByServiceType(Order.ServiceType.STANDARD).orElseThrow();latest.setBaseFee(original);tariffs.saveAndFlush(latest);}
    }
    @Test void concurrentRemoveAndConfirmNeverDuplicateOrders() throws Exception {
        var first=drafts.addDraft(form(),customer.getUsername());drafts.addDraft(form(),customer.getUsername());
        long before=orders.count();var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        try {
            var confirm=executor.submit(()->{start.await();return drafts.confirmAllDrafts(customer.getUsername()).size();});
            var remove=executor.submit(()->{start.await();try{drafts.removeDraft(first.getId(),customer.getUsername());}catch(IllegalStateException alreadyConfirmed){}return true;});
            start.countDown();int count=confirm.get(10,TimeUnit.SECONDS);remove.get(10,TimeUnit.SECONDS);
            assertTrue(count==1||count==2);assertEquals(before+count,orders.count());
            assertEquals(0,drafts.countDrafts(customer.getUsername()));verifyNoInteractions(geocoder);
        } finally {executor.shutdownNow();}
    }
    @Test void oneInvalidPromoRollsBackEveryOrderAndKeepsWholeCart() {
        var invalid=form(); invalid.setPromoCode("NO_SUCH_PROMO");
        // Mô phỏng giỏ cũ hoặc mã vừa hết hiệu lực sau khi thêm giỏ.
        var stale=drafts.addDraft(form(),customer.getUsername());
        var good=drafts.addDraft(form(),customer.getUsername()); // xử lý trước bản nháp cũ có mã sai
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var draft=drafts.findDrafts(customer.getUsername()).stream().filter(d->d.getId().equals(stale.getId())).findFirst().orElseThrow();
            draft.setPromoCode(invalid.getPromoCode());
        });
        long before=orders.count(), tokens=submissions.count(), paymentCount=payments.count();
        assertThrows(IllegalStateException.class,()->drafts.confirmAllDrafts(customer.getUsername()));
        assertEquals(before,orders.count()); assertEquals(tokens,submissions.count());
        assertEquals(paymentCount,payments.count());
        assertEquals(2,drafts.countDrafts(customer.getUsername())); assertNotNull(good.getId());
    }
    @ParameterizedTest @ValueSource(strings={"--","''","..."," - . ' "})
    void namesContainingOnlyPunctuationNeverReachMapOrDatabase(String name) {
        var request=form(); request.setSenderName(name); request.setReceiverName(name);
        assertFalse(validator.validate(request).isEmpty());
        assertThrows(IllegalArgumentException.class,()->orderService.createOrder(request,customer.getUsername()));
        assertThrows(IllegalStateException.class,()->drafts.addDraft(request,customer.getUsername()));
        verifyNoInteractions(geocoder);
    }
    @ParameterizedTest @ValueSource(strings={"Nguyễn Văn An","Trần Thị Ánh","Jean-Luc O'Neil","Đặng A.","Nguyễn An"})
    void legitimateVietnameseAndPunctuatedNamesRemainValid(String name) {
        var request=form(); request.setSenderName(name); request.setReceiverName(name);
        assertTrue(validator.validate(request).isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"0.001","0","-1","100.01","NaN","Infinity","abc",""})
    void invalidQuoteWeightsHaveJsonValidationResponseWithoutCallingMap(String weight) throws Exception {
        mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("senderAddress","Địa chỉ gửi thử").param("receiverAddress","Địa chỉ nhận thử")
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC")
                .param("deliveryProvinceCode","79").param("deliveryAreaCode","SGN-THUDUC")
                .param("serviceType","STANDARD").param("weightKg",weight))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false)).andExpect(header().string("Cache-Control","no-store"));
        verifyNoInteractions(geocoder);
    }
    @ParameterizedTest @ValueSource(strings={"0.01","100"})
    void quoteAndCreateAcceptBothWeightBoundaries(String weight) throws Exception {
        var request=form(); request.setWeightKg(Double.valueOf(weight));assertTrue(validator.validate(request).isEmpty());
        mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("senderAddress",request.getSenderAddress()).param("receiverAddress",request.getReceiverAddress())
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC")
                .param("deliveryProvinceCode","79").param("deliveryAreaCode","SGN-THUDUC")
                .param("serviceType","STANDARD").param("weightKg",weight))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(true));
        assertNotNull(orderService.createOrder(request,customer.getUsername()).getId());
    }
    @Test void missingOrInvalidServiceAndControlCharactersAreRejectedAsJson() throws Exception {
        for(String service:List.of("","NOT_A_SERVICE"))
            mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(user(customer.getUsername()).roles("USER")).with(csrf())
                    .param("senderAddress","Địa chỉ thử").param("receiverAddress","Địa chỉ thử").param("serviceType",service).param("weightKg","1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));
        mvc.perform(post("/nguoi-dung/tao-don/bao-gia").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("senderAddress","Địa\nchỉ thử").param("receiverAddress","Địa chỉ thử").param("serviceType","STANDARD").param("weightKg","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.ok").value(false));
        verifyNoInteractions(geocoder);
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void usernameAndEmailShareLockoutForBothPublicRoles(boolean shipper) throws Exception {
        var actor=shipper?account(AppUser.Role.SHIPPER):customer;
        var session=new MockHttpSession();String identity=PublicAuthRequestFilter.accountIdentity(actor.getId());
        for(int i=0;i<10;i++) {
            String alias=i%2==0?actor.getUsername():actor.getEmail().toUpperCase(Locale.ROOT);
            mvc.perform(post("/dang-nhap").session(session).with(csrf()).param("username",alias).param("password","WrongPass123"))
                .andExpect(status().is3xxRedirection());
            assertEquals(i+1,rateGuard.loginFailures(identity));
            assertEquals(i+1,session.getAttribute(PublicAuthRequestFilter.FAILURES_ATTRIBUTE));
        }
        assertTrue(rateGuard.loginBlocked(identity));
        for(String alias:List.of(actor.getUsername(),actor.getEmail()))
            mvc.perform(post("/dang-nhap").session(session).with(csrf()).param("username",alias).param("password","OriginalPass123"))
                .andExpect(redirectedUrl("/dang-nhap?error&locked"));
        assertEquals(10,rateGuard.loginFailures(identity));
        // Chỉ tài khoản này bị khóa; tài khoản khác vẫn dùng được.
        var other=account(AppUser.Role.USER);
        mvc.perform(post("/dang-nhap").with(csrf()).param("username",other.getEmail()).param("password","OriginalPass123"))
            .andExpect(redirectedUrl("/nguoi-dung/trang-chu"));
    }
    @Test void successfulLoginThroughOtherAliasClearsSameCounterAndKeepsTypedUsernameInUi() throws Exception {
        var session=new MockHttpSession();String identity=PublicAuthRequestFilter.accountIdentity(customer.getId());
        mvc.perform(post("/dang-nhap").session(session).with(csrf()).param("username",customer.getUsername()).param("password","WrongPass123"))
            .andExpect(redirectedUrl("/dang-nhap?error"));
        assertEquals(1,rateGuard.loginFailures(identity));
        assertEquals(customer.getUsername(),session.getAttribute(PublicAuthRequestFilter.USERNAME_ATTRIBUTE));
        mvc.perform(post("/dang-nhap").session(session).with(csrf()).param("username",customer.getEmail()).param("password","OriginalPass123"))
            .andExpect(redirectedUrl("/nguoi-dung/trang-chu"));
        assertEquals(0,rateGuard.loginFailures(identity));assertNull(session.getAttribute(PublicAuthRequestFilter.FAILURES_ATTRIBUTE));
    }

    @Test void emailChangeDoesNotResetAccountLockout() throws Exception {
        String identity=PublicAuthRequestFilter.accountIdentity(customer.getId());
        for(int i=0;i<9;i++)rateGuard.loginFailed(identity);
        customer.setEmail("changed"+SEQUENCE.incrementAndGet()+"@test.invalid");users.saveAndFlush(customer);
        mvc.perform(post("/dang-nhap").with(csrf()).param("username",customer.getEmail()).param("password","WrongPass123"))
            .andExpect(redirectedUrl("/dang-nhap?error&locked"));
        mvc.perform(post("/dang-nhap").with(csrf()).param("username",customer.getUsername()).param("password","OriginalPass123"))
            .andExpect(redirectedUrl("/dang-nhap?error&locked"));
        assertEquals(10,rateGuard.loginFailures(identity));
    }

    @Test void mapResolutionRejectsAnUnexpectedEnclosingTransaction() {
        assertThrows(IllegalStateException.class,()->new TransactionTemplate(transactions).execute(status ->
                pricing.resolveRoute("Địa chỉ gửi thử","Địa chỉ nhận thử")));
        verifyNoInteractions(geocoder);
    }

    @Test void userSearchTreatsPercentAndUnderscoreAsLiteralCharactersAndKeepsOwnership() {
        var literal=form();literal.setReceiverName("Người Nhận Riêng");var matching=orderService.createOrder(literal,customer.getUsername());
        var ordinary=form();ordinary.setReceiverName("Người Khác");orderService.createOrder(ordinary,customer.getUsername());
        var other=account(AppUser.Role.USER);var foreign=form(other);foreign.setReceiverName(literal.getReceiverName());orderService.createOrder(foreign,other.getUsername());
        var page=orderService.searchOrdersOfUser(customer.getUsername(),null,"%_",org.springframework.data.domain.PageRequest.of(0,10));
        assertTrue(page.isEmpty(), "Ký tự LIKE không được biến thành điều kiện khớp mọi đơn");
        var own=orderService.searchOrdersOfUser(customer.getUsername(),null,"Nhận Riêng",org.springframework.data.domain.PageRequest.of(0,10));
        assertEquals(List.of(matching.getId()),own.stream().map(Order::getId).toList());
    }
    @Test void userSearchRejectsOverlongControlAndOversizedPaginationInputs() throws Exception {
        for(String keyword:List.of("x".repeat(101),"abc\u0000xyz","abc\nxyz")) {
            assertThrows(IllegalArgumentException.class,()->orderService.searchOrdersOfUser(customer.getUsername(),null,keyword,org.springframework.data.domain.PageRequest.of(0,10)));
            mvc.perform(get("/nguoi-dung/don-hang").with(user(customer.getUsername()).roles("USER")).param("keyword",keyword)).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/nguoi-dung/don-hang").with(user(customer.getUsername()).roles("USER")).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/nguoi-dung/don-hang").with(user(customer.getUsername()).roles("USER")).param("page","2147483647")).andExpect(status().isBadRequest());
        assertThrows(IllegalArgumentException.class,()->orderService.searchOrdersOfUser(customer.getUsername(),null,null,org.springframework.data.domain.PageRequest.of(0,101)));
    }
    @Test void labelDoesNotDescribeMissingPaymentAsNoMoneyDue() throws Exception {
        var order=orderService.createOrder(form(),customer.getUsername());payments.delete(payments.findByOrder(order).orElseThrow());payments.flush();
        mvc.perform(get("/nguoi-dung/don-hang/"+order.getId()).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa xác định cước")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Không thu tiền khi giao"))));
    }
    @Test void labelAndPaymentBadgeDoNotTreatAnInvalidWalletReceiptAsPaid() throws Exception {
        var request=form();request.setPaymentMethod(OrderPayment.PaymentMethod.MOMO);var order=orderService.createOrder(request,customer.getUsername());
        var payment=payments.findByOrder(order).orElseThrow();payment.setStatus(OrderPayment.PaymentStatus.PAID);payments.saveAndFlush(payment);
        mvc.perform(get("/nguoi-dung/don-hang/"+order.getId()).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Chưa xác định cước")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("badge-payment-paid"))));
    }
    @Test void labelClearlyStatesPendingWalletAndDoesNotCollectCashForCancelledOrders() throws Exception {
        var request=form();request.setPaymentMethod(OrderPayment.PaymentMethod.MOMO);var wallet=orderService.createOrder(request,customer.getUsername());
        mvc.perform(get("/nguoi-dung/don-hang/"+wallet.getId()).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("MoMo chưa thanh toán")));
        var cash=orderService.createOrder(form(),customer.getUsername());orderService.cancelOrder(cash.getId(),customer.getUsername());
        mvc.perform(get("/nguoi-dung/don-hang/"+cash.getId()).with(user(customer.getUsername()).roles("USER")))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Thu cước người nhận:"))));
    }

    @Test void anotherAccountsCartIsNeverResolvedOrConfirmed() {
        var draft=drafts.addDraft(form(),customer.getUsername()); var other=account(AppUser.Role.USER);
        clearInvocations(geocoder);
        assertThrows(IllegalStateException.class,()->drafts.confirmDraft(draft.getId(),other.getUsername()));
        assertThrows(IllegalStateException.class,()->drafts.confirmAllDrafts(other.getUsername()));
        assertEquals(1,drafts.countDrafts(customer.getUsername()));verifyNoInteractions(geocoder);
    }

    @Test void cartCannotAcceptAStreetThatCannotFitItsFullDeliveryAddress() {
        var request=form();request.setReceiverAddress("Đ".repeat(250));
        long before=drafts.countDrafts(customer.getUsername());
        assertThrows(IllegalArgumentException.class,()->drafts.addDraft(request,customer.getUsername()));
        assertEquals(before,drafts.countDrafts(customer.getUsername()));
        assertTrue(submissions.findByToken(request.getSubmissionToken()).isEmpty());
    }

    @Test void selectingCheaperAreaMustRecheckPromoMinimumAndLeaveCartUnchangedOnFailure() {
        var promo=new PromoCode();promo.setCode("AUDIT"+SEQUENCE.incrementAndGet());
        promo.setDiscountType(PromoCode.DiscountType.FIXED_AMOUNT);promo.setDiscountValue(BigDecimal.valueOf(1000));
        promo.setMinOrderAmount(BigDecimal.valueOf(18001));promoCodes.saveAndFlush(promo);
        var request=form();request.setDeliveryProvinceCode("48");request.setDeliveryAreaCode("DAD-HAICHAU");request.setPromoCode(promo.getCode());
        var draft=drafts.addDraft(request,customer.getUsername());
        var change=drafts.prepareAreaForm(draft.getId(),customer.getUsername());
        change.setDeliveryProvinceCode("79");change.setDeliveryAreaCode("SGN-THUDUC");
        assertThrows(IllegalStateException.class,()->drafts.selectAreas(draft.getId(),change,customer.getUsername()));
        assertEquals("DAD-HAICHAU",drafts.prepareAreaForm(draft.getId(),customer.getUsername()).getDeliveryAreaCode());
        assertEquals(0,draft.getEstimatedFee().compareTo(drafts.findDrafts(customer.getUsername()).get(0).getEstimatedFee()));
    }

    @Test void requiresNewTransactionMustHaveItsOwnAccountCacheAndRestoreOuterCache() {
        var outer=new TransactionTemplate(transactions);var inner=new TransactionTemplate(transactions);
        inner.setPropagationBehavior(org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        outer.executeWithoutResult(status -> {
            assertEquals("outer",vn.edu.hcmute.uteexpress.service.tracking.TransactionCache.get("audit-isolation",()->"outer"));
            inner.executeWithoutResult(nested -> assertEquals("inner",vn.edu.hcmute.uteexpress.service.tracking.TransactionCache.get("audit-isolation",()->"inner")));
            assertEquals("outer",vn.edu.hcmute.uteexpress.service.tracking.TransactionCache.get("audit-isolation",()->"unexpected"));
        });
        outer.executeWithoutResult(status -> assertEquals("fresh",vn.edu.hcmute.uteexpress.service.tracking.TransactionCache.get("audit-isolation",()->"fresh")));
    }
}
