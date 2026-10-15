package vn.edu.hcmute.uteexpress.dto.tracking;

import java.util.List;

/**
 * Lộ trình hôm nay: các điểm dừng (gộp đơn cùng địa chỉ) và các chặng mở bằng Google Maps.
 * {@code legs}: chặng cho máy tính (tối đa 10 điểm mỗi chặng).
 * {@code mobileLegs}: chặng cho điện thoại; Google Maps trên trình duyệt điện thoại chỉ nhận 3 điểm dừng
 * giữa đường nên mỗi chặng tối đa 4 điểm.
 */
public record ShipperRoute(List<Stop> stops, List<Leg> legs, List<Leg> mobileLegs, int orderCount, int missingAddress, boolean truncated) {
    public record Task(Long orderId, String trackingCode, boolean pickup, String contactName, String phone, boolean due) { }
    public record Stop(int number, String address, List<Task> tasks) {
        public boolean hasPickup() { return tasks.stream().anyMatch(Task::pickup); }
        public boolean hasDelivery() { return tasks.stream().anyMatch(task -> !task.pickup()); }
        public boolean isDue() { return tasks.stream().anyMatch(Task::due); }
    }
    public record Leg(int index, int fromStop, int toStop, String url) { }
    public boolean isEmpty() { return stops.isEmpty(); }
}
