package vn.edu.hcmute.uteexpress.dto.manager;
import vn.edu.hcmute.uteexpress.entity.Order;
public record DispatchOrderResponse(Long id, String trackingCode, String receiverName, String shipperName, long revision) {
    public static DispatchOrderResponse from(Order order) {
        return new DispatchOrderResponse(order.getId(), order.getTrackingCode(), order.getReceiverName(),
            order.getShipper() == null ? "Chưa phân công" : order.getShipper().getUsername(), order.getRevision());
    }
}

