package vn.edu.hcmute.uteexpress.dto.manager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.Order;

public record ManagerOrderResponse(String trackingCode, String senderName, String receiverName,
        String shipperName, Order.OrderStatus status, BigDecimal shippingFee, LocalDateTime createdAt) {
    public String getStatusLabel() {
        return status == null ? "Chưa xác định" : getStatusLabels().getOrDefault(status, "Chưa xác định");
    }

    public static java.util.Map<Order.OrderStatus, String> getStatusLabels() {
        return java.util.Map.of(
                Order.OrderStatus.PENDING_PICKUP, "Chờ lấy hàng",
                Order.OrderStatus.PICKED_UP, "Đã lấy hàng",
                Order.OrderStatus.IN_TRANSIT, "Đang giao",
                Order.OrderStatus.DELIVERED, "Giao thành công",
                Order.OrderStatus.FAILED, "Giao thất bại",
                Order.OrderStatus.CANCELLED, "Đã hủy",
                Order.OrderStatus.RETURNED, "Đã hoàn trả");
    }

    public static ManagerOrderResponse from(Order order) {
        return new ManagerOrderResponse(order.getTrackingCode(), order.getSenderName(), order.getReceiverName(),
                order.getShipper() == null ? "Chưa phân công" : order.getShipper().getFullName(),
                order.getStatus(), order.getShippingFee(), order.getCreatedAt());
    }
}
