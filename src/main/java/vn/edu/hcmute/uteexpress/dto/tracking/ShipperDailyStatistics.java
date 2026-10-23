package vn.edu.hcmute.uteexpress.dto.tracking;
import java.math.BigDecimal;
import java.time.LocalDate;
/** pickedUp: số đơn shipper lấy hàng thành công trong ngày (kể cả đơn liên tỉnh đã bàn giao vào kho). */
public record ShipperDailyStatistics(LocalDate date, long delivered, long failed, long returned, BigDecimal collectedCod, long pickedUp) {
    public long getUnsuccessful() { return failed + returned; }
}
