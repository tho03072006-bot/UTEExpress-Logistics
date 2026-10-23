package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.*;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.entity.Order;

/**
 * Cập nhật nhiều đơn cùng lúc cho hai bước không cần bằng chứng: đã lấy hàng và bắt đầu giao.
 * Mỗi đơn chạy trong transaction riêng của TrackingService nên một đơn lỗi không chặn các đơn khác.
 */
@Service
public class ShipperBulkService {
    public static final int MAX_ORDERS = 50;
    private final TrackingService tracking;

    public ShipperBulkService(TrackingService tracking) { this.tracking = tracking; }

    public record Failure(String label, String reason) { }
    public record Result(List<String> updated, List<Failure> failed) {
        public String summary() {
            var text = new StringBuilder("Đã cập nhật ").append(updated.size()).append('/')
                    .append(updated.size() + failed.size()).append(" đơn.");
            if (!failed.isEmpty()) {
                text.append(" Chưa cập nhật: ");
                for (int i = 0; i < failed.size(); i++) {
                    if (i == 5) { text.append("; và ").append(failed.size() - 5).append(" đơn khác"); break; }
                    if (i > 0) text.append("; ");
                    text.append(failed.get(i).label()).append(" (").append(failed.get(i).reason()).append(')');
                }
                text.append('.');
            }
            return text.toString();
        }
    }

    public Result update(String username, List<Long> orderIds, Order.OrderStatus status) {
        if (status != Order.OrderStatus.PICKED_UP && status != Order.OrderStatus.IN_TRANSIT)
            throw new IllegalArgumentException("Chỉ cập nhật hàng loạt bước Đã lấy hàng hoặc Bắt đầu giao.");
        if (orderIds == null || orderIds.isEmpty())
            throw new IllegalArgumentException("Hãy chọn ít nhất một đơn.");
        if (orderIds.size() > MAX_ORDERS) throw new IllegalArgumentException("Mỗi lần cập nhật tối đa " + MAX_ORDERS + " đơn.");
        if (orderIds.stream().anyMatch(id -> id == null || id <= 0)) throw new IllegalArgumentException("Mã đơn không hợp lệ.");
        var ids = new LinkedHashSet<>(orderIds);
        List<String> updated = new ArrayList<>();
        List<Failure> failed = new ArrayList<>();
        for (Long id : ids) {
            try {
                updated.add(tracking.updateStatus(id, username, status).getTrackingCode());
            } catch (IllegalStateException | IllegalArgumentException ex) {
                failed.add(new Failure(label(id, username), ex.getMessage()));
            } catch (ConcurrencyFailureException ex) {
                failed.add(new Failure(label(id, username), "đơn đang được cập nhật ở nơi khác"));
            }
        }
        return new Result(List.copyOf(updated), List.copyOf(failed));
    }

    /** Chỉ hiện mã vận đơn khi đơn thuộc Shipper; đơn khác chỉ hiện số thứ tự để không lộ dữ liệu. */
    private String label(Long id, String username) {
        return tracking.findAssignedOrder(id, username).map(Order::getTrackingCode).orElse("Đơn #" + id);
    }
}
