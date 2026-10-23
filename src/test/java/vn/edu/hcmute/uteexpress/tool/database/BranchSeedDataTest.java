package vn.edu.hcmute.uteexpress.tool.database;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vn.edu.hcmute.uteexpress.entity.Order.ServiceType;
import static org.junit.jupiter.api.Assertions.*;

class BranchSeedDataTest {
    private final BranchSeedData.Catalog catalog = BranchSeedData.load();

    @Test void eachAreaHasOneBranchAndOneUniqueInternalCode() {
        assertEquals(59, catalog.areas().size());
        assertEquals(59, catalog.areas().stream().map(BranchSeedData.AreaSeed::code).distinct().count());
        assertEquals(Map.of("SGN", 22L, "HAN", 30L, "DAD", 7L), catalog.areas().stream()
                .collect(Collectors.groupingBy(BranchSeedData.AreaSeed::branchCode, Collectors.counting())));
        var provinces = catalog.branches().stream().collect(Collectors.toMap(BranchSeedData.BranchSeed::code, BranchSeedData.BranchSeed::provinceCode));
        for (var area : catalog.areas()) {
            assertTrue(area.code().startsWith(area.branchCode() + "-"));
            assertEquals(provinces.get(area.branchCode()), area.provinceCode());
        }
        assertTrue(catalog.areas().stream().noneMatch(area -> area.code().contains("HOANGSA")));
    }

    @Test void allNeighborsAreSymmetricDistinctAndInsideTheSameBranch() {
        var neighbors = new HashSet<>(catalog.neighbors());
        var areas = catalog.areas().stream().collect(Collectors.toMap(BranchSeedData.AreaSeed::code, area -> area));
        assertEquals(270, neighbors.size());
        assertEquals(catalog.neighbors().size(), neighbors.size());
        for (var neighbor : neighbors) {
            assertNotEquals(neighbor.areaCode(), neighbor.neighborCode());
            assertTrue(neighbors.contains(new BranchSeedData.NeighborSeed(neighbor.neighborCode(), neighbor.areaCode())));
            assertEquals(areas.get(neighbor.areaCode()).branchCode(), areas.get(neighbor.neighborCode()).branchCode());
        }
        assertEquals(areas.keySet(), neighbors.stream().map(BranchSeedData.NeighborSeed::areaCode).collect(Collectors.toSet()));
    }

    @Test void hubsBelongToTheirOwnBranchAndHaveApproximateCoordinates() {
        assertEquals(Map.of("SGN", "SGN-THUDUC", "HAN", "HAN-CAUGIAY", "DAD", "DAD-HAICHAU"), catalog.branches().stream()
                .collect(Collectors.toMap(BranchSeedData.BranchSeed::code, BranchSeedData.BranchSeed::hubAreaCode)));
        for (var branch : catalog.branches()) {
            var hub = catalog.areas().stream().filter(area -> area.code().equals(branch.hubAreaCode())).findFirst().orElseThrow();
            assertEquals(branch.code(), hub.branchCode());
        }
        for (var area : catalog.areas()) {
            assertTrue(area.lat().compareTo(BigDecimal.ZERO) > 0 && area.lat().compareTo(new BigDecimal("90")) < 0);
            assertTrue(area.lng().compareTo(BigDecimal.ZERO) > 0 && area.lng().compareTo(new BigDecimal("180")) < 0);
        }
    }

    @Test void tariffsAreUniqueSymmetricAndNeverOfferInterprovincialSuperExpress() {
        assertEquals(21, catalog.tariffs().size());
        assertEquals(21, catalog.tariffs().stream().map(t -> t.origin() + ":" + t.destination() + ":" + t.service()).distinct().count());
        for (var tariff : catalog.tariffs()) {
            var reverse = tariff(tariff.destination(), tariff.origin(), tariff.service());
            assertEquals(tariff.surcharge(), reverse.surcharge());
            assertEquals(tariff.leadTime(), reverse.leadTime());
            assertTrue(tariff.surcharge().signum() >= 0);
            if (tariff.service() == ServiceType.SUPER_EXPRESS) assertEquals(tariff.origin(), tariff.destination());
            if (tariff.origin().equals(tariff.destination())) assertEquals(BigDecimal.ZERO, tariff.surcharge());
        }
    }

    @Test void defaultSurchargesAndLeadTimesMatchApprovedRoutes() {
        assertEquals(new BigDecimal("25000"), tariff("SGN", "HAN", ServiceType.STANDARD).surcharge());
        assertEquals(new BigDecimal("40000"), tariff("SGN", "HAN", ServiceType.EXPRESS).surcharge());
        assertEquals("4–5 ngày", tariff("SGN", "HAN", ServiceType.STANDARD).leadTime());
        assertEquals("2–3 ngày", tariff("SGN", "HAN", ServiceType.EXPRESS).leadTime());
        for (String origin : List.of("SGN", "HAN")) {
            assertEquals(new BigDecimal("15000"), tariff(origin, "DAD", ServiceType.STANDARD).surcharge());
            assertEquals(new BigDecimal("25000"), tariff(origin, "DAD", ServiceType.EXPRESS).surcharge());
            assertEquals("3–4 ngày", tariff(origin, "DAD", ServiceType.STANDARD).leadTime());
            assertEquals("2 ngày", tariff(origin, "DAD", ServiceType.EXPRESS).leadTime());
        }
    }
    private BranchSeedData.TariffSeed tariff(String from, String to, ServiceType service) {
        return catalog.tariffs().stream().filter(t -> t.origin().equals(from) && t.destination().equals(to) && t.service() == service).findFirst().orElseThrow();
    }
}
