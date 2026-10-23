package vn.edu.hcmute.uteexpress.util;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import vn.edu.hcmute.uteexpress.dto.manager.ShipperPerformanceResponse;
import static org.junit.jupiter.api.Assertions.*;

class DeliverySuccessRateTest {
    @ParameterizedTest @CsvSource({"0,0,0.0", "0,1,0.0", "1,0,100.0", "2,1,66.7", "1,2,33.3", "8,2,80.0"})
    void allRolesShareTheSameFormulaAndRounding(long delivered, long returned, String expected) {
        var percentage = new BigDecimal(expected);
        assertEquals(percentage, DeliverySuccessRate.calculate(delivered, returned));
        assertEquals(percentage, new ShipperPerformanceResponse(1L, "test", "Kiểm thử", 20, 20, delivered, 20, returned, 0).getSuccessRate());
    }

    @Test void countsCannotBeNegativeAndLargeCountsDoNotOverflow() {
        assertThrows(IllegalArgumentException.class, () -> DeliverySuccessRate.calculate(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> DeliverySuccessRate.calculate(0, -1));
        assertEquals(new BigDecimal("50.0"), DeliverySuccessRate.calculate(Long.MAX_VALUE, Long.MAX_VALUE));
    }
}
