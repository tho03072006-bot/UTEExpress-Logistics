package vn.edu.hcmute.uteexpress.service.admin.impl;

import jakarta.validation.*;
import java.time.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.data.domain.*;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.hcmute.uteexpress.dto.admin.AuditFilterRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.admin.StaffAccount;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.service.admin.StaffAccessPolicy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AuditServiceImplTest {
    private static ValidatorFactory factory;
    private AuditEventRepository events;
    private AuditServiceImpl service;
    @BeforeAll static void open() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void close() { factory.close(); }
    @BeforeEach void setUp() {
        events = mock(AuditEventRepository.class);
        var accounts = mock(StaffAccountRepository.class);
        for (var role : new AppUser.Role[]{AppUser.Role.ADMIN, AppUser.Role.MANAGER}) {
            var user = new AppUser(role == AppUser.Role.ADMIN ? "admin01" : "manager01", "not-exposed-hash", "staff@example.com");
            user.setEnabled(true); user.setRole(role);
            var staff = new StaffAccount(); staff.setUser(user); staff.setPasswordChangeRequired(false);
            when(accounts.findByUserUsername(user.getUsername())).thenReturn(java.util.Optional.of(staff));
        }
        var access = new StaffAccessPolicy(accounts, factory.getValidator());
        service = new AuditServiceImpl(events, access, Clock.fixed(Instant.parse("2026-10-18T03:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh")));
        when(events.findFiltered(anyString(), anyString(), any(), any(), any())).thenReturn(Page.empty());
    }
    @Test void exactActorCategoryAndDatesArePassedAsBoundParameters() {
        var filter = new AuditFilterRequest(); filter.setOperator("  manager01  "); filter.setCategory(AuditFilterRequest.Category.OPERATIONS);
        filter.setFrom(LocalDate.of(2026,10,1)); filter.setTo(LocalDate.of(2026,10,18));
        assertTrue(service.findEvents("admin01",filter,0).isEmpty());
        verify(events).findFiltered(eq("manager01"), eq("OPERATIONS"), eq(LocalDateTime.of(2026,10,1,0,0)),
            eq(LocalDateTime.of(2026,10,19,0,0)), argThat(page -> page.getPageSize() == 20 && page.getPageNumber() == 0));
    }
    @Test void noDatesDoesNotSilentlyHideOlderHistory() {
        service.findEvents("admin01",new AuditFilterRequest(),0);
        verify(events).findFiltered(eq(""),eq("ALL"),isNull(),isNull(),any());
    }
    @Test void oneDateGetsPredictableRangeAndInclusiveEndDate() {
        var filter = new AuditFilterRequest(); filter.setTo(LocalDate.of(2026,10,18));
        service.findEvents("admin01",filter,0);
        verify(events).findFiltered(eq(""),eq("ALL"),eq(LocalDateTime.of(2026,9,19,0,0)),eq(LocalDateTime.of(2026,10,19,0,0)),any());
        filter.setTo(null); filter.setFrom(LocalDate.of(2026,10,1)); service.findEvents("admin01",filter,0);
        verify(events).findFiltered(eq(""),eq("ALL"),eq(LocalDateTime.of(2026,10,1,0,0)),eq(LocalDateTime.of(2026,10,19,0,0)),any());
    }
    @ParameterizedTest @CsvSource({"1999-12-31,2000-01-01", "2026-10-18,2026-10-17", "2026-10-01,2026-10-19", "2025-10-17,2026-10-18"})
    void invalidDateRangesAreRejectedBeforeQuery(String from,String to) {
        var filter = new AuditFilterRequest(); filter.setFrom(LocalDate.parse(from)); filter.setTo(LocalDate.parse(to));
        assertThrows(IllegalArgumentException.class, () -> service.findEvents("admin01",filter,0)); verifyNoInteractions(events);
    }
    @Test void inclusive366DayBoundaryIsValid() {
        var filter = new AuditFilterRequest(); filter.setFrom(LocalDate.of(2025,10,18)); filter.setTo(LocalDate.of(2026,10,18));
        assertDoesNotThrow(() -> service.findEvents("admin01",filter,0));
    }
    @Test void extremeDatesDoNotOverflowAndEarliestEndDateIsClampedSafely() {
        var filter = new AuditFilterRequest(); filter.setTo(LocalDate.MIN);
        assertThrows(IllegalArgumentException.class,() -> service.findEvents("admin01",filter,0));
        filter.setTo(LocalDate.MAX); assertThrows(IllegalArgumentException.class,() -> service.findEvents("admin01",filter,0));
        filter.setTo(LocalDate.of(2000,1,1)); assertDoesNotThrow(() -> service.findEvents("admin01",filter,0));
        verify(events).findFiltered(eq(""),eq("ALL"),eq(LocalDateTime.of(2000,1,1,0,0)),eq(LocalDateTime.of(2000,1,2,0,0)),any());
    }
    @ParameterizedTest @ValueSource(strings = {"admin name", "admin<script>", "' or 1=1", "admin\u0000", "Thắng"})
    void invalidActorFilterCannotReachQuery(String actor) {
        var filter = new AuditFilterRequest(); filter.setOperator(actor);
        assertThrows(IllegalArgumentException.class, () -> service.findEvents("admin01",filter,0)); verifyNoInteractions(events);
    }
    @Test void oversizedFilterMissingCategoryAndInvalidPagesReject() {
        var filter = new AuditFilterRequest(); filter.setOperator("a".repeat(51));
        assertThrows(IllegalArgumentException.class, () -> service.findEvents("admin01",filter,0));
        filter.setOperator(""); filter.setCategory(null);
        assertThrows(IllegalArgumentException.class, () -> service.findEvents("admin01",filter,0));
        assertThrows(IllegalArgumentException.class, () -> service.findEvents("admin01",null,0));
        assertThrows(IllegalArgumentException.class, () -> service.findEvents("admin01",new AuditFilterRequest(),-1));
        assertThrows(IllegalArgumentException.class, () -> service.findEvents("admin01",new AuditFilterRequest(),10001));
        verifyNoInteractions(events);
    }
    @Test void managerCannotReadEvenWhenServiceIsCalledDirectly() {
        assertThrows(AccessDeniedException.class, () -> service.findEvents("manager01",new AuditFilterRequest(),0));
        assertThrows(AccessDeniedException.class, () -> service.findEvents("manager01",0)); verifyNoInteractions(events);
    }
}
