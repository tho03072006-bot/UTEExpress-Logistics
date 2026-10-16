package vn.edu.hcmute.uteexpress.service.admin.impl;

import jakarta.validation.*;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.*;
import vn.edu.hcmute.uteexpress.dto.admin.ShippingTariffUpdateRequest;
import vn.edu.hcmute.uteexpress.exception.StaffConflictException;
import vn.edu.hcmute.uteexpress.repository.admin.*;
import vn.edu.hcmute.uteexpress.service.admin.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ShippingTariffServiceImplTest {
    private final ShippingTariffRepository tariffs = mock(ShippingTariffRepository.class);
    private final StaffAccountRepository accounts = mock(StaffAccountRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private ShippingTariffServiceImpl service;
    private ValidatorFactory factory;
    private ShippingTariff tariff;
    @BeforeEach void setup() {
        factory = Validation.buildDefaultValidatorFactory();
        var account = new StaffAccount(); var user = new AppUser("admin01", "hash", "admin@example.com");
        user.setEnabled(true); user.setRole(AppUser.Role.ADMIN); account.setUser(user); account.setPasswordChangeRequired(false);
        when(accounts.findByUserUsername("admin01")).thenReturn(Optional.of(account));
        var manager = new StaffAccount(); var managerUser = new AppUser("manager01", "hash", "manager@example.com");
        managerUser.setEnabled(true); managerUser.setRole(AppUser.Role.MANAGER); manager.setUser(managerUser); manager.setPasswordChangeRequired(false);
        when(accounts.findByUserUsername("manager01")).thenReturn(Optional.of(manager));
        service = new ShippingTariffServiceImpl(tariffs, new StaffAccessPolicy(accounts, factory.getValidator()), audit, Clock.systemUTC(), mock(ShippingTariffVersionRepository.class));
        tariff = new ShippingTariff(); tariff.setServiceType(Order.ServiceType.STANDARD);
        tariff.setBaseFee(BigDecimal.valueOf(15000)); tariff.setFeePerKg(BigDecimal.valueOf(3000));
        when(tariffs.findByServiceType(Order.ServiceType.STANDARD)).thenReturn(Optional.of(tariff));
        when(tariffs.lockByServiceType(Order.ServiceType.STANDARD)).thenReturn(Optional.of(tariff));
    }
    @AfterEach void close() { factory.close(); }
    private ShippingTariffUpdateRequest form() {
        var form = new ShippingTariffUpdateRequest(); form.setRevision(0L);
        form.setBaseFee(BigDecimal.valueOf(20000)); form.setFeePerKg(BigDecimal.valueOf(4000));
        form.setReason("Cập nhật cước demo"); return form;
    }
    @ParameterizedTest @ValueSource(doubles = {-1, 0, 100.01, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidWeightIsRejected(double weight) {
        assertThrows(IllegalArgumentException.class, () -> service.quote(Order.ServiceType.STANDARD, weight));
    }
    @Test void fractionalMoneyRoundsToWholeVndAndReadsCurrentTariff() {
        assertEquals(new BigDecimal("15004"), service.quote(Order.ServiceType.STANDARD, 0.0012));
        tariff.setBaseFee(BigDecimal.valueOf(25000));
        assertEquals(new BigDecimal("31000"), service.quote(Order.ServiceType.STANDARD, 2));
    }
    @Test void managerCannotChangeTariffEvenDirectlyCallingService() {
        assertThrows(AccessDeniedException.class, () -> service.updateTariff("manager01", Order.ServiceType.STANDARD, form()));
        verify(tariffs, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }
    @Test void adminUpdatesAndAudits() {
        service.updateTariff("admin01", Order.ServiceType.STANDARD, form());
        assertEquals(BigDecimal.valueOf(20000), tariff.getBaseFee());
        verify(audit).record(eq("admin01"), eq("TARIFF_UPDATE"), eq("STANDARD"), anyString());
    }
    @Test void staleRevisionAndFractionalNegativeZeroFeesReject() {
        var stale = form(); stale.setRevision(1L);
        assertThrows(StaffConflictException.class, () -> service.updateTariff("admin01", Order.ServiceType.STANDARD, stale));
        var decimal = form(); decimal.setBaseFee(new BigDecimal("10.1"));
        assertThrows(IllegalArgumentException.class, () -> service.updateTariff("admin01", Order.ServiceType.STANDARD, decimal));
        var zero = form(); zero.setBaseFee(BigDecimal.ZERO); zero.setFeePerKg(BigDecimal.ZERO);
        assertThrows(IllegalArgumentException.class, () -> service.updateTariff("admin01", Order.ServiceType.STANDARD, zero));
        verify(tariffs, never()).saveAndFlush(any()); verifyNoInteractions(audit);
    }
    @Test void missingTariffNeverFallsBackToHardcodedLocalPrice() {
        assertThrows(IllegalStateException.class, () -> service.quote(Order.ServiceType.EXPRESS, 1));
    }
}

