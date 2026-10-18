package vn.edu.hcmute.uteexpress.service.manager.impl;

import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.Page;
import vn.edu.hcmute.uteexpress.dto.manager.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.repository.admin.StaffAccountRepository;
import vn.edu.hcmute.uteexpress.repository.manager.ManagerOrderReadRepository;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccessPolicy;
import vn.edu.hcmute.uteexpress.service.manager.OperationsOverviewService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StaffWorkbenchServiceImplTest {
    private final ManagerOrderReadRepository orders = mock(ManagerOrderReadRepository.class);
    private final OperationsOverviewService overview = mock(OperationsOverviewService.class);
    private StaffWorkbenchServiceImpl service;
    private StaffAccessPolicy access;
    @BeforeEach void setUp() {
        access = spy(new StaffAccessPolicy(mock(StaffAccountRepository.class), Validation.buildDefaultValidatorFactory().getValidator()));
        doNothing().when(access).requireOperator("manager01"); service = new StaffWorkbenchServiceImpl(access, overview, orders);
    }
    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {" ", ",UTE1", "UTE1,", "UTE1,,UTE2", "UTE1;;UTE2", "UTE1\n\nUTE2", "UTE1<script>", "UTE 1", "a123456789012345678901", "UTE1/../../admin", "UTE1\t", "UTE1\u0000"})
    void invalidInputDoesNotQueryDatabase(String codes) {
        // strip() loại khoảng trắng cuối hợp lệ, còn ký tự điều khiển nhúng không hợp lệ.
        if ("UTE1\t".equals(codes)) codes = "UT\tE1";
        var form = form(codes);
        assertThrows(IllegalArgumentException.class, () -> service.findShipments("manager01", form));
        verifyNoInteractions(orders);
    }
    @Test void enforcesTenCodesBeforeDeduplicationAndLengthBeforeSplit() {
        assertThrows(IllegalArgumentException.class, () -> service.findShipments("manager01", form(String.join(",", Collections.nCopies(11, "UTE1")))));
        assertThrows(IllegalArgumentException.class, () -> service.findShipments("manager01", form("a".repeat(501))));
        verifyNoInteractions(orders);
    }
    @Test void deduplicatesNormalizesAndPreservesRequestedOrderIncludingMissing() {
        var order = new Order(); order.setTrackingCode("UTE2"); order.setStatus(Order.OrderStatus.PENDING_PICKUP);
        order.setServiceType(Order.ServiceType.STANDARD); order.setShippingFee(new BigDecimal("30000"));
        when(orders.findByTrackingCodeIn(anyCollection())).thenReturn(List.of(order));
        var rows = service.findShipments("manager01", form("ute2\r\n ute1 ;UTE2"));
        assertEquals(List.of("UTE2", "UTE1"), rows.stream().map(BatchTrackingResponse::trackingCode).toList());
        assertTrue(rows.get(0).found()); assertFalse(rows.get(1).found());
        verify(orders).findByTrackingCodeIn(new LinkedHashSet<>(List.of("UTE2", "UTE1")));
    }
    @Test void tenCodesAndUnknownCodeAreValid() {
        when(orders.findByTrackingCodeIn(anyCollection())).thenReturn(List.of());
        assertEquals(10, service.findShipments("manager01", form("A1,A2,A3,A4,A5,A6,A7,A8,A9,A10")).size());
    }
    @Test void csvNeutralizesFormulaPrefixAndContainsNoContactData() {
        when(orders.findByTrackingCodeIn(anyCollection())).thenReturn(List.of());
        String csv = service.exportShipments("manager01", form("-UTE1"));
        assertTrue(csv.startsWith("\uFEFF")); assertTrue(csv.contains("\"'-UTE1\""));
        assertFalse(csv.contains("receiverName")); assertFalse(csv.contains("phone"));
    }
    @Test void queuesUseSharedPoliciesAndRealTotals() {
        when(overview.alerts(anyString(), any(), eq(0))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(new Order())));
        when(overview.lateComplaints(anyString(), eq(0))).thenReturn(Page.empty());
        var queues = service.getWorkQueues("manager01"); assertEquals(6, queues.size());
        assertEquals(1, queues.get(0).count()); assertEquals(0, queues.get(5).count());
    }
    @Test void nonStaffCannotSearchExportOrReadQueues() {
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.findShipments("user01", form("UTE1")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.exportShipments("user01", form("UTE1")));
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.getWorkQueues("user01"));
        verifyNoInteractions(orders, overview);
    }
    private static BatchTrackingRequest form(String codes) { var form = new BatchTrackingRequest(); form.setCodes(codes); return form; }
}
