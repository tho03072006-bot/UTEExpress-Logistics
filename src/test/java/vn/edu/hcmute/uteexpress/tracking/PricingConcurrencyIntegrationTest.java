package vn.edu.hcmute.uteexpress.tracking;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.edu.hcmute.uteexpress.dto.admin.TariffScheduleRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.service.PromoCodeService;
import vn.edu.hcmute.uteexpress.service.admin.*;
import vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** H2 SNAPSHOT mô phỏng read view toàn giao dịch của InnoDB; H2 REPEATABLE_READ vẫn cho phantom read. */
@SpringBootTest(classes=IsolatedLogisticsTestConfiguration.class,properties={
    "spring.datasource.url=jdbc:h2:mem:pricing-deep-audit;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;INIT=SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL SNAPSHOT",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.mail.order-updates.enabled=false"})
@ActiveProfiles("isolated-logistics")
class PricingConcurrencyIntegrationTest {
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired ShippingTariffRepository tariffs;
    @Autowired ShippingTariffVersionRepository versions;
    @Autowired TariffVersionService schedules;
    @Autowired PromoCodeRepository promotions;
    @Autowired PromoCodeService promoService;
    @Autowired PlatformTransactionManager transactions;
    @Autowired Clock clock;
    @Autowired DataSource source;
    @Autowired jakarta.persistence.EntityManager em;
    @SpyBean StaffAccessPolicy access;
    AppUser admin;

    @BeforeEach void seed() {
        versions.deleteAll();
        admin=users.findByUsername("pricing-race-admin").orElseGet(()->{
            var user=new AppUser("pricing-race-admin","test-only-hash","pricing-race-admin@test.invalid");
            user.setRole(AppUser.Role.ADMIN);user.setEnabled(true);return users.saveAndFlush(user);
        });
        if(staff.findByUserUsername(admin.getUsername()).isEmpty()) {
            var account=new StaffAccount();account.setUser(admin);account.setActive(true);account.setPasswordChangeRequired(false);account.setIssuedBy("test-only");staff.saveAndFlush(account);
        }
        if(tariffs.findByServiceType(Order.ServiceType.STANDARD).isEmpty()) {
            var tariff=new ShippingTariff();tariff.setServiceType(Order.ServiceType.STANDARD);tariff.setBaseFee(new BigDecimal("10000"));tariff.setFeePerKg(new BigDecimal("1000"));tariffs.saveAndFlush(tariff);
        }
    }
    @AfterEach void clearSpy() { reset(access); }
    TariffScheduleRequest request(LocalDateTime start,LocalDateTime end) {
        return new TariffScheduleRequest(Order.ServiceType.STANDARD,new BigDecimal("12000"),new BigDecimal("1500"),start,end,"Lịch giá kiểm thử đồng thời");
    }
    int schedule(TariffScheduleRequest form) {
        try { schedules.schedule(admin.getUsername(),form);return 1; }
        catch(StaffConflictException conflict) { assertTrue(conflict.getMessage().contains("trùng"));return 0; }
    }
    int simultaneousSchedules(TariffScheduleRequest first,TariffScheduleRequest second) throws Exception {
        var barrier=new CyclicBarrier(2);
        doAnswer(call->{call.callRealMethod();barrier.await(10,TimeUnit.SECONDS);return null;}).when(access).requireAdmin(anyString());
        var executor=Executors.newFixedThreadPool(2);
        try {
            var a=executor.submit(()->schedule(first));var b=executor.submit(()->schedule(second));
            return a.get(20,TimeUnit.SECONDS)+b.get(20,TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
    @Test void simultaneousOverlappingSchedulesCannotCreateAmbiguousEffectivePrice() throws Exception {
        try(var connection=source.getConnection()) { assertEquals(6,connection.getTransactionIsolation()); } // H2 SNAPSHOT
        var start=LocalDateTime.now(clock).plusDays(3).withSecond(0).withNano(0);var end=start.plusDays(2);
        assertEquals(1,simultaneousSchedules(request(start,end),request(start.plusHours(1),end.plusHours(1))));
        assertEquals(1,versions.active(Order.ServiceType.STANDARD,start.plusDays(1)).size());
    }
    @Test void adjacentSchedulesAllowTwoPricesWithAnExclusiveEndBoundary() throws Exception {
        var start=LocalDateTime.now(clock).plusDays(3).withSecond(0).withNano(0);var middle=start.plusDays(1);var end=middle.plusDays(1);
        assertEquals(2,simultaneousSchedules(request(start,middle),request(middle,end)));
        assertEquals(1,versions.active(Order.ServiceType.STANDARD,middle).size());
        assertEquals(1,versions.active(Order.ServiceType.STANDARD,middle.minusNanos(1000)).size());
        assertTrue(versions.active(Order.ServiceType.STANDARD,end).isEmpty());
    }
    @Test void scheduleRejectsHiddenSubMinuteBoundariesAndDoesNotPersistRoundedIntervals() {
        var start=LocalDateTime.now(clock).plusDays(3).withSecond(0).withNano(0);
        assertThrows(IllegalArgumentException.class,()->schedules.schedule(admin.getUsername(),request(start.plusNanos(1),start.plusNanos(2))));
        assertThrows(IllegalArgumentException.class,()->schedules.schedule(admin.getUsername(),request(start.plusSeconds(1),start.plusHours(1))));
        assertEquals(0,versions.count());
    }
    @Test void simultaneousUseOfLastPromotionAllowanceCommitsOnlyOneDiscount() throws Exception {
        var promo=new PromoCode();promo.setCode("LAST"+System.nanoTime());promo.setDiscountType(PromoCode.DiscountType.FIXED_AMOUNT);
        promo.setDiscountValue(new BigDecimal("1000"));promo.setUsageLimit(1);promotions.saveAndFlush(promo);
        var barrier=new CyclicBarrier(2);var executor=Executors.newFixedThreadPool(2);
        Callable<Integer> consume=()->{
            try { return new TransactionTemplate(transactions).execute(status->{
                em.createQuery("select count(u) from AppUser u",Long.class).getSingleResult();
                try { barrier.await(10,TimeUnit.SECONDS); } catch(Exception failure) { throw new IllegalStateException(failure); }
                var current=promoService.requireUsableCode(promo.getCode(),new BigDecimal("10000"));promoService.markUsed(current);return 1;
            }); } catch(IllegalStateException failure) { assertTrue(failure.getMessage().contains("hết lượt"));return 0; }
            catch(org.springframework.dao.PessimisticLockingFailureException conflict) { return 0; }
        };
        try { var a=executor.submit(consume);var b=executor.submit(consume);assertEquals(1,a.get(20,TimeUnit.SECONDS)+b.get(20,TimeUnit.SECONDS)); }
        finally { executor.shutdownNow(); }
        assertEquals(1,promotions.findById(promo.getId()).orElseThrow().getUsedCount());
    }
}
