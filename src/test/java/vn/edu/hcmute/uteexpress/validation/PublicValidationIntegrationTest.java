package vn.edu.hcmute.uteexpress.validation;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.edu.hcmute.uteexpress.dto.*;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.ShippingTariffVersion;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.containsString;

/** Không @Transactional ở lớp test: kiểm tra COMMIT/ROLLBACK thật trên H2, không ghi Aiven/SMTP thật. */
@SpringBootTest(classes=vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class,properties={
    "spring.datasource.url=jdbc:h2:mem:public-validation-fix;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.mail.order-updates.enabled=false"})
@AutoConfigureMockMvc @ActiveProfiles("isolated-logistics")
@Import(PublicValidationIntegrationTest.TestTime.class)
class PublicValidationIntegrationTest {
    static final AtomicInteger SEQUENCE=new AtomicInteger();
    static final class MutableClock extends Clock {
        volatile Instant value=Instant.parse("2026-10-21T03:00:00Z");
        public ZoneId getZone(){return ZoneId.of("Asia/Ho_Chi_Minh");}
        public Clock withZone(ZoneId zone){return Clock.fixed(value,zone);}
        public Instant instant(){return value;}
        void advance(long seconds){value=value.plusSeconds(seconds);}
    }
    @TestConfiguration static class TestTime { @Bean @Primary MutableClock testTime(){return new MutableClock();} }
    @Autowired MutableClock clock;
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired EmailChangeRequestRepository changes;
    @Autowired OrderRepository orders;
    @Autowired OrderPaymentRepository payments;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired ShippingTariffRepository tariffs;
    @Autowired ShippingTariffVersionRepository versions;
    @Autowired AuthService auth;
    @Autowired EmailChangeService emailChanges;
    @Autowired OrderService orderService;
    @Autowired SavedAddressService addresses;
    @Autowired UserProfileService profiles;
    @Autowired PasswordEncoder encoder;
    @Autowired jakarta.validation.Validator validator;
    @MockBean JavaMailSender mail;
    AppUser customer;

    @BeforeEach void seed() {
        clock.value=Instant.parse("2026-10-21T03:00:00Z");
        customer=account(AppUser.Role.USER);
    }
    AppUser account(AppUser.Role role) {
        String name="validation"+SEQUENCE.incrementAndGet();
        var user=new AppUser(name,encoder.encode("OriginalPass123"),name+"@test.invalid");
        user.setEnabled(true); user.setRole(role); user.setFullName("Người Kiểm Thử"); user.setPhone("0901234567");
        return users.saveAndFlush(user);
    }
    String wrong(String correct){return correct.equals("000000")?"000001":"000000";}
    OrderCreateRequest form(){
        var form=orderService.prepareCreateForm(customer.getUsername());
        form.setSenderName("Người Gửi");form.setSenderAddress("Địa chỉ gửi kiểm thử");
        form.setReceiverName("Người Nhận");form.setReceiverPhone("0901234567");form.setReceiverAddress("Địa chỉ nhận kiểm thử");
        form.setWeightKg(1d);form.setServiceType(Order.ServiceType.STANDARD);form.setPaymentMethod(OrderPayment.PaymentMethod.COD);return form;
    }

