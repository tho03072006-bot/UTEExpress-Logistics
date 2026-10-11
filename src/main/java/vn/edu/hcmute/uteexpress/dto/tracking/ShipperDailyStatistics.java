package vn.edu.hcmute.uteexpress.dto.tracking;
import java.math.BigDecimal;
import java.time.LocalDate;
public record ShipperDailyStatistics(LocalDate date, long delivered, long failed, long returned, BigDecimal collectedCod) {
    public long getUnsuccessful() { return failed + returned; }
}
