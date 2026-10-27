package vn.edu.hcmute.uteexpress.tracking;

import jakarta.persistence.EntityManagerFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.OrderComplaint;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.service.tracking.OrderComplaintService;
import static org.junit.jupiter.api.Assertions.*;

/** Giao dịch commit thật trên H2 để kiểm tra đổi người phụ trách, mở lại và cache của request cũ. */
@SpringBootTest(classes=vn.edu.hcmute.uteexpress.support.IsolatedLogisticsTestConfiguration.class,properties={
    "spring.datasource.url=jdbc:h2:mem:complaint-regression;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000;INIT=SET SESSION CHARACTERISTICS AS TRANSACTION ISOLATION LEVEL SNAPSHOT",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.mail.order-updates.enabled=false"})
@ActiveProfiles("isolated-logistics")
class ComplaintWorkflowRegressionIntegrationTest {
    static final AtomicInteger SEQUENCE=new AtomicInteger();
    @Autowired AppUserRepository users;
    @Autowired OrderRepository orders;
    @Autowired OrderComplaintRepository complaints;
    @Autowired StaffAccountRepository staff;
    @Autowired OrderComplaintService service;
    @Autowired EntityManagerFactory entityManagers;
    AppUser customer,shipper,otherShipper,manager,otherManager,admin;
    Order order;
    @BeforeEach void seed(@Autowired javax.sql.DataSource source) throws Exception {
        try(var c=source.getConnection()) { assertTrue(c.getMetaData().getURL().startsWith("jdbc:h2:mem:")); }
        customer=account(AppUser.Role.USER);shipper=account(AppUser.Role.SHIPPER);otherShipper=account(AppUser.Role.SHIPPER);
        manager=account(AppUser.Role.MANAGER);otherManager=account(AppUser.Role.MANAGER);admin=account(AppUser.Role.ADMIN);
        order=new Order();order.setSender(customer);order.setShipper(shipper);order.setTrackingCode("UTECX"+SEQUENCE.incrementAndGet());
        order.setSenderName("Kiểm thử người gửi");order.setReceiverName("Kiểm thử người nhận");order.setReceiverAddress("Địa chỉ kiểm thử");
        orders.saveAndFlush(order);
    }
    AppUser account(AppUser.Role role) {
        String name="cx-test-"+SEQUENCE.incrementAndGet();var user=new AppUser(name,"test-only-hash",name+"@test.invalid");
        user.setRole(role);user.setEnabled(true);user=users.saveAndFlush(user);
        if(role==AppUser.Role.MANAGER || role==AppUser.Role.ADMIN) {
            var internal=new StaffAccount();internal.setUser(user);internal.setActive(true);internal.setPasswordChangeRequired(false);internal.setIssuedBy("test-only");staff.saveAndFlush(internal);
        }
        return user;
    }
    OrderComplaint create() {
        service.create(order.getId(),customer.getUsername(),"Kiểm thử khiếu nại","Cần tra soát kiện kiểm thử",null);
        return current();
    }
    OrderComplaint current() { return complaints.findByOrder(order).orElseThrow(); }
    void preloaded(OrderComplaint complaint,Runnable competing,Runnable cached) {
        var em=entityManagers.createEntityManager();
        try {
            em.getTransaction().begin();var old=em.find(OrderComplaint.class,complaint.getId());old.getOrder().getShipper().getUsername();em.getTransaction().commit();
            competing.run();
            TransactionSynchronizationManager.bindResource(entityManagers,new EntityManagerHolder(em));
            try { cached.run(); } finally { TransactionSynchronizationManager.unbindResource(entityManagers); }
        } finally { em.close(); }
    }
    @ParameterizedTest @EnumSource(OrderComplaint.Status.class)
    void customerCanContinueEveryComplaintStateWithoutLosingItsHistory(OrderComplaint.Status status) {
        var complaint=create();
        if(status!=OrderComplaint.Status.OPEN) service.respond(complaint.getId(),manager.getUsername(),complaint.getRevision(),status,"Phản hồi kiểm thử ban đầu");
        long before=service.timeline(complaint.getId(),customer.getUsername(),null).getTotalElements();
        service.customerMessage(complaint.getId(),customer.getUsername(),"Kiểm thử: tôi cần kiểm tra thêm");
        assertEquals(status==OrderComplaint.Status.OPEN ? status : OrderComplaint.Status.IN_PROGRESS,current().getStatus());
        assertEquals(before+1,service.timeline(complaint.getId(),customer.getUsername(),null).getTotalElements());
        if(status!=OrderComplaint.Status.OPEN) assertEquals(manager.getId(),current().getHandledBy().getId());
    }
    @ParameterizedTest @EnumSource(OrderComplaint.Status.class)
    void shipperSupplementRespectsOpenAndClosedStates(OrderComplaint.Status status) {
        var complaint=create();
        if(status!=OrderComplaint.Status.OPEN) service.respond(complaint.getId(),manager.getUsername(),complaint.getRevision(),status,"Phản hồi kiểm thử");
        var now=current();
        if(status.isClosed()) assertThrows(IllegalArgumentException.class,()->service.supplement(now.getId(),shipper.getUsername(),now.getRevision(),"Thông tin kiểm thử"));
        else { service.supplement(now.getId(),shipper.getUsername(),now.getRevision(),"Thông tin kiểm thử");assertEquals("Thông tin kiểm thử",current().getShipperNote()); }
    }
    @Test void preloadedCustomerMessageReopensLatestResolvedComplaint() {
        var complaint=create();
        preloaded(complaint,()->service.respond(complaint.getId(),manager.getUsername(),complaint.getRevision(),OrderComplaint.Status.RESOLVED,"Đã tra soát kiểm thử"),
                ()->service.customerMessage(complaint.getId(),customer.getUsername(),"Kiểm thử mở lại sau khi kết thúc"));
        assertEquals(OrderComplaint.Status.IN_PROGRESS,current().getStatus());assertEquals(manager.getId(),current().getHandledBy().getId());
        assertEquals(3,service.timeline(complaint.getId(),customer.getUsername(),null).getTotalElements());
    }
    @Test void reassignedShipperCannotSupplementUsingPreloadedOrder() {
        var complaint=create();
        preloaded(complaint,()->{var moved=orders.findById(order.getId()).orElseThrow();moved.setShipper(otherShipper);orders.saveAndFlush(moved);},
                ()->assertThrows(AccessDeniedException.class,()->service.supplement(complaint.getId(),shipper.getUsername(),complaint.getRevision(),"Thông tin từ người không còn giữ đơn")));
        assertNull(current().getShipperNote());
    }
    @Test void staleRevisionReturnsValidationErrorBeforeWritingAnotherResponse() {
        var complaint=create();
        preloaded(complaint,()->service.claim(complaint.getId(),manager.getUsername(),complaint.getRevision()),
                ()->assertThrows(IllegalArgumentException.class,()->service.respond(complaint.getId(),manager.getUsername(),complaint.getRevision(),OrderComplaint.Status.RESOLVED,"Phản hồi từ form cũ")));
        assertEquals(OrderComplaint.Status.IN_PROGRESS,current().getStatus());assertEquals(2,service.timeline(complaint.getId(),customer.getUsername(),null).getTotalElements());
    }
    @Test void anotherManagerCannotRespondButAdminCanTakeOver() {
        var complaint=create();service.claim(complaint.getId(),manager.getUsername(),complaint.getRevision());
        assertThrows(AccessDeniedException.class,()->service.respond(complaint.getId(),otherManager.getUsername(),current().getRevision(),OrderComplaint.Status.RESOLVED,"Phản hồi sai người"));
        service.claim(complaint.getId(),admin.getUsername(),current().getRevision());
        service.respond(complaint.getId(),admin.getUsername(),current().getRevision(),OrderComplaint.Status.RESOLVED,"Kiểm thử Admin tiếp quản");
        assertEquals(admin.getId(),current().getHandledBy().getId());assertEquals(OrderComplaint.Status.RESOLVED,current().getStatus());
    }
    @Test void timelineDefaultsToLastPageAndPreservesAllMessages() {
        var complaint=create();for(int i=0;i<55;i++) service.customerMessage(complaint.getId(),customer.getUsername(),"Kiểm thử tin nhắn "+i);
        var page=service.timeline(complaint.getId(),customer.getUsername(),null);
        assertEquals(1,page.getNumber());assertEquals(56,page.getTotalElements());assertTrue(page.getContent().stream().anyMatch(x->x.getMessage().endsWith("54")));
    }
    @Test void concurrentCreateProducesExactlyOneComplaint() throws Exception {
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        Callable<Integer> call=()->{start.await();try{create();return 1;}catch(IllegalArgumentException expected){return 0;}};
        try {var first=pool.submit(call);var second=pool.submit(call);start.countDown();assertEquals(1,first.get(15,TimeUnit.SECONDS)+second.get(15,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
        assertEquals(1,service.timeline(current().getId(),customer.getUsername(),null).getTotalElements());
    }
    @Test void twoManagersClaimingAtTheSameTimeDoNotOverwriteEachOther() throws Exception {
        var complaint=create();var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        java.util.function.Function<AppUser,Callable<Integer>> claim=actor->()->{
            start.await();try{service.claim(complaint.getId(),actor.getUsername(),complaint.getRevision());return 1;}
            catch(IllegalArgumentException expected){return 0;}
        };
        try {var first=pool.submit(claim.apply(manager));var second=pool.submit(claim.apply(otherManager));start.countDown();
            assertEquals(1,first.get(15,TimeUnit.SECONDS)+second.get(15,TimeUnit.SECONDS));}
        finally{pool.shutdownNow();}
        assertEquals(OrderComplaint.Status.IN_PROGRESS,current().getStatus());
        assertEquals(2,service.timeline(complaint.getId(),customer.getUsername(),null).getTotalElements());
    }
}
