package vn.edu.hcmute.uteexpress.dto.tracking;
import java.time.LocalDateTime;
import vn.edu.hcmute.uteexpress.entity.Order;
public record ShipperActivity(Long orderId, Order.OrderStatus status, LocalDateTime at) { }
