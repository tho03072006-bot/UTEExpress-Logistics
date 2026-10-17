package vn.edu.hcmute.uteexpress.util;

/**
 * Tỉ lệ giao thành công (phần trăm, làm tròn) trên các đơn đã kết thúc: đã giao + đã hoàn trả.
 * Đơn đang chạy hoặc vừa giao thất bại (còn được giao lại) chưa tính. Chưa có đơn kết thúc thì 0%.
 * Dùng cho khối số liệu ở trang chủ (OrderServiceImpl#getHomeStatistics).
 */
public final class DeliverySuccessRate {
    private DeliverySuccessRate() { }

    public static int calculate(long delivered, long returned) {
        if (delivered < 0 || returned < 0) throw new IllegalArgumentException("Số đơn không được âm.");
        long finished = delivered + returned;
        return finished == 0 ? 0 : (int) Math.round(delivered * 100.0 / finished);
    }
}
