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
    @Autowired org.springframework.context.ApplicationContext context;
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
    @Autowired jakarta.persistence.EntityManagerFactory entityManagers;
    @Autowired OrderDraftService drafts;
    @Autowired OrderFormSubmissionRepository submissions;
    @MockBean JavaMailSender mail;
    @Autowired vn.edu.hcmute.uteexpress.service.routing.OrderRoutingService routing;
    @Autowired vn.edu.hcmute.uteexpress.repository.routing.OrderRouteRepository routeRecords;
    @Autowired vn.edu.hcmute.uteexpress.repository.routing.DraftOrderAreaRepository draftAreas;
    @Autowired vn.edu.hcmute.uteexpress.repository.routing.SavedAddressAreaRepository addressAreas;
    @Autowired DraftOrderRepository draftRecords;
    AppUser customer;

    @Test void quoteApiAndCreatedOrderUseExactlyTheSameInterprovincialFee() throws Exception {
        var form = form(); form.setDeliveryProvinceCode("01"); form.setDeliveryAreaCode("HAN-CAUGIAY");
        var quote = routing.quote(form);
        mvc.perform(get("/api/routing/quote").param("weightKg","1").param("serviceType","STANDARD")
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC")
                .param("deliveryProvinceCode","01").param("deliveryAreaCode","HAN-CAUGIAY"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].total").value(quote.total().intValue()))
                .andExpect(jsonPath("$[0].distanceFee").value(0)).andExpect(jsonPath("$[0].routeFee").value(25000));
        var order = orderService.createOrder(form, customer.getUsername());
        assertEquals(0, quote.total().compareTo(order.getShippingFee()));
        var route = routeRecords.findByOrder_Id(order.getId()).orElseThrow();
        assertEquals(vn.edu.hcmute.uteexpress.entity.routing.OrderRoute.Stage.PICKUP, route.getStage());
        assertTrue(order.getSenderAddress().endsWith("TP. Hồ Chí Minh")); assertTrue(order.getReceiverAddress().endsWith("Hà Nội"));
    }
    @Test void mismatchedProvinceRollsBackReservedSubmissionAndDoesNotCreateAnyOrder() {
        var form = form(); form.setPickupProvinceCode("01"); long before = orders.count(), routesBefore = routeRecords.count();
        assertThrows(IllegalArgumentException.class, () -> orderService.createOrder(form, customer.getUsername()));
        assertEquals(before, orders.count()); assertEquals(routesBefore, routeRecords.count());
        assertTrue(submissions.findByToken(form.getSubmissionToken()).isEmpty());
    }
    @Test void changingOnlyAreaCannotReuseASubmittedToken() {
        var form = form(); orderService.createOrder(form, customer.getUsername()); form.setDeliveryAreaCode("SGN-Q1");
        assertThrows(IllegalStateException.class, () -> orderService.createOrder(form, customer.getUsername()));
    }
    @Test void legacyDraftRequiresOwnedAreaSelectionAndRejectsStaleSelection() {
        var saved = drafts.addDraft(form(), customer.getUsername());
        draftAreas.deleteAll(draftAreas.findByDraftOrder_Id(saved.getId()).stream().toList());
        assertThrows(IllegalStateException.class, () -> drafts.confirmDraft(saved.getId(), customer.getUsername()));
        var other = account(AppUser.Role.USER);
        assertThrows(IllegalStateException.class, () -> drafts.prepareAreaForm(saved.getId(), other.getUsername()));
        var selection = drafts.prepareAreaForm(saved.getId(), customer.getUsername());
        selection.setPickupProvinceCode("79"); selection.setPickupAreaCode("SGN-THUDUC");
        selection.setDeliveryProvinceCode("01"); selection.setDeliveryAreaCode("HAN-CAUGIAY");
        drafts.selectAreas(saved.getId(), selection, customer.getUsername());
        assertThrows(IllegalStateException.class, () -> drafts.selectAreas(saved.getId(), selection, customer.getUsername()));
        var order = drafts.confirmDraft(saved.getId(), customer.getUsername());
        assertEquals(new BigDecimal("43000"), order.getShippingFee());
        assertTrue(draftRecords.findById(saved.getId()).isEmpty()); assertTrue(draftAreas.findByDraftOrder_Id(saved.getId()).isEmpty());
    }
    @Test void savedAddressMustFitFullShippingAddressAndRollbackOnFailure() {
        var form=address("Địa chỉ dài",SavedAddress.AddressType.SENDER);form.setAddressLine("A".repeat(255));
        long before=addresses.countOfUser(customer.getUsername());
        assertThrows(IllegalArgumentException.class,()->addresses.create(form,customer.getUsername()));
        assertEquals(before,addresses.countOfUser(customer.getUsername()));
    }
    @Test void updateSavedAddressCannotMakeItUnusableForOrderCreation() {
        var original=addresses.create(address("Địa chỉ gốc",SavedAddress.AddressType.SENDER),customer.getUsername());
        var form=address("Địa chỉ gốc",SavedAddress.AddressType.SENDER);form.setAddressLine("A".repeat(255));
        assertThrows(IllegalArgumentException.class,()->addresses.update(original.getId(),form,customer.getUsername()));
        assertEquals(original.getAddressLine(),addresses.findOwned(original.getId(),customer.getUsername()).getAddressLine());
    }
    @Test void savedAddressAreaIsUpdatedAndDeletedWithOwnedAddress() {
        var form = address("Địa chỉ khu vực", SavedAddress.AddressType.SENDER);
        var address = addresses.create(form, customer.getUsername());
        assertEquals("SGN-THUDUC", addressAreas.findBySavedAddress_Id(address.getId()).orElseThrow().getArea().getAreaCode());
        form.setAreaCode("SGN-Q1"); addresses.update(address.getId(), form, customer.getUsername());
        assertEquals("SGN-Q1", addressAreas.findBySavedAddress_Id(address.getId()).orElseThrow().getArea().getAreaCode());
        addresses.delete(address.getId(), customer.getUsername()); assertTrue(addressAreas.findBySavedAddress_Id(address.getId()).isEmpty());
    }
    @Test void unknownAndInactiveAreaCannotSaveAnAddress() {
        var form = address("Địa chỉ sai", SavedAddress.AddressType.SENDER); form.setAreaCode("UNKNOWN");
        long before = addresses.countOfUser(customer.getUsername());
        assertThrows(IllegalArgumentException.class, () -> addresses.create(form, customer.getUsername()));
        assertEquals(before, addresses.countOfUser(customer.getUsername()));
    }

    @BeforeEach void seed() {
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.seedH2(context);
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
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.fill(form);
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
    @Test void preloadedWebRequestsCannotLoseEmailOtpAttemptsOrSurviveTheFifthFailure() {
        emailChanges.requestChange(customer.getUsername(),"cache"+SEQUENCE.incrementAndGet()+"@test.invalid");
        var pending=emailChanges.findPending(customer.getUsername()).orElseThrow();
        String incorrect=wrong(pending.getOtpCode());
        for(int wave=0;wave<3;wave++) {
            var contexts=new ArrayList<jakarta.persistence.EntityManager>();
            try {
                // Hai request đã đọc yêu cầu cùng lúc trước khi vào transaction confirm, như OSIV.
                for(int request=0;request<2;request++) {
                    var context=entityManagers.createEntityManager();contexts.add(context);
                    context.getTransaction().begin();
                    context.find(AppUser.class,customer.getId());
                    assertNotNull(context.find(EmailChangeRequest.class,pending.getId()));
                    context.getTransaction().commit();
                }
                for(var context:contexts) {
                    org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(entityManagers,new org.springframework.orm.jpa.EntityManagerHolder(context));
                    try { assertThrows(IllegalStateException.class,()->emailChanges.confirm(customer.getUsername(),incorrect)); }
                    finally { org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagers); }
                }
            } finally { for(var context:contexts)context.close(); }
            if(wave<2) assertEquals((wave+1)*2,emailChanges.findPending(customer.getUsername()).orElseThrow().getAttemptCount());
        }
        assertTrue(emailChanges.findPending(customer.getUsername()).isEmpty());
        assertThrows(IllegalStateException.class,()->emailChanges.confirm(customer.getUsername(),pending.getOtpCode()));
    }
    @Test void concurrentWrongEmailOtpAttemptsAreSerializedAndStopAtFive() throws Exception {
        emailChanges.requestChange(customer.getUsername(),"parallel"+SEQUENCE.incrementAndGet()+"@test.invalid");
        String incorrect=wrong(emailChanges.findPending(customer.getUsername()).orElseThrow().getOtpCode());
        var executor=Executors.newFixedThreadPool(4);var start=new CountDownLatch(1);
        try {
            var calls=new ArrayList<Future<Boolean>>();
            for(int i=0;i<6;i++)calls.add(executor.submit(()->{start.await();try{emailChanges.confirm(customer.getUsername(),incorrect);return false;}catch(IllegalStateException ex){return true;}}));
            start.countDown();for(var call:calls)assertTrue(call.get(20,TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertTrue(emailChanges.findPending(customer.getUsername()).isEmpty());
    }
    @Test void changedEmailInvalidatesOldMailboxRecoveryButNewMailboxCanRecover() {
        auth.sendPasswordResetOtp(customer.getEmail());
        String oldCode=users.findById(customer.getId()).orElseThrow().getOtpCode();clock.advance(60);
        String target="newmail"+SEQUENCE.incrementAndGet()+"@test.invalid";
        emailChanges.requestChange(customer.getUsername(),target);
        String confirmation=emailChanges.findPending(customer.getUsername()).orElseThrow().getOtpCode();
        emailChanges.confirm(customer.getUsername(),confirmation);
        var updated=users.findById(customer.getId()).orElseThrow();
        assertEquals(target,updated.getEmail());assertNull(updated.getOtpCode());assertNull(updated.getOtpExpiry());assertEquals(0,updated.getOtpAttempts());
        assertFalse(auth.resetPassword(target,oldCode,"NewReplacement123"));
        assertFalse(auth.resetPassword(customer.getEmail(),oldCode,"NewReplacement123"));
        clock.advance(60);auth.sendPasswordResetOtp(target);
        String fresh=users.findById(customer.getId()).orElseThrow().getOtpCode();
        assertTrue(auth.resetPassword(target,fresh,"NewReplacement123"));
    }
    @Test void failedEmailConfirmationDoesNotInvalidateLegitimateRecoveryCode() {
        auth.sendPasswordResetOtp(customer.getEmail());
        String reset=users.findById(customer.getId()).orElseThrow().getOtpCode();clock.advance(60);
        emailChanges.requestChange(customer.getUsername(),"unconfirmed"+SEQUENCE.incrementAndGet()+"@test.invalid");
        String correct=emailChanges.findPending(customer.getUsername()).orElseThrow().getOtpCode();
        assertThrows(IllegalStateException.class,()->emailChanges.confirm(customer.getUsername(),wrong(correct)));
        assertTrue(auth.resetPassword(customer.getEmail(),reset,"StillRecover123"));
        assertTrue(emailChanges.findPending(customer.getUsername()).isEmpty());
    }
    @Test void cachedEmailChallengeUsesTheFreshCodeAfterResend() {
        emailChanges.requestChange(customer.getUsername(),"resent"+SEQUENCE.incrementAndGet()+"@test.invalid");
        var pending=emailChanges.findPending(customer.getUsername()).orElseThrow();
        var context=entityManagers.createEntityManager();
        try {
            context.getTransaction().begin();context.find(EmailChangeRequest.class,pending.getId());context.getTransaction().commit();
            clock.advance(60);emailChanges.resendOtp(customer.getUsername());
            String fresh=emailChanges.findPending(customer.getUsername()).orElseThrow().getOtpCode();
            assertNotEquals(pending.getOtpCode(),fresh);
            org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(entityManagers,new org.springframework.orm.jpa.EntityManagerHolder(context));
            try { emailChanges.confirm(customer.getUsername(),fresh); }
            finally { org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagers); }
            assertTrue(emailChanges.findPending(customer.getUsername()).isEmpty());
        } finally { context.close(); }
    }
    @ParameterizedTest @ValueSource(strings={"EMAIL_CHANGE","PASSWORD_RESET","REGISTRATION"})
    void preloadedAccountCannotBypassSharedOtpSendCooldown(String flow) {
        if(flow.equals("REGISTRATION")) { customer.setEnabled(false); users.saveAndFlush(customer); }
        Runnable send=switch(flow) {
            case "EMAIL_CHANGE" -> ()->emailChanges.requestChange(customer.getUsername(),"cooldown"+customer.getId()+"@test.invalid");
            case "PASSWORD_RESET" -> ()->auth.sendPasswordResetOtp(customer.getEmail());
            default -> ()->auth.resendRegistrationOtp(customer.getUsername());
        };
        withPreloadedAccount(send,()->assertThrows(IllegalStateException.class,send::run));
        assertEquals(1,users.findById(customer.getId()).orElseThrow().getOtpSendCount());
        verify(mail,times(1)).send(any(SimpleMailMessage.class));
    }
    @Test void preloadedAccountCannotUseAnAlreadyConsumedPasswordResetCode() {
        auth.sendPasswordResetOtp(customer.getEmail());
        String code=users.findById(customer.getId()).orElseThrow().getOtpCode();
        withPreloadedAccount(()->assertTrue(auth.resetPassword(customer.getEmail(),code,"FirstReplacement123")),
                ()->assertFalse(auth.resetPassword(customer.getEmail(),code,"SecondReplacement123")));
        assertTrue(encoder.matches("FirstReplacement123",users.findById(customer.getId()).orElseThrow().getPassword()));
    }
    @Test void preloadedAccountCannotUseAResetCodeAfterFiveCommittedFailures() {
        auth.sendPasswordResetOtp(customer.getEmail());
        String code=users.findById(customer.getId()).orElseThrow().getOtpCode();
        withPreloadedAccount(()->{
            for(int i=0;i<5;i++) assertFalse(auth.resetPassword(customer.getEmail(),wrong(code),"Replacement123"));
        },()->assertFalse(auth.resetPassword(customer.getEmail(),code,"Replacement123")));
        assertTrue(encoder.matches("OriginalPass123",users.findById(customer.getId()).orElseThrow().getPassword()));
    }
    @ParameterizedTest @ValueSource(strings={"CONFIRM_EMAIL","UPDATE_PROFILE","CHANGE_PASSWORD"})
    void preloadedAccountCannotMutateAfterAdminLocksIt(String action) {
        emailChanges.requestChange(customer.getUsername(),"locked"+customer.getId()+"@test.invalid");
        String code=emailChanges.findPending(customer.getUsername()).orElseThrow().getOtpCode();
        Runnable mutation=switch(action) {
            case "CONFIRM_EMAIL" -> ()->emailChanges.confirm(customer.getUsername(),code);
            case "UPDATE_PROFILE" -> ()->profiles.updateProfile(customer.getUsername(),ProfileUpdateRequest.from(customer));
            default -> ()->profiles.changePassword(customer.getUsername(),passwordChange("OriginalPass123","Replacement123"));
        };
        withPreloadedAccount(()->{
            var locked=users.findById(customer.getId()).orElseThrow();
            locked.setEnabled(false); locked.setLockedByAdmin(true); users.saveAndFlush(locked);
        },()->assertThrows(org.springframework.security.access.AccessDeniedException.class,
                mutation::run));
        var locked=users.findById(customer.getId()).orElseThrow();
        assertEquals(customer.getEmail(),locked.getEmail()); assertFalse(locked.isEnabled()); assertTrue(locked.isLockedByAdmin());
    }
    @Test void preloadedAccountMustValidateCurrentPasswordAgainstLatestCommittedPassword() {
        withPreloadedAccount(()->profiles.changePassword(customer.getUsername(),passwordChange("OriginalPass123","FirstReplacement123")),
                ()->assertThrows(IllegalStateException.class,
                        ()->profiles.changePassword(customer.getUsername(),passwordChange("OriginalPass123","SecondReplacement123"))));
        assertTrue(encoder.matches("FirstReplacement123",users.findById(customer.getId()).orElseThrow().getPassword()));
    }
    @Test void nestedEmailChallengeLocksPreserveLegitimateProfileAndPasswordUpdates() {
        var form=ProfileUpdateRequest.from(customer); form.setFullName("Tên Mới Hợp Lệ"); form.setPhone("0912345678");
        form.setEmail("profile"+customer.getId()+"@test.invalid");
        assertTrue(profiles.updateProfile(customer.getUsername(),form));
        var updated=users.findById(customer.getId()).orElseThrow();
        assertEquals(form.getFullName(),updated.getFullName()); assertEquals(form.getPhone(),updated.getPhone());
        assertEquals(customer.getEmail(),updated.getEmail()); assertTrue(emailChanges.findPending(customer.getUsername()).isPresent());
        profiles.changePassword(customer.getUsername(),passwordChange("OriginalPass123","Replacement123"));
        updated=users.findById(customer.getId()).orElseThrow();
        assertTrue(encoder.matches("Replacement123",updated.getPassword())); assertEquals(1,updated.getAccessVersion());
        assertTrue(emailChanges.findPending(customer.getUsername()).isEmpty());
    }
    private PasswordChangeRequest passwordChange(String current,String replacement) {
        var form=new PasswordChangeRequest(); form.setCurrentPassword(current); form.setNewPassword(replacement); form.setConfirmPassword(replacement); return form;
    }
    @ParameterizedTest @ValueSource(strings={"CREATE","UPDATE","DELETE","DEFAULT"})
    void preloadedAccountCannotMutateSavedAddressesAfterAdminLocksIt(String action) {
        var original=addresses.create(address("Địa chỉ gốc",SavedAddress.AddressType.SENDER),customer.getUsername());
        Runnable mutation=switch(action) {
            case "CREATE" -> ()->addresses.create(address("Địa chỉ mới",SavedAddress.AddressType.SENDER),customer.getUsername());
            case "UPDATE" -> ()->addresses.update(original.getId(),address("Địa chỉ sửa",SavedAddress.AddressType.SENDER),customer.getUsername());
            case "DELETE" -> ()->addresses.delete(original.getId(),customer.getUsername());
            default -> ()->addresses.markAsDefault(original.getId(),customer.getUsername());
        };
        withPreloadedAccount(()->{
            var locked=users.findById(customer.getId()).orElseThrow();locked.setEnabled(false);locked.setLockedByAdmin(true);users.saveAndFlush(locked);
        },()->assertThrows(org.springframework.security.access.AccessDeniedException.class,mutation::run));
        assertEquals(1,addresses.countOfUser(customer.getUsername()));
        assertEquals("Địa chỉ gốc",addresses.findOwned(original.getId(),customer.getUsername()).getLabel());
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"USER,DETAILS","SHIPPER,DETAILS","USER,NOTIFICATIONS","SHIPPER,NOTIFICATIONS",
            "USER,AVATAR_UPLOAD","SHIPPER,AVATAR_UPLOAD","USER,AVATAR_REMOVE","SHIPPER,AVATAR_REMOVE"})
    void preloadedPublicProfileCannotMutateAfterAdminLocksIt(AppUser.Role role,String action) {
        customer.setRole(role);users.saveAndFlush(customer);
        var accountProfile=context.getBean(vn.edu.hcmute.uteexpress.service.profile.AccountProfileService.class);
        var form=new vn.edu.hcmute.uteexpress.dto.profile.ProfileDetailForm();form.setBio("Hồ sơ hợp lệ");
        var avatar=new org.springframework.mock.web.MockMultipartFile("avatar","avatar.png","image/png",
                context.getBean(vn.edu.hcmute.uteexpress.service.tracking.ShipmentQrService.class).generate("UTEPROFILE01"));
        Runnable mutation=switch(action) {
            case "DETAILS" -> ()->accountProfile.updateDetails(customer.getUsername(),form);
            case "NOTIFICATIONS" -> ()->accountProfile.updateNotifications(customer.getUsername(),false);
            case "AVATAR_UPLOAD" -> ()->accountProfile.updateAvatar(customer.getUsername(),avatar);
            default -> ()->accountProfile.removeAvatar(customer.getUsername());
        };
        withPreloadedAccount(()->{
            var locked=users.findById(customer.getId()).orElseThrow();locked.setEnabled(false);locked.setLockedByAdmin(true);users.saveAndFlush(locked);
        },()->assertThrows(org.springframework.security.access.AccessDeniedException.class,mutation::run));
        assertTrue(context.getBean(UserProfileDetailRepository.class).findByUser(customer).isEmpty());
    }
    @Test void profileServiceRejectsInvalidDetailsBeforeWritingOrTruncatingUserInput() {
        var accountProfile=context.getBean(vn.edu.hcmute.uteexpress.service.profile.AccountProfileService.class);
        assertThrows(IllegalArgumentException.class,()->accountProfile.updateDetails(customer.getUsername(),null));
        var form=new vn.edu.hcmute.uteexpress.dto.profile.ProfileDetailForm();form.setBio("x".repeat(161));
        assertThrows(IllegalArgumentException.class,()->accountProfile.updateDetails(customer.getUsername(),form));
        assertTrue(context.getBean(UserProfileDetailRepository.class).findByUser(customer).isEmpty());
    }
    /** Mô phỏng advice/OSIV đã đọc tài khoản trước khi một request khác commit. */
    private void withPreloadedAccount(Runnable competingRequest,Runnable cachedRequest) {
        var context=entityManagers.createEntityManager();
        try {
            context.getTransaction().begin(); context.find(AppUser.class,customer.getId()); context.getTransaction().commit();
            competingRequest.run();
            org.springframework.transaction.support.TransactionSynchronizationManager.bindResource(entityManagers,new org.springframework.orm.jpa.EntityManagerHolder(context));
            try { cachedRequest.run(); }
            finally { org.springframework.transaction.support.TransactionSynchronizationManager.unbindResource(entityManagers); }
        } finally { context.close(); }
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
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC").param("deliveryProvinceCode","79").param("deliveryAreaCode","SGN-THUDUC")
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
    @Test void repeatedCartSubmissionAndConfirmationProduceOneOrderOnly() {
        var request=form();long before=orders.count();
        var first=drafts.addDraft(request,customer.getUsername());
        assertEquals(first.getId(),drafts.addDraft(request,customer.getUsername()).getId());
        assertEquals(1,drafts.countDrafts(customer.getUsername()));
        assertEquals(1,drafts.confirmAllDrafts(customer.getUsername()).size());assertEquals(before+1,orders.count());
        assertThrows(IllegalStateException.class,()->drafts.addDraft(request,customer.getUsername()));
        assertEquals(0,drafts.countDrafts(customer.getUsername()));assertEquals(before+1,orders.count());
    }
    @Test void concurrentCartSubmissionsHaveOneDurableTokenAndOneDraft() throws Exception {
        var request=form();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Long> call=()->{start.await();return drafts.addDraft(request,customer.getUsername()).getId();};
            var first=executor.submit(call);var second=executor.submit(call);start.countDown();
            assertEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
        assertEquals(1,drafts.countDrafts(customer.getUsername()));
        assertNotNull(submissions.findByToken(request.getSubmissionToken()).orElseThrow().getResourceId());
    }
    @Test void samePayloadWithDifferentFormTokensCanCreateTwoLegitimateDrafts() {
        var first=drafts.addDraft(form(),customer.getUsername());
        var second=drafts.addDraft(form(),customer.getUsername());
        assertNotEquals(first.getId(),second.getId());assertEquals(2,drafts.countDrafts(customer.getUsername()));
    }
    @Test void concurrentDirectAndCartUseOfOneTokenOnlyAllowsOneAction() throws Exception {
        var request=form();long before=orders.count();var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var direct=executor.submit(()->{start.await();try{orderService.createOrder(request,customer.getUsername());return 1;}catch(IllegalStateException ex){return 0;}});
            var cart=executor.submit(()->{start.await();try{drafts.addDraft(request,customer.getUsername());return 1;}catch(IllegalStateException ex){return 0;}});
            start.countDown();assertEquals(1,direct.get(15,TimeUnit.SECONDS)+cart.get(15,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
        assertEquals(1,(orders.count()-before)+drafts.countDrafts(customer.getUsername()));
    }
    @Test void concurrentDifferentAccountsCannotClaimTheSameFormToken() throws Exception {
        var request=form();var other=account(AppUser.Role.USER);var executor=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var tasks=new ArrayList<Future<Integer>>();
            for(var actor:List.of(customer,other)) tasks.add(executor.submit(()->{
                start.await();try{drafts.addDraft(request,actor.getUsername());return 1;}
                catch(IllegalStateException | org.springframework.dao.DataIntegrityViolationException ex){return 0;}
            }));
            start.countDown();assertEquals(1,tasks.get(0).get(15,TimeUnit.SECONDS)+tasks.get(1).get(15,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
        assertEquals(1,drafts.countDrafts(customer.getUsername())+drafts.countDrafts(other.getUsername()));
    }
    @Test void removedDraftAndClearedCartNeverReviveAnOldFormToken() {
        var removed=form();var draft=drafts.addDraft(removed,customer.getUsername());drafts.removeDraft(draft.getId(),customer.getUsername());
        assertThrows(IllegalStateException.class,()->drafts.addDraft(removed,customer.getUsername()));
        var cleared=form();drafts.addDraft(cleared,customer.getUsername());drafts.clearDrafts(customer.getUsername());
        assertThrows(IllegalStateException.class,()->drafts.addDraft(cleared,customer.getUsername()));
        assertEquals(0,drafts.countDrafts(customer.getUsername()));
    }
    @Test void cartTokenCannotChangePayloadOwnerOrSwitchToDirectCreation() {
        var request=form();drafts.addDraft(request,customer.getUsername());
        assertThrows(IllegalStateException.class,()->orderService.createOrder(request,customer.getUsername()));
        var other=account(AppUser.Role.USER);assertThrows(IllegalStateException.class,()->drafts.addDraft(request,other.getUsername()));
        request.setReceiverName("Người Khác");assertThrows(IllegalStateException.class,()->drafts.addDraft(request,customer.getUsername()));
        assertEquals(1,drafts.countDrafts(customer.getUsername()));assertEquals(0,drafts.countDrafts(other.getUsername()));
    }
    @Test void directTokenCannotSwitchToCartAndOldOrdersRemainIdempotent() {
        var request=form();var order=orderService.createOrder(request,customer.getUsername());
        assertThrows(IllegalStateException.class,()->drafts.addDraft(request,customer.getUsername()));
        // Đơn cũ trước migration chưa có bản ghi dấu vết, vẫn phải retry về đúng đơn.
        submissions.delete(submissions.findByToken(request.getSubmissionToken()).orElseThrow());
        assertEquals(order.getId(),orderService.createOrder(request,customer.getUsername()).getId());
    }
    @Test void failedCartValidationRollsBackReservationAndAllowsCorrectedRetry() {
        var request=form();request.setPromoCode("MISSINGCODE");
        assertThrows(IllegalStateException.class,()->drafts.addDraft(request,customer.getUsername()));
        assertTrue(submissions.findByToken(request.getSubmissionToken()).isEmpty());
        request.setPromoCode(null);assertNotNull(drafts.addDraft(request,customer.getUsername()).getId());
    }
    @Test void retryAtCartLimitSucceedsButNewFormsAreRejected() {
        var first=form();Long firstId=drafts.addDraft(first,customer.getUsername()).getId();
        for(int i=1;i<30;i++)drafts.addDraft(form(),customer.getUsername());
        assertEquals(firstId,drafts.addDraft(first,customer.getUsername()).getId());
        var extra=form();assertThrows(IllegalStateException.class,()->drafts.addDraft(extra,customer.getUsername()));
        assertTrue(submissions.findByToken(extra.getSubmissionToken()).isEmpty());assertEquals(30,drafts.countDrafts(customer.getUsername()));
    }
    @Test void cartHttpPostRequiresTokenAndDuplicatePostRedirectsWithoutDuplicating() throws Exception {
        var request=form();
        for(int i=0;i<3;i++) {
            var post=post("/nguoi-dung/tao-don/them-vao-gio").with(user(customer.getUsername()).roles("USER")).with(csrf())
                .param("senderName",request.getSenderName()).param("senderAddress",request.getSenderAddress()).param("receiverName",request.getReceiverName())
                .param("pickupProvinceCode","79").param("pickupAreaCode","SGN-THUDUC").param("deliveryProvinceCode","79").param("deliveryAreaCode","SGN-THUDUC")
                .param("receiverAddress",request.getReceiverAddress()).param("receiverPhone",request.getReceiverPhone()).param("weightKg","1").param("serviceType","STANDARD").param("paymentMethod","COD");
            if(i==0)mvc.perform(post).andExpect(status().isOk()).andExpect(model().attributeHasFieldErrors("form","submissionToken"));
            else mvc.perform(post.param("submissionToken",request.getSubmissionToken())).andExpect(redirectedUrl("/nguoi-dung/tao-don"));
        }
        assertEquals(1,drafts.countDrafts(customer.getUsername()));
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
        vn.edu.hcmute.uteexpress.support.RoutingFixtures.fill(form);
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
