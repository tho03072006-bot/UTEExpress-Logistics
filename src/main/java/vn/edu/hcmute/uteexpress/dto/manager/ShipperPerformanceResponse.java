package vn.edu.hcmute.uteexpress.dto.manager;
public record ShipperPerformanceResponse(Long id, String username, String fullName,
        long pending, long inTransit, long delivered, long failed, long returned) {
    public long getActive() { return pending + inTransit; }
    public int getSuccessRate() { long ended = delivered + returned; return ended == 0 ? 0 : (int) Math.round(delivered * 100.0 / ended); }
}
