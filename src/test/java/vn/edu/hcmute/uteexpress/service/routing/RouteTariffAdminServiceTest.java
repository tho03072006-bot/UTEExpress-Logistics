package vn.edu.hcmute.uteexpress.service.routing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.hcmute.uteexpress.dto.admin.RouteTariffUpdateRequest;
import vn.edu.hcmute.uteexpress.entity.Order.ServiceType;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.routing.RouteTariffRepository;
import vn.edu.hcmute.uteexpress.service.admin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteTariffAdminServiceTest {
    @Mock RouteTariffRepository tariffs;
    @Mock StaffAccessPolicy access;
    @Mock AuditService audit;
    RouteTariffAdminService service;
    RouteTariff tariff;
    @BeforeEach void setup() {
        service = new RouteTariffAdminService(tariffs, access, audit);
        var from = new Branch("SGN", "SGN", "SGN", "Địa chỉ", "SGN-A", LocalDateTime.now());
        var to = new Branch("HAN", "HAN", "HAN", "Địa chỉ", "HAN-A", LocalDateTime.now());
        ReflectionTestUtils.setField(from, "id", 1L); ReflectionTestUtils.setField(to, "id", 2L);
        tariff = new RouteTariff(from, to, ServiceType.STANDARD, new BigDecimal("25000"), "4–5 ngày");
    }
    @Test void managerCannotReadOrUpdateAdminTariffs() {
        doThrow(new AccessDeniedException("Chỉ Admin.")).when(access).requireAdmin("manager");
        assertThrows(AccessDeniedException.class, () -> service.list("manager"));
        assertThrows(AccessDeniedException.class, () -> service.get("manager", 3L));
        assertThrows(AccessDeniedException.class, () -> service.update("manager", 3L, form())); verifyNoInteractions(tariffs, audit);
    }
    @Test void staleRevisionDoesNotChangeTariff() {
        lock(); var form = form(); form.setRevision(8L);
        assertThrows(StaffConflictException.class, () -> service.update("admin", 3L, form));
        assertEquals(new BigDecimal("25000"), tariff.getSurcharge()); verifyNoInteractions(audit);
    }
    @Test void unchangedFormIsRejected() {
        lock(); var form = RouteTariffUpdateRequest.from(tariff); form.setReason("Kiểm thử giữ giá");
        assertThrows(IllegalArgumentException.class, () -> service.update("admin", 3L, form)); verifyNoInteractions(audit);
    }
    @Test void localSurchargeMustStayZero() {
        tariff = new RouteTariff(tariff.getOriginBranch(), tariff.getOriginBranch(), ServiceType.STANDARD, BigDecimal.ZERO, "1–2 ngày"); lock();
        assertThrows(IllegalArgumentException.class, () -> service.update("admin", 3L, form())); verifyNoInteractions(audit);
    }
    @Test void leadTimeMustStayReadableBecauseDeadlinesAreComputedFromIt() {
        lock(); var form = form(); form.setLeadTimeText("Sớm nhất có thể");
        var error = assertThrows(IllegalArgumentException.class, () -> service.update("admin", 3L, form));
        assertTrue(error.getMessage().contains("Trong 4 giờ")); assertEquals("4–5 ngày", tariff.getLeadTimeText()); verifyNoInteractions(audit);
        var readable = form(); readable.setLeadTimeText("3–4 ngày"); service.update("admin", 3L, readable);
        assertEquals("3–4 ngày", tariff.getLeadTimeText());
    }
    @Test void priceChangeIsLockedAndAudited() {
        lock(); service.update("admin", 3L, form()); assertEquals(new BigDecimal("30000"), tariff.getSurcharge());
        verify(tariffs).flush(); verify(audit).record(eq("admin"), eq("ROUTE_TARIFF_UPDATE"), eq("route-tariff:3"), contains("25000 -> 30000"));
    }
    @Test void validatesMoneyAndTextBeforeWriting() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var form = form(); assertTrue(factory.getValidator().validate(form).isEmpty());
            for (String value : new String[]{"-1", "1.5", "100000001"}) {
                form.setSurcharge(new BigDecimal(value)); assertFalse(factory.getValidator().validate(form).isEmpty());
            }
            form = form(); form.setLeadTimeText("x\nx"); assertFalse(factory.getValidator().validate(form).isEmpty());
        }
    }
    private void lock() { when(tariffs.lockById(3L)).thenReturn(Optional.of(tariff)); }
    private RouteTariffUpdateRequest form() {
        var form = RouteTariffUpdateRequest.from(tariff); form.setSurcharge(new BigDecimal("30000")); form.setReason("Kiểm thử giá mới"); return form;
    }
}
