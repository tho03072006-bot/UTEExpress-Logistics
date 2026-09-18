package vn.edu.hcmute.uteexpress.service.manager.impl;

import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderReadRepository;
import vn.edu.hcmute.uteexpress.entity.Order;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ManagerOrderServiceImplTest {
    @Mock ManagerOrderReadRepository orders;
    private ManagerOrderServiceImpl service;
    private final LocalDate today = LocalDate.of(2026, 9, 18);
    @BeforeEach void setUp() {
        service = new ManagerOrderServiceImpl(orders, Clock.fixed(Instant.parse("2026-09-18T05:00:00Z"), ZoneOffset.UTC));
    }
    @Test void includesEntireEndDateAndStablePagination() {
        when(orders.findOrders(anyString(), any(), any(), any(), any())).thenReturn(Page.empty());
        assertTrue(service.findOrders(" UTE123 ", Order.OrderStatus.PENDING_PICKUP, today.minusDays(29), today, 0).isEmpty());
        verify(orders).findOrders(eq("UTE123"), eq(Order.OrderStatus.PENDING_PICKUP),
                eq(today.minusDays(29).atStartOfDay()), eq(today.plusDays(1).atStartOfDay()),
                eq(PageRequest.of(0, 20, Sort.by("createdAt").descending().and(Sort.by("id").descending()))));
    }
    @Test void rejectsReversedDates() {
        assertThrows(IllegalArgumentException.class, () -> service.findOrders("", null, today, today.minusDays(1), 0));
        verifyNoInteractions(orders);
    }
    @Test void rejectsFutureDate() {
        assertThrows(IllegalArgumentException.class, () -> service.findOrders("", null, today, today.plusDays(1), 0));
    }
    @Test void rejectsExcessivelyWideRange() {
        assertThrows(IllegalArgumentException.class, () -> service.findOrders("", null, today.minusDays(366), today, 0));
    }
    @Test void rejectsInvalidPage() {
        assertThrows(IllegalArgumentException.class, () -> service.findOrders("", null, today, today, -1));
        assertThrows(IllegalArgumentException.class, () -> service.findOrders("", null, today, today, 10001));
    }
    @Test void rejectsInvalidTrackingCode() {
        assertThrows(IllegalArgumentException.class, () -> service.findOrders("' OR 1=1 --", null, today, today, 0));
        assertThrows(IllegalArgumentException.class, () -> service.findOrders("A".repeat(21), null, today, today, 0));
    }
    @Test void returnsSafeDtoWhenShipperAndFeeAreMissing() {
        Order order = new Order(); order.setTrackingCode("UTE123");
        when(orders.findOrders(anyString(), any(), any(), any(), any())).thenReturn(new PageImpl<>(java.util.List.of(order)));
        var result = service.findOrders("", null, today, today, 0).getContent().get(0);
        assertEquals("Chưa phân công", result.shipperName()); assertNull(result.shippingFee());
    }
}

