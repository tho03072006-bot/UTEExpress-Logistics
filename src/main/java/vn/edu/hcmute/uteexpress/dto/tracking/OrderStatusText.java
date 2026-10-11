package vn.edu.hcmute.uteexpress.dto.tracking;

import vn.edu.hcmute.uteexpress.entity.Order;

/** Nhãn tiếng Việt cho thông báo lỗi; Order.java là entity dùng chung nên không thêm nhãn vào enum. */
public final class OrderStatusText {
    private OrderStatusText() { }
    public static String of(Order.OrderStatus status) {
        if (status == null) return "không xác định";
        return switch (status) {
            case PENDING_PICKUP -> "Chờ lấy hàng";
            case PICKED_UP -> "Đã lấy hàng";
            case IN_TRANSIT -> "Đang giao";
            case DELIVERED -> "Giao thành công";
            case FAILED -> "Giao thất bại";
            case CANCELLED -> "Đã hủy";
            case RETURNED -> "Đã hoàn trả";
        };
    }
}
