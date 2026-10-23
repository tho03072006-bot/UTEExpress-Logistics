package vn.edu.hcmute.uteexpress.tracking;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.*;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.OrderService;
import vn.edu.hcmute.uteexpress.service.routing.HubService;
import vn.edu.hcmute.uteexpress.support.*;
import static org.junit.jupiter.api.Assertions.*;

/** COMMIT/ROLLBACK và khóa thật trên H2, không tạo chuyến/đơn demo trên Aiven. */
@SpringBootTest(classes=IsolatedLogisticsTestConfiguration.class,properties={
    "spring.datasource.url=jdbc:h2:mem:hub-concurrency;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
    "spring.jpa.hibernate.ddl-auto=create-drop","app.mail.order-updates.enabled=false"})
@ActiveProfiles("isolated-logistics")
class HubConcurrencyIntegrationTest {
    static final AtomicInteger SEQUENCE=new AtomicInteger();
    @Autowired ApplicationContext context;
    @Autowired HubService hub;
    @Autowired OrderService orderService;
    @Autowired AppUserRepository users;
    @Autowired StaffAccountRepository staff;
    @Autowired StaffBranchRepository staffBranches;
    @Autowired BranchRepository branches;
    @Autowired LinehaulTripRepository trips;
    @Autowired LinehaulItemRepository items;
    @Autowired OrderRouteRepository routes;
    @Autowired OrderStatusHistoryRepository history;
    AppUser sender, originManager, destManager, otherManager;
    Branch origin,dest;

    @BeforeEach void seed() {
        RoutingFixtures.seedH2(context); // guard chỉ cho jdbc:h2:mem trước mọi thao tác ghi
        sender=account(AppUser.Role.USER);originManager=account(AppUser.Role.MANAGER);destManager=account(AppUser.Role.MANAGER);otherManager=account(AppUser.Role.MANAGER);
        origin=branches.findByCode("SGN").orElseThrow();dest=branches.findByCode("DAD").orElseThrow();
        internal(originManager,origin);internal(destManager,dest);internal(otherManager,branches.findByCode("HAN").orElseThrow());
    }
    AppUser account(AppUser.Role role) {
        String name="hubqa"+SEQUENCE.incrementAndGet();var user=new AppUser(name,"test-only-hash",name+"@test.invalid");
        user.setRole(role);user.setEnabled(true);user.setFullName("Tài khoản thử");return users.saveAndFlush(user);
    }
    void internal(AppUser user,Branch branch) {
        var account=new StaffAccount();account.setUser(user);account.setIssuedBy("h2-test-only");account.setPasswordChangeRequired(false);staff.saveAndFlush(account);
        staffBranches.saveAndFlush(new StaffBranch(user,branch,user,LocalDateTime.now()));
    }
    Order inOriginHub() {
        var form=orderService.prepareCreateForm(sender.getUsername());RoutingFixtures.fill(form);
        form.setDeliveryProvinceCode("48");form.setDeliveryAreaCode("DAD-HAICHAU");
        form.setSenderName("Người Gửi");form.setSenderAddress("Số 1 đường thử");form.setReceiverName("Người Nhận");
        form.setReceiverAddress("Số 2 đường thử");form.setReceiverPhone("0901234567");form.setWeightKg(1d);form.setPickupMode(OrderRoute.PickupMode.DROP_OFF);
        var order=orderService.createOrder(form,sender.getUsername());hub.receiveAtCounter(originManager.getUsername(),order.getId());return order;
    }
    LinehaulTrip create(Order... orders) {
        return hub.createTrip(originManager.getUsername(),origin.getId(),dest.getId(),"51C-123.45","Nguyễn Văn An","+84 901 234 567",
                Arrays.stream(orders).map(Order::getId).toList());
    }
    LinehaulTrip current(LinehaulTrip trip){return trips.findById(trip.getId()).orElseThrow();}

