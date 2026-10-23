package vn.edu.hcmute.uteexpress.service.routing;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;
import vn.edu.hcmute.uteexpress.repository.OrderStatusHistoryRepository;
import vn.edu.hcmute.uteexpress.repository.routing.*;
import vn.edu.hcmute.uteexpress.service.admin.AuditService;
import vn.edu.hcmute.uteexpress.service.tracking.LogisticsAccess;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HubServiceTest {
    @Mock LogisticsAccess access;
    @Mock DispatchRoutingPolicy routing;
    @Mock BranchRepository branches;
    @Mock OrderRouteRepository routes;
    @Mock LinehaulTripRepository trips;
    @Mock LinehaulItemRepository items;
    @Mock OrderRepository orders;
    @Mock OrderStatusHistoryRepository history;
    @Mock AuditService audit;
    @Mock ApplicationEventPublisher events;
    @Mock vn.edu.hcmute.uteexpress.service.OrderPaymentService payments;
    @Mock jakarta.persistence.EntityManager entityManager;
    HubService hub;
    AppUser manager;
    Branch sgn, han, dad;

    @BeforeEach void setup() {
        hub = new HubService(access, routing, branches, routes, trips, items, orders, history, audit, events,
                Clock.fixed(Instant.parse("2026-10-23T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")), payments);
        ReflectionTestUtils.setField(hub,"entityManager",entityManager);
        manager = new AppUser("managerqa", "hash", "m@example.com"); manager.setId(7L); manager.setRole(AppUser.Role.MANAGER);
        sgn = branch(1L, "SGN"); han = branch(2L, "HAN"); dad = branch(3L, "DAD");
        lenient().when(access.internal("managerqa")).thenReturn(manager);
    }

    @Test void departMovesEveryItemToLinehaulAndSealsTrip() {
        var trip = trip(10L, sgn, han);
        var first = order(100L, "UTE00000100", sgn, han, OrderRoute.Stage.ORIGIN_HUB);
        var second = order(101L, "UTE00000101", sgn, han, OrderRoute.Stage.ORIGIN_HUB);
        when(trips.findLockedById(10L)).thenReturn(Optional.of(trip));
        when(routing.scope(manager, null)).thenReturn(1L);
        when(items.findAllByTrip_Id(10L)).thenReturn(List.of(new LinehaulItem(trip, first.getOrder()), new LinehaulItem(trip, second.getOrder())));
        stubRoute(first); stubRoute(second);
        hub.depart("managerqa", 10L, 0, "seal-001");
        assertEquals(LinehaulTrip.Status.DEPARTED, trip.getStatus());
        assertEquals("SEAL-001", trip.getSealCode());
        assertEquals(2, trip.getItemCount());
        assertEquals(OrderRoute.Stage.LINEHAUL, first.getStage());
        assertEquals(OrderRoute.Stage.LINEHAUL, second.getStage());
        verify(history, times(2)).save(any());
    }

    @Test void managerOfOtherBranchCannotDepartTrip() {
        var trip = trip(10L, sgn, han);
        when(trips.findLockedById(10L)).thenReturn(Optional.of(trip));
        when(routing.scope(manager, null)).thenReturn(2L);
        assertThrows(AccessDeniedException.class, () -> hub.depart("managerqa", 10L, 0, "SEAL-001"));
        assertEquals(LinehaulTrip.Status.LOADING, trip.getStatus());
        verifyNoInteractions(history, audit);
    }

    @Test void staleRevisionIsRejected() {
        var trip = trip(10L, sgn, han);
        when(trips.findLockedById(10L)).thenReturn(Optional.of(trip));
        assertThrows(IllegalStateException.class, () -> hub.depart("managerqa", 10L, 5, "SEAL-001"));
        verifyNoInteractions(history, audit);
    }

    @Test void missingItemNeedsNoteAndStaysOnLinehaul() {
        var trip = departed(11L, sgn, han, "SEAL-777");
        var first = order(200L, "UTE00000200", sgn, han, OrderRoute.Stage.LINEHAUL);
        var second = order(201L, "UTE00000201", sgn, han, OrderRoute.Stage.LINEHAUL);
        when(trips.findLockedById(11L)).thenReturn(Optional.of(trip));
        when(routing.scope(manager, null)).thenReturn(2L);
        when(items.findAllByTrip_Id(11L)).thenReturn(List.of(new LinehaulItem(trip, first.getOrder()), new LinehaulItem(trip, second.getOrder())));
        assertThrows(IllegalArgumentException.class, () -> hub.arrive("managerqa", 11L, 1, "SEAL-777", List.of(200L), ""));
        assertEquals(LinehaulTrip.Status.DEPARTED, trip.getStatus());

        stubRoute(first);
        int missing = hub.arrive("managerqa", 11L, 1, "SEAL-777", List.of(200L), "Kiện 201 không có trên xe, đã báo kho SGN");
        assertEquals(1, missing);
        assertEquals(OrderRoute.Stage.DEST_HUB, first.getStage());
        assertEquals(OrderRoute.Stage.LINEHAUL, second.getStage());
        assertEquals(LinehaulTrip.Status.ARRIVED, trip.getStatus());
        assertTrue(trip.getArrivalNote().contains("UTE00000201"));
    }

    @Test void sealMismatchWithoutNoteIsRejected() {
        var trip = departed(12L, sgn, han, "SEAL-777");
        var only = order(300L, "UTE00000300", sgn, han, OrderRoute.Stage.LINEHAUL);
        when(trips.findLockedById(12L)).thenReturn(Optional.of(trip));
        when(routing.scope(manager, null)).thenReturn(2L);
        when(items.findAllByTrip_Id(12L)).thenReturn(List.of(new LinehaulItem(trip, only.getOrder())));
        assertThrows(IllegalArgumentException.class, () -> hub.arrive("managerqa", 12L, 1, "SEAL-999", List.of(300L), "ngắn"));
        assertEquals(OrderRoute.Stage.LINEHAUL, only.getStage());
    }

    @Test void createTripRejectsOrderGoingToAnotherBranch() {
        var route = order(400L, "UTE00000400", sgn, han, OrderRoute.Stage.ORIGIN_HUB);
        when(routing.scope(manager, 1L)).thenReturn(1L);
        when(branches.findById(1L)).thenReturn(Optional.of(sgn));
        when(branches.findById(3L)).thenReturn(Optional.of(dad));
        when(trips.save(any())).thenAnswer(call -> call.getArgument(0));
        stubRoute(route);
        var error = assertThrows(IllegalArgumentException.class, () -> hub.createTrip("managerqa", 1L, 3L, "51C-123.45",
                "Nguyễn Văn Tài", "0901234567", List.of(400L)));
        assertTrue(error.getMessage().contains("không đi tới"));
        verify(items, never()).save(any());
    }

    @Test void inputFormatsAreValidated() {
        assertEquals("51C-123.45", HubService.plate("51c-123.45"));
        assertEquals("29H-1234", HubService.plate("29H-1234"));
        assertThrows(IllegalArgumentException.class, () -> HubService.plate("ABC"));
        assertEquals("0901234567", HubService.phone("0901 234 567"));
        assertThrows(IllegalArgumentException.class, () -> HubService.phone("12345"));
        assertThrows(IllegalArgumentException.class, () -> HubService.seal("ab"));
        assertThrows(IllegalArgumentException.class, () -> HubService.seal("SEAL 001"));
    }

    @Test void driverNameNeedsLettersRatherThanOnlyPunctuation() {
        prepareCreate(order(400L,"UTE00000400",sgn,han,OrderRoute.Stage.ORIGIN_HUB));
        assertThrows(IllegalArgumentException.class,()->hub.createTrip("managerqa",1L,2L,"51C-123.45","---","0901234567",List.of(400L)));
        verify(items,never()).save(any());
    }
    @Test void invalidIdsAreRejectedBeforeQueryingOrCreatingTrip() {
        prepareCreate(order(400L,"UTE00000400",sgn,han,OrderRoute.Stage.ORIGIN_HUB));
        assertThrows(IllegalArgumentException.class,()->hub.createTrip("managerqa",1L,2L,"51C-123.45","Nguyễn Văn An","0901234567",Arrays.asList((Long)null)));
        verifyNoInteractions(orders);verify(trips,never()).save(any());
    }
    @Test void moreThanFiveHundredRawIdsCannotBypassLimitThroughDuplicates() {
        prepareCreate(order(400L,"UTE00000400",sgn,han,OrderRoute.Stage.ORIGIN_HUB));
        assertThrows(IllegalArgumentException.class,()->hub.createTrip("managerqa",1L,2L,"51C-123.45","Nguyễn Văn An","0901234567",Collections.nCopies(501,400L)));
        verify(trips,never()).save(any());
    }
    @Test void terminalOrderCannotBeLoadedOntoTrip() {
        var route=order(400L,"UTE00000400",sgn,han,OrderRoute.Stage.ORIGIN_HUB);route.getOrder().setStatus(Order.OrderStatus.CANCELLED);
        prepareCreate(route);
        assertThrows(IllegalStateException.class,()->hub.createTrip("managerqa",1L,2L,"51C-123.45","Nguyễn Văn An","0901234567",List.of(400L)));
        verify(items,never()).save(any());
    }
    @Test void terminalOrderCannotBeDepartedOrReceived() {
        var trip=trip(10L,sgn,han);var route=order(400L,"UTE00000400",sgn,han,OrderRoute.Stage.ORIGIN_HUB);
        route.getOrder().setStatus(Order.OrderStatus.CANCELLED);
        when(trips.findLockedById(10L)).thenReturn(Optional.of(trip));when(routing.scope(manager,null)).thenReturn(1L);
        when(items.findAllByTrip_Id(10L)).thenReturn(List.of(new LinehaulItem(trip,route.getOrder())));
        stubRoute(route);
        assertThrows(IllegalStateException.class,()->hub.depart("managerqa",10L,0,"SEAL-001"));
        assertEquals(OrderRoute.Stage.ORIGIN_HUB,route.getStage());verifyNoInteractions(history);
    }
    @Test void driverPhoneAndSealCannotBeInvalidButCorrectLength() {
        assertThrows(IllegalArgumentException.class,()->HubService.phone("0101234567"));
        assertThrows(IllegalArgumentException.class,()->HubService.seal("----"));
    }
    @Test void validUnicodeNameInternationalPhoneAndTripCodeArePreserved() {
        prepareCreate(order(400L,"UTE00000400",sgn,han,OrderRoute.Stage.ORIGIN_HUB));
        var trip=hub.createTrip("managerqa",1L,2L,"51C-123.45","Nguyễn Văn An","+84 901 234 567",List.of(400L));
        assertEquals("Nguyễn Văn An",trip.getDriverName());assertEquals("0901234567",trip.getDriverPhone());
        assertTrue(trip.getCode().startsWith("TC-SGNHAN-"));assertTrue(trip.getCode().length()<=40);
    }
    @Test void cancelledOrderCannotBeReceivedAtDestination() {
        var trip=departed(10L,sgn,han,"SEAL-001");var route=order(400L,"UTE00000400",sgn,han,OrderRoute.Stage.LINEHAUL);
        route.getOrder().setStatus(Order.OrderStatus.CANCELLED);
        when(trips.findLockedById(10L)).thenReturn(Optional.of(trip));when(routing.scope(manager,null)).thenReturn(2L);
        when(items.findAllByTrip_Id(10L)).thenReturn(List.of(new LinehaulItem(trip,route.getOrder())));
        stubRoute(route);
        assertThrows(IllegalStateException.class,()->hub.arrive("managerqa",10L,1,"SEAL-001",List.of(400L),""));
        assertEquals(OrderRoute.Stage.LINEHAUL,route.getStage());assertEquals(LinehaulTrip.Status.DEPARTED,trip.getStatus());
        verifyNoInteractions(history);
    }
    @Test void invalidLateReceiptIdIsRejectedWithoutRepositoryQuery() {
        assertThrows(IllegalArgumentException.class,()->hub.receiveLate("managerqa",null,"Ghi chú nhận bổ sung."));
        verifyNoInteractions(items,trips,orders,history);
    }
    @Test void failedOrderWithoutReturnLegCannotBeLoadedOntoTrip() {
        var route=order(400L,"UTE00000400",sgn,han,OrderRoute.Stage.ORIGIN_HUB);
        route.getOrder().setStatus(Order.OrderStatus.FAILED);
        prepareCreate(route);
        assertThrows(IllegalStateException.class,()->hub.createTrip("managerqa",1L,2L,"51C-123.45","Nguyễn Văn An","0901234567",List.of(400L)));
        verify(items,never()).save(any());
    }
    @Test void returnLegWithNonFailedStatusCannotBeLoadedOntoTrip() {
        var route=order(400L,"UTE00000400",han,sgn,OrderRoute.Stage.LAST_MILE);
        route.beginReturn();
        prepareCreate(route);
        assertThrows(IllegalStateException.class,()->hub.createTrip("managerqa",1L,2L,"51C-123.45","Nguyễn Văn An","0901234567",List.of(400L)));
        verify(items,never()).save(any());
    }
    private void prepareCreate(OrderRoute route) {
        lenient().when(routing.scope(manager,1L)).thenReturn(1L);
        lenient().when(branches.findById(1L)).thenReturn(Optional.of(sgn));
        lenient().when(branches.findById(2L)).thenReturn(Optional.of(han));
        lenient().when(trips.save(any())).thenAnswer(call->call.getArgument(0));
        lenient().when(orders.findLockedById(route.getOrder().getId())).thenReturn(Optional.of(route.getOrder()));
        lenient().when(routing.route(route.getOrder())).thenReturn(route);
    }

    private void stubRoute(OrderRoute route) {
        when(orders.findLockedById(route.getOrder().getId())).thenReturn(Optional.of(route.getOrder()));
        when(routing.route(route.getOrder())).thenReturn(route);
    }

    private OrderRoute order(Long id, String code, Branch from, Branch to, OrderRoute.Stage stage) {
        var order = new Order(); order.setId(id); order.setTrackingCode(code); order.setStatus(Order.OrderStatus.PICKED_UP);
        var route = new OrderRoute(order, area(from), area(to));
        ReflectionTestUtils.setField(route, "stage", stage);
        return route;
    }

    private LinehaulTrip trip(Long id, Branch from, Branch to) {
        var trip = new LinehaulTrip("TC-" + id, from, to, "51C-123.45", "Tài xế", "0901234567");
        ReflectionTestUtils.setField(trip, "id", id);
        return trip;
    }

    private LinehaulTrip departed(Long id, Branch from, Branch to, String seal) {
        var trip = trip(id, from, to);
        trip.depart(seal, 2, manager, LocalDateTime.of(2026, 10, 23, 8, 0));
        ReflectionTestUtils.setField(trip, "revision", 1L);
        return trip;
    }

    private static ProvinceArea area(Branch branch) {
        return new ProvinceArea("79", branch.getCode() + "-A", "Khu " + branch.getCode(), branch, BigDecimal.ONE, BigDecimal.ONE);
    }

    private static Branch branch(Long id, String code) {
        var branch = new Branch(code, "Kho " + code, "79", "Địa chỉ", code + "-A", LocalDateTime.of(2026, 10, 1, 8, 0));
        ReflectionTestUtils.setField(branch, "id", id);
        return branch;
    }
}
