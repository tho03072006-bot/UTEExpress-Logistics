package vn.edu.hcmute.uteexpress.dto.tracking;

import java.util.List;

/** Lộ trình hôm nay: các điểm dừng (gộp đơn cùng địa chỉ) và các chặng mở bằng Google Maps. */
public record ShipperRoute(List<Stop> stops, List<Leg> legs, int orderCount, int missingAddress, boolean truncated) {
    public record Task(Long orderId, String trackingCode, boolean pickup, String contactName, String phone, boolean due) { }
    public record Stop(int number, String address, List<Task> tasks) {
        public boolean hasPickup() { return tasks.stream().anyMatch(Task::pickup); }
        public boolean hasDelivery() { return tasks.stream().anyMatch(task -> !task.pickup()); }
        public boolean isDue() { return tasks.stream().anyMatch(Task::due); }
    }
    public record Leg(int index, int fromStop, int toStop, String url) { }
    public boolean isEmpty() { return stops.isEmpty(); }
}
