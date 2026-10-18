package vn.edu.hcmute.uteexpress.dto.manager;
public record ShipperPerformanceResponse(Long id, String username, String fullName,
        long pending, long inTransit, long delivered, long failed, long returned) {
    public long getActive() { return pending + inTransit; }
    public java.math.BigDecimal getSuccessRate() {
        return vn.edu.hcmute.uteexpress.util.DeliverySuccessRate.calculate(delivered, returned);
    }
}
