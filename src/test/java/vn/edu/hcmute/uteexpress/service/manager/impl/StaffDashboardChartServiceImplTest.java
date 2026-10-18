package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.math.BigDecimal;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.hcmute.uteexpress.entity.Order.OrderStatus;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderStatisticsRepository;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderStatisticsRepository.*;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccessPolicy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class StaffDashboardChartServiceImplTest {
    @Mock ManagerOrderStatisticsRepository orders;
    @Mock StaffAccessPolicy access;
    private StaffDashboardChartServiceImpl service;
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    @BeforeEach void setUp() {
        // UTC còn ngày 18 nhưng Việt Nam đã sang ngày 19.
        var clock = Clock.fixed(Instant.parse("2026-10-18T18:30:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        service = new StaffDashboardChartServiceImpl(orders, access, clock);
    }

    @Test void fillsExactlyFourteenDaysAndUsesVietnamMidnightBoundaries() {
        when(orders.countGroupedStatuses()).thenReturn(List.of(new StatusRow(OrderStatus.DELIVERED, 9)));
        when(orders.countCreatedByDay(any(),any())).thenReturn(List.of(new DailyRow(2026,10,6,2),new DailyRow(2026,10,19,4)));
        var chart = service.getOperationsCharts("manager01");
        assertEquals(TODAY.minusDays(13), chart.fromDate()); assertEquals(TODAY, chart.toDate());
        assertEquals(14,chart.days().size()); assertEquals(6,chart.totalNewOrders()); assertEquals(4,chart.peakDailyOrders());
        assertEquals(new BigDecimal("50.00"),chart.days().get(0).heightPercentage());
        assertEquals(BigDecimal.ZERO.setScale(2),chart.days().get(1).heightPercentage());
        assertEquals(new BigDecimal("100.00"),chart.days().get(13).heightPercentage());
        verify(orders).countCreatedByDay(TODAY.minusDays(13).atStartOfDay(), TODAY.plusDays(1).atStartOfDay());
        verify(access).requireOperator("manager01");
    }

    @Test void includesAllStatusesAndDoesNotConfuseFailedWithReturnedOrSuccessRate() {
        when(orders.countGroupedStatuses()).thenReturn(List.of(new StatusRow(OrderStatus.DELIVERED,2),
                new StatusRow(OrderStatus.FAILED,1),new StatusRow(OrderStatus.RETURNED,1)));
        var chart = service.getAdminCharts("admin01");
        assertEquals(4,chart.totalOrders()); assertEquals(OrderStatus.values().length,chart.orderStatuses().size());
        var delivered = chart.orderStatuses().stream().filter(row -> row.code().equals("DELIVERED")).findFirst().orElseThrow();
        assertEquals(new BigDecimal("50.00"),delivered.percentage());
        assertEquals(2,chart.orderStatuses().stream().filter(row -> row.count()==1).count());
        verify(access).requireAdmin("admin01");
    }

    @Test void emptyDatabaseProducesZerosNotDivisionByZeroOrFakeData() {
        var chart = service.getOperationsCharts("manager01");
        assertEquals(0,chart.totalOrders()); assertEquals(0,chart.totalNewOrders()); assertEquals(0,chart.peakDailyOrders());
        assertEquals(14,chart.days().size()); assertTrue(chart.days().stream().allMatch(row -> row.heightPercentage().signum()==0));
        assertTrue(chart.orderStatuses().stream().allMatch(row -> row.percentage().signum()==0));
    }

    @Test void usesDecimalArithmeticWithoutMultiplyingLongCounts() {
        when(orders.countGroupedStatuses()).thenReturn(List.of(new StatusRow(OrderStatus.DELIVERED,Long.MAX_VALUE)));
        assertEquals(new BigDecimal("100.00"),service.getAdminCharts("admin01").orderStatuses().get(3).percentage());
    }

    @Test void cannotWrapTotalOrderCountsOnOverflow() {
        when(orders.countGroupedStatuses()).thenReturn(List.of(new StatusRow(OrderStatus.DELIVERED,Long.MAX_VALUE),new StatusRow(OrderStatus.FAILED,1)));
        assertThrows(ArithmeticException.class,() -> service.getOperationsCharts("manager01"));
    }

    @Test void rejectsNegativeAndDuplicateStatusGroups() {
        when(orders.countGroupedStatuses()).thenReturn(List.of(new StatusRow(OrderStatus.FAILED,-1)));
        assertThrows(IllegalStateException.class,() -> service.getAdminCharts("admin01"));
        when(orders.countGroupedStatuses()).thenReturn(List.of(new StatusRow(OrderStatus.FAILED,1),new StatusRow(OrderStatus.FAILED,2)));
        assertThrows(IllegalStateException.class,() -> service.getAdminCharts("admin01"));
    }

    @Test void rejectsUnknownStatusInsteadOfSilentlyDroppingOrders() {
        when(orders.countGroupedStatuses()).thenReturn(List.of(new StatusRow(null,1)));
        assertThrows(IllegalStateException.class,() -> service.getOperationsCharts("manager01"));
    }

    @Test void rejectsNegativeDuplicateOrOutOfWindowDailyRows() {
        for (var rows : List.of(List.of(new DailyRow(2026,10,19,-1)),
                List.of(new DailyRow(2026,10,19,1),new DailyRow(2026,10,19,2)),
                List.of(new DailyRow(2026,10,5,1)),List.of(new DailyRow(2026,10,20,1)))) {
            when(orders.countCreatedByDay(any(),any())).thenReturn(List.copyOf(rows));
            assertThrows(IllegalStateException.class,() -> service.getOperationsCharts("manager01"));
        }
    }

    @Test void failedPermissionChecksNeverQueryOrderData() {
        doThrow(new AccessDeniedException("denied")).when(access).requireOperator("public-user");
        doThrow(new AccessDeniedException("denied")).when(access).requireAdmin("manager01");
        assertThrows(AccessDeniedException.class,() -> service.getOperationsCharts("public-user"));
        assertThrows(AccessDeniedException.class,() -> service.getAdminCharts("manager01"));
        verifyNoInteractions(orders);
    }

    @Test void doesNotTurnDatabaseFailureIntoEmptyData() {
        when(orders.countGroupedStatuses()).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThrows(DataAccessResourceFailureException.class,() -> service.getOperationsCharts("manager01"));
    }

    @Test void responseCollectionsCannotBeModified() {
        var chart = service.getOperationsCharts("manager01");
        assertThrows(UnsupportedOperationException.class,() -> chart.days().clear());
        assertThrows(UnsupportedOperationException.class,() -> chart.orderStatuses().clear());
    }

    @Test void barLabelsAreCompactButExactCountsRemainAvailable() {
        for (var entry : java.util.Map.of(0L,"0",999L,"999",1500L,"1,5k",2500000L,"2,5tr",1200000000L,"1,2tỷ").entrySet()) {
            var day = new vn.edu.hcmute.uteexpress.dto.manager.DashboardChartsResponse.DayResponse(TODAY,entry.getKey(),BigDecimal.ZERO);
            assertEquals(entry.getValue(),day.getDisplayCount()); assertEquals(entry.getKey().longValue(),day.count());
        }
    }

    private record StatusRow(OrderStatus getStatus, long getTotal) implements StatusCount { }
    private record DailyRow(int getYear, int getMonth, int getDay, long getTotal) implements DayCount { }
}