    @Test void wrongEmailOtpCountsAreCommittedAndFifthFailureDeletesChallenge(){
        emailChanges.requestChange(customer.getUsername(),"target"+SEQUENCE.incrementAndGet()+"@test.invalid");
        String correct=emailChanges.findPending(customer.getUsername()).orElseThrow().getOtpCode();
        for(int attempt=1;attempt<=4;attempt++){
            assertThrows(IllegalStateException.class,()->emailChanges.confirm(customer.getUsername(),wrong(correct)));
            assertEquals(attempt,emailChanges.findPending(customer.getUsername()).orElseThrow().getAttemptCount());
        }
        assertThrows(IllegalStateException.class,()->emailChanges.confirm(customer.getUsername(),wrong(correct)));
        assertTrue(emailChanges.findPending(customer.getUsername()).isEmpty());
        assertThrows(IllegalStateException.class,()->emailChanges.confirm(customer.getUsername(),correct));
        assertEquals(customer.getEmail(),users.findById(customer.getId()).orElseThrow().getEmail());
    }
    @Test void expiredEmailChallengeDeletionIsCommittedAtExactBoundary(){
        emailChanges.requestChange(customer.getUsername(),"expired"+SEQUENCE.incrementAndGet()+"@test.invalid");
        String correct=emailChanges.findPending(customer.getUsername()).orElseThrow().getOtpCode();
        clock.advance(600);
        assertThrows(IllegalStateException.class,()->emailChanges.confirm(customer.getUsername(),correct));
        assertTrue(emailChanges.findPending(customer.getUsername()).isEmpty());
    }
    @Test void resetOtpIsInvalidatedAfterFiveFailuresAndNotRevivedByServiceRecreation(){
        auth.sendPasswordResetOtp(customer.getEmail());String code=users.findById(customer.getId()).orElseThrow().getOtpCode();
        for(int i=0;i<5;i++) assertFalse(auth.resetPassword(customer.getEmail(),wrong(code),"Replacement123"));
        assertNull(users.findById(customer.getId()).orElseThrow().getOtpCode());
        assertFalse(auth.resetPassword(customer.getEmail(),code,"Replacement123"));
        assertTrue(encoder.matches("OriginalPass123",users.findById(customer.getId()).orElseThrow().getPassword()));
    }
    @Test void sendsHaveSharedSixtySecondCooldownHourlyQuotaAndRecoverAfterWindow(){
        auth.sendPasswordResetOtp(customer.getEmail());
        assertThrows(IllegalStateException.class,()->auth.sendPasswordResetOtp(customer.getEmail()));
        for(int i=0;i<4;i++){clock.advance(60);auth.sendPasswordResetOtp(customer.getEmail());}
        clock.advance(60);assertThrows(IllegalStateException.class,()->auth.sendPasswordResetOtp(customer.getEmail()));
        verify(mail,times(5)).send(any(SimpleMailMessage.class));
        clock.advance(3600);auth.sendPasswordResetOtp(customer.getEmail());
        assertEquals(1,users.findById(customer.getId()).orElseThrow().getOtpSendCount());
    }
    @Test void smtpFailureRollsBackChallengeAndQuota(){
        doThrow(new MailSendException("test-only")).when(mail).send(any(SimpleMailMessage.class));
        assertThrows(IllegalStateException.class,()->auth.sendPasswordResetOtp(customer.getEmail()));
        var unchanged=users.findById(customer.getId()).orElseThrow();
        assertNull(unchanged.getOtpCode());assertNull(unchanged.getOtpLastSentAt());assertEquals(0,unchanged.getOtpSendCount());
    }
    @Test void twoConcurrentUsesOfSameResetOtpOnlyOneSucceeds() throws Exception {
        auth.sendPasswordResetOtp(customer.getEmail());String code=users.findById(customer.getId()).orElseThrow().getOtpCode();
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            Callable<Boolean> call=()->{start.await();return auth.resetPassword(customer.getEmail(),code,"Replacement123");};
            var first=executor.submit(call);var second=executor.submit(call);start.countDown();
            assertEquals(1,(first.get(15,TimeUnit.SECONDS)?1:0)+(second.get(15,TimeUnit.SECONDS)?1:0));
        }finally{executor.shutdownNow();}
    }
    @Test void activationCanRequestNewCodeAfterExpiryButNeverActivatesIssuedRoles(){
        String username="registration"+SEQUENCE.incrementAndGet();
        auth.register(username,"RegisterPass123",username+"@test.invalid","Người Mới");
        String old=users.findByUsername(username).orElseThrow().getOtpCode();clock.advance(600);
        assertFalse(auth.verifyOtp(username,old));auth.resendRegistrationOtp(username);
        String code=users.findByUsername(username).orElseThrow().getOtpCode();assertTrue(auth.verifyOtp(username,code));
        for(var role:List.of(AppUser.Role.ADMIN,AppUser.Role.MANAGER)){
            var issued=account(role);issued.setEnabled(false);users.saveAndFlush(issued);
            auth.resendRegistrationOtp(issued.getUsername());assertNull(users.findById(issued.getId()).orElseThrow().getOtpCode());
        }
    }
    @ParameterizedTest @ValueSource(strings={"a","abcdefgh","12345678","Password 123","Password\n123"})
    void weakPasswordsFailBothDtoAndServiceValidation(String password){
        var form=new vn.edu.hcmute.uteexpress.controller.auth.AuthController.RegisterForm();
        form.setUsername("validname");form.setEmail("valid@test.invalid");form.setPassword(password);
        assertFalse(validator.validate(form).isEmpty());
        assertThrows(IllegalArgumentException.class,()->auth.register("newname",password,"new@test.invalid","Tên"));
    }
    @Test void oversizedNamesAndBcryptUtf8OverflowAreRejected(){
        var form=new vn.edu.hcmute.uteexpress.controller.auth.AuthController.RegisterForm();
        form.setUsername("a".repeat(51));form.setFullName("b".repeat(101));
        assertFalse(validator.validateProperty(form,"username").isEmpty());assertFalse(validator.validateProperty(form,"fullName").isEmpty());
        assertFalse(PublicAccountValidation.passwordValid("ắ".repeat(30)+"1"));
        assertTrue(PublicAccountValidation.passwordValid("MậtKhẩu123"));
    }
    @ParameterizedTest @ValueSource(strings={"/dang-nhap","/dang-ky","/quen-mat-khau","/dat-lai-mat-khau","/xac-thuc-otp","/xac-thuc-otp/gui-lai"})
    void publicMutationsRequireCsrf(String route)throws Exception{mvc.perform(post(route)).andExpect(status().isForbidden());}
    @Test void publicLoginStillWorksWithCsrfAndCorrectRole()throws Exception{
        for(var role:List.of(AppUser.Role.USER,AppUser.Role.SHIPPER)){
            var actor=account(role);
            mvc.perform(post("/dang-nhap").with(csrf()).param("username",actor.getUsername()).param("password","OriginalPass123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(role==AppUser.Role.USER?"/nguoi-dung/trang-chu":"/shipper/trang-chu"));
        }
    }
    @Test void repeatedCodPostRedirectsToSameOwnedGetPageAndDoesNotDuplicateRows()throws Exception{
        var form=form();long before=orders.count();
        String location=null;
        for(int i=0;i<2;i++){
            var response=mvc.perform(post("/nguoi-dung/tao-don").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("submissionToken",form.getSubmissionToken()).param("senderName",form.getSenderName()).param("senderAddress",form.getSenderAddress())
                .param("receiverName",form.getReceiverName()).param("receiverAddress",form.getReceiverAddress()).param("receiverPhone",form.getReceiverPhone())
                .param("weightKg","1").param("serviceType","STANDARD").param("paymentMethod","COD"))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse();
            if(location==null)location=response.getRedirectedUrl();else assertEquals(location,response.getRedirectedUrl());
        }
        assertEquals(before+1,orders.count());
        mvc.perform(get(location).with(user(customer.getUsername()).roles("USER"))).andExpect(status().isOk());
        var other=account(AppUser.Role.USER);
        mvc.perform(get(location).with(user(other.getUsername()).roles("USER"))).andExpect(status().isNotFound());
    }
    @Test void concurrentIdenticalSubmissionCreatesOneOrderPaymentAndHistory()throws Exception{
        var request=form();long orderBefore=orders.count(),paymentBefore=payments.count(),historyBefore=history.count();
        var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try{
            Callable<Long> call=()->{start.await();return orderService.createOrder(request,customer.getUsername()).getId();};
            var first=executor.submit(call);var second=executor.submit(call);start.countDown();
            assertEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
        }finally{executor.shutdownNow();}
        assertEquals(orderBefore+1,orders.count());assertEquals(paymentBefore+1,payments.count());assertEquals(historyBefore+1,history.count());
    }
    @Test void tokenCannotBeReusedForChangedPayloadOrAnotherAccount(){
        var request=form();orderService.createOrder(request,customer.getUsername());request.setReceiverName("Người Khác");
        assertThrows(IllegalStateException.class,()->orderService.createOrder(request,customer.getUsername()));
        var other=account(AppUser.Role.USER);
        assertThrows(IllegalStateException.class,()->orderService.createOrder(request,other.getUsername()));
    }
    @Test void liveTariffAndScheduledVersionAreUsedByBothCustomerPages()throws Exception{
        var tariff=tariffs.findByServiceType(Order.ServiceType.STANDARD).orElseThrow();
        BigDecimal previousBase=tariff.getBaseFee(),previousPerKg=tariff.getFeePerKg();
        tariff.setBaseFee(new BigDecimal("21000"));tariff.setFeePerKg(new BigDecimal("4500"));tariffs.saveAndFlush(tariff);
        ShippingTariffVersion scheduled=null;
        try{
            mvc.perform(get("/nguoi-dung/tao-don").with(user(customer.getUsername()).roles("USER"))).andExpect(content().string(containsString("21.000đ + 4.500đ/kg")));
            mvc.perform(get("/uoc-tinh-cuoc")).andExpect(status().isOk()).andExpect(content().string(containsString("21.000")));
            var now=LocalDateTime.now(clock);
            scheduled=versions.saveAndFlush(new ShippingTariffVersion(Order.ServiceType.STANDARD,new BigDecimal("23000"),new BigDecimal("6000"),now.minusMinutes(1),now.plusHours(1),"test-only","Kiểm thử lịch giá",now));
            assertEquals(new BigDecimal("29000"),orderService.estimateFee(Order.ServiceType.STANDARD,1));
            mvc.perform(get("/nguoi-dung/tao-don").with(user(customer.getUsername()).roles("USER"))).andExpect(content().string(containsString("23.000đ + 6.000đ/kg")));
        }finally{if(scheduled!=null)versions.deleteById(scheduled.getId());tariff=tariffs.findByServiceType(Order.ServiceType.STANDARD).orElseThrow();tariff.setBaseFee(previousBase);tariff.setFeePerKg(previousPerKg);tariffs.saveAndFlush(tariff);}
    }

    @ParameterizedTest @ValueSource(strings={".name@test.invalid","name..x@test.invalid","name@-test.invalid","name@test..invalid","name@localhost"})
    void malformedEmailIsRejectedBeforeServiceOrDatabase(String email){
        assertThrows(IllegalArgumentException.class,()->PublicAccountValidation.requireEmail(email));
        var form=new PasswordResetRequest();form.setEmail(email);
        assertFalse(validator.validateProperty(form,"email").isEmpty());
    }
    @Test void contactPhoneIsNormalizedAndLegacyElevenDigitInputIsRejected(){
        var form=new ProfileUpdateRequest();form.setFullName(" Người Dùng ");form.setEmail(" USER@Test.Invalid ");form.setPhone("+84 901-234-567");
        assertTrue(validator.validate(form).isEmpty());assertEquals("0901234567",form.getPhone());assertEquals("user@test.invalid",form.getEmail());
        form.setPhone("01234567890");assertFalse(validator.validateProperty(form,"phone").isEmpty());
    }
    SavedAddressRequest address(String label,SavedAddress.AddressType type){
        var form=new SavedAddressRequest();form.setLabel(label);form.setContactName("Người Liên Hệ");form.setContactPhone("0901234567");
        form.setAddressLine("Địa chỉ liên hệ thử nghiệm");form.setAddressType(type);return form;
    }
    @Test void switchingAddressTypeCannotBypassTwentyAddressLimit(){
        for(int i=0;i<20;i++)addresses.create(address("Điểm "+i,SavedAddress.AddressType.RECEIVER),customer.getUsername());
        var sender=addresses.create(address("Điểm nguồn",SavedAddress.AddressType.SENDER),customer.getUsername());
        assertThrows(IllegalStateException.class,()->addresses.update(sender.getId(),address("Điểm mới",SavedAddress.AddressType.RECEIVER),customer.getUsername()));
        assertEquals(20,addresses.findByType(customer.getUsername(),SavedAddress.AddressType.RECEIVER).size());
        assertEquals(SavedAddress.AddressType.SENDER,addresses.findOwned(sender.getId(),customer.getUsername()).getAddressType());
    }
    @Test void changedPasswordRevokesSessionsAndOutstandingRecoveryAndEmailChallenges(){
        emailChanges.requestChange(customer.getUsername(),"pending"+SEQUENCE.incrementAndGet()+"@test.invalid");clock.advance(60);
        auth.sendPasswordResetOtp(customer.getEmail());String code=users.findById(customer.getId()).orElseThrow().getOtpCode();
        var oldPrincipal=new vn.edu.hcmute.uteexpress.security.admin.PublicAccountPrincipal(users.findById(customer.getId()).orElseThrow());
        var form=new PasswordChangeRequest();form.setCurrentPassword("OriginalPass123");form.setNewPassword("ChangedPassword123");form.setConfirmPassword("ChangedPassword123");
        profiles.changePassword(customer.getUsername(),form);
        var updated=users.findById(customer.getId()).orElseThrow();assertFalse(oldPrincipal.matchesAccount(updated));assertNull(updated.getOtpCode());
        assertTrue(emailChanges.findPending(customer.getUsername()).isEmpty());assertFalse(auth.resetPassword(customer.getEmail(),code,"AnotherPass123"));
    }
    @Test void userAndShipperPasswordChangeEndSessionWithClearSuccessMessage()throws Exception{
        for(var role:List.of(AppUser.Role.USER,AppUser.Role.SHIPPER)){
            var actor=account(role);var session=new org.springframework.mock.web.MockHttpSession();session.setAttribute("old-conversation","test-only");
            String prefix=role==AppUser.Role.USER?"/nguoi-dung":"/shipper";
            mvc.perform(post(prefix+"/tai-khoan/doi-mat-khau").session(session).with(user(actor.getUsername()).roles(role.name())).with(csrf())
                .param("currentPassword","OriginalPass123").param("newPassword","ChangedPass123").param("confirmPassword","ChangedPass123"))
                .andExpect(redirectedUrl("/dang-nhap?changed"));assertTrue(session.isInvalid());
        }
    }

    @Test void renderOptionalFrontendPreviewWithSyntheticDataOnly()throws Exception{
        if(!Boolean.getBoolean("validation.export-preview"))return;
        var root=java.nio.file.Path.of("target/validation-preview");java.nio.file.Files.createDirectories(root);
        for(var page:Map.of("register.html","/dang-ky","reset.html","/dat-lai-mat-khau","activate.html","/xac-thuc-otp",
                "order.html","/nguoi-dung/tao-don","address.html","/nguoi-dung/dia-chi/them","password.html","/nguoi-dung/tai-khoan/doi-mat-khau",
                "estimate.html","/uoc-tinh-cuoc").entrySet()){
            var request=get(page.getValue());if(page.getValue().startsWith("/nguoi-dung/"))request.with(user(customer.getUsername()).roles("USER"));
            String html=mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
            java.nio.file.Files.writeString(root.resolve(page.getKey()),html,java.nio.charset.StandardCharsets.UTF_8);
        }
        String invalid=mvc.perform(post("/dang-ky").with(csrf()).param("username","a").param("password","a").param("email","invalid"))
            .andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form","username","password","email"))
            .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.file.Files.writeString(root.resolve("register-invalid.html"),invalid,java.nio.charset.StandardCharsets.UTF_8);
    }
}
