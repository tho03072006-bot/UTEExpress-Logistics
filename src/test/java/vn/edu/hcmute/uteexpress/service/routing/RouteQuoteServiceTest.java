package vn.edu.hcmute.uteexpress.service.routing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import vn.edu.hcmute.uteexpress.entity.Order.ServiceType;
import vn.edu.hcmute.uteexpress.entity.routing.*;
import vn.edu.hcmute.uteexpress.repository.routing.RouteTariffRepository;
import vn.edu.hcmute.uteexpress.service.admin.ShippingTariffService;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteQuoteServiceTest {
    @Mock RoutingCatalogService catalog;
    @Mock ShippingTariffService base;
    @Mock RouteTariffRepository tariffs;
    @Mock vn.edu.hcmute.uteexpress.service.admin.OperationsPolicyService policies;
    RouteQuoteService service;
    Branch sgn, han;
    @BeforeEach void setup() {
        service = new RouteQuoteService(catalog, base, tariffs, policies);
        sgn = branch(1L, "SGN"); han = branch(2L, "HAN");
    }
    @Test void interprovincialUsesRouteSurchargeWithoutDistanceSurcharge() {
        areas(sgn, han, 10.85, 21.03);
        price(ServiceType.STANDARD, han, 25000);
        var quote = service.quote(ServiceType.STANDARD, 1, "P", "D");
        assertTrue(quote.interprovincial()); assertTrue(quote.distanceKm() > 1000);
        assertEquals(new BigDecimal("43000"), quote.total()); assertEquals(BigDecimal.ZERO, quote.distanceFee());
        assertEquals(new BigDecimal("25000"), quote.routeFee()); assertEquals("Kiểm thử thời gian", quote.leadTimeText());
    }
    @Test void localUsesDistanceAndIgnoresRouteSurcharge() {
        areas(sgn, sgn, 10, 10.1); price(ServiceType.STANDARD, sgn, 0);
        var quote = service.quote(ServiceType.STANDARD, 1, "P", "D");
        assertFalse(quote.interprovincial()); assertEquals(11.1195, quote.distanceKm(), 0.001);
        assertEquals(new BigDecimal("6100"), quote.distanceFee()); assertEquals(new BigDecimal("24100"), quote.total());
        assertEquals(BigDecimal.ZERO, quote.routeFee());
    }
    @Test void dropOffSubtractsPolicyDiscountAfterAllSurcharges() {
        areas(sgn, han, 10.85, 21.03); price(ServiceType.STANDARD, han, 25000); discount(3000);
        var quote = service.quote(ServiceType.STANDARD, 1, "P", "D", OrderRoute.PickupMode.DROP_OFF);
        assertEquals(new BigDecimal("3000"), quote.dropOffDiscount()); assertEquals(new BigDecimal("40000"), quote.total());
        assertEquals(OrderRoute.PickupMode.DROP_OFF, quote.pickupMode());
    }
    @Test void dropOffDiscountNeverMakesTheFeeNegative() {
        areas(sgn, sgn, 10, 10); price(ServiceType.STANDARD, sgn, 0); discount(20000);
        var quote = service.quote(ServiceType.STANDARD, 1, "P", "D", OrderRoute.PickupMode.DROP_OFF);
        assertEquals(new BigDecimal("18000"), quote.dropOffDiscount()); assertEquals(0, quote.total().signum());
    }
    @Test void courierQuoteNeverReadsTheDropOffPolicy() {
        areas(sgn, sgn, 10, 10); price(ServiceType.STANDARD, sgn, 0);
        var quote = service.quote(ServiceType.STANDARD, 1, "P", "D");
        assertEquals(BigDecimal.ZERO, quote.dropOffDiscount()); assertEquals(OrderRoute.PickupMode.COURIER, quote.pickupMode());
        verifyNoInteractions(policies);
    }
    @Test void sameAreaHasZeroDistanceAndNoExtraFee() {
        areas(sgn, sgn, 10, 10); price(ServiceType.SUPER_EXPRESS, sgn, 0);
        var quote = service.quote(ServiceType.SUPER_EXPRESS, 1, "P", "D");
        assertEquals(0, quote.distanceKm()); assertEquals(new BigDecimal("18000"), quote.total());
    }
    @Test void expressAcrossBranchesIsRejectedEvenIfCentersAreNearby() {
        areas(sgn, han, 10, 10.01);
        assertThrows(IllegalArgumentException.class, () -> service.quote(ServiceType.SUPER_EXPRESS, 1, "P", "D"));
        verifyNoInteractions(tariffs, base);
    }
    @Test void expressBeyondThirtyKilometersInSameBranchIsRejected() {
        areas(sgn, sgn, 10, 10.3);
        assertThrows(IllegalArgumentException.class, () -> service.quote(ServiceType.SUPER_EXPRESS, 1, "P", "D"));
        verifyNoInteractions(tariffs, base);
    }
    @Test void missingRouteFailsWithoutFallingBackToAnUnrelatedPrice() {
        areas(sgn, han, 10, 20);
        assertThrows(IllegalStateException.class, () -> service.quote(ServiceType.STANDARD, 1, "P", "D"));
        verifyNoInteractions(base);
    }
    @ParameterizedTest @ValueSource(doubles = {0, -1, 0.001, 101, Double.NaN, Double.POSITIVE_INFINITY})
    void invalidWeightNeverQueriesCatalog(double kg) {
        assertThrows(IllegalArgumentException.class, () -> service.quote(ServiceType.STANDARD, kg, "P", "D"));
        verifyNoInteractions(catalog, tariffs, base);
    }
    @ParameterizedTest @CsvSource({"0,STANDARD,0", "5,STANDARD,0", "12,STANDARD,7000", "30,STANDARD,25000",
            "50,STANDARD,31000", "300,STANDARD,106000", "1700,STANDARD,246000", "12,EXPRESS,9100", "12,SUPER_EXPRESS,11200"})
    void distanceTiersAndRoundingStayConsistent(double km, ServiceType type, String expected) {
        assertEquals(new BigDecimal(expected), RouteDistance.surcharge(km, type));
    }
    @Test void haversineIsSymmetricAndClampedAtAntipodes() {
        var a = new ProvinceArea("SGN", "A", "A", sgn, BigDecimal.ZERO, BigDecimal.ZERO);
        var b = new ProvinceArea("SGN", "B", "B", sgn, BigDecimal.ZERO, new BigDecimal("180"));
        assertEquals(RouteDistance.kilometers(a, b), RouteDistance.kilometers(b, a), 0.00001);
        assertTrue(Double.isFinite(RouteDistance.kilometers(a, b)));
    }
    private void areas(Branch from, Branch to, double lat1, double lat2) {
        when(catalog.requireArea("P")).thenReturn(area("P", from, lat1));
        when(catalog.requireArea("D")).thenReturn(area("D", to, lat2));
    }
    private void price(ServiceType type, Branch to, long surcharge) {
        when(tariffs.findByOriginBranch_IdAndDestBranch_IdAndServiceType(1L, to.getId(), type))
                .thenReturn(Optional.of(new RouteTariff(sgn, to, type, BigDecimal.valueOf(surcharge), "Kiểm thử thời gian")));
        when(base.quote(type, 1)).thenReturn(new BigDecimal("18000"));
    }
    private void discount(long amount) {
        var policy = new vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy(); policy.setDropOffDiscount(BigDecimal.valueOf(amount));
        when(policies.current()).thenReturn(policy);
    }
    private static Branch branch(Long id, String code) {
        var b = new Branch(code, code, code, "Địa chỉ kho", code + "-A", LocalDateTime.now()); ReflectionTestUtils.setField(b, "id", id); return b;
    }
    private static ProvinceArea area(String code, Branch branch, double lat) {
        return new ProvinceArea(branch.getProvinceCode(), code, code, branch, BigDecimal.valueOf(lat), new BigDecimal("106"));
    }
}