    @Test void fullTripLifecycleReceivesEveryItemAtCorrectDestination() {
        var first=inOriginHub();var second=inOriginHub();var trip=create(first,second);
        hub.depart(originManager.getUsername(),trip.getId(),trip.getRevision(),"SEAL-001");
        var departed=current(trip);assertEquals(LinehaulTrip.Status.DEPARTED,departed.getStatus());
        assertEquals(0,hub.arrive(destManager.getUsername(),trip.getId(),departed.getRevision(),"SEAL-001",List.of(first.getId(),second.getId()),"Nhận đủ hai kiện hàng."));
        assertEquals(LinehaulTrip.Status.ARRIVED,current(trip).getStatus());
        assertTrue(items.findAllByTrip_Id(trip.getId()).stream().allMatch(LinehaulItem::isReceived));
        assertEquals(OrderRoute.Stage.DEST_HUB,routes.findByOrder_Id(first.getId()).orElseThrow().getStage());
    }
    @Test void twoConcurrentManagersCannotLoadSameOrderOnTwoTrips() throws Exception {
        var order=inOriginHub();long before=trips.count();var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        Callable<Integer> action=()->{start.await();try{create(order);return 1;}catch(IllegalArgumentException ex){return 0;}};
        try {
            var first=executor.submit(action);var second=executor.submit(action);start.countDown();
            assertEquals(1,first.get(10,TimeUnit.SECONDS)+second.get(10,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
        assertEquals(before+1,trips.count());
    }
    @Test void twoLateReceiptsOnlyRecordOneMovementAndOneHistoryEntry() throws Exception {
        var order=inOriginHub();var trip=create(order);hub.depart(originManager.getUsername(),trip.getId(),trip.getRevision(),"SEAL-001");
        hub.arrive(destManager.getUsername(),trip.getId(),current(trip).getRevision(),"SEAL-001",List.of(),"Chưa tìm thấy kiện, đang kiểm tra.");
        var item=items.findAllByTrip_Id(trip.getId()).get(0);long before=history.count();
        var start=new CountDownLatch(1);var executor=Executors.newFixedThreadPool(2);
        Callable<Integer> action=()->{start.await();try{hub.receiveLate(destManager.getUsername(),item.getId(),"Đã tìm thấy kiện, nhận bổ sung.");return 1;}catch(IllegalStateException ex){return 0;}};
        try {
            var first=executor.submit(action);var second=executor.submit(action);start.countDown();
            assertEquals(1,first.get(10,TimeUnit.SECONDS)+second.get(10,TimeUnit.SECONDS));
        } finally {executor.shutdownNow();}
        assertEquals(before+1,history.count());assertTrue(items.findById(item.getId()).orElseThrow().isReceived());
        assertEquals(OrderRoute.Stage.DEST_HUB,routes.findByOrder_Id(order.getId()).orElseThrow().getStage());
    }
    @Test void invalidArrivalOrWrongBranchCannotPartiallyReceiveTrip() {
        var first=inOriginHub();var second=inOriginHub();var trip=create(first,second);
        hub.depart(originManager.getUsername(),trip.getId(),trip.getRevision(),"SEAL-001");long revision=current(trip).getRevision();
        assertThrows(AccessDeniedException.class,()->hub.arrive(otherManager.getUsername(),trip.getId(),revision,"SEAL-001",List.of(first.getId()),"Kiểm tra nhận kiện."));
        assertThrows(IllegalArgumentException.class,()->hub.arrive(destManager.getUsername(),trip.getId(),revision,"SEAL-001",List.of(first.getId(),Long.MAX_VALUE),"Kiểm tra nhận kiện."));
        assertEquals(LinehaulTrip.Status.DEPARTED,current(trip).getStatus());assertTrue(items.findAllByTrip_Id(trip.getId()).stream().noneMatch(LinehaulItem::isReceived));
        assertEquals(OrderRoute.Stage.LINEHAUL,routes.findByOrder_Id(first.getId()).orElseThrow().getStage());
    }
    @Test void staleRevisionCannotCancelTripAlreadyDeparted() {
        var trip=create(inOriginHub());hub.depart(originManager.getUsername(),trip.getId(),trip.getRevision(),"SEAL-001");
        assertThrows(IllegalStateException.class,()->hub.cancelTrip(originManager.getUsername(),trip.getId(),trip.getRevision()));
        assertEquals(LinehaulTrip.Status.DEPARTED,current(trip).getStatus());assertEquals(1,items.findAllByTrip_Id(trip.getId()).size());
    }
}
