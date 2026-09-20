package vn.edu.hcmute.uteexpress.dto;

/**
 * Số liệu hoạt động hiện trên trang chủ công khai - TV1 phụ trách.
 *
 * Toàn bộ là số đếm thật từ cơ sở dữ liệu, không phải con số tự đặt cho đẹp.
 * Trang chủ của các bên giao nhận lớn đều có khối này vì nó trả lời câu hỏi đầu
 * tiên của người mới ghé: chỗ này có ai dùng không, có chạy thật không.
 *
 * Cố ý KHÔNG hiện doanh thu hay tổng tiền: đó là số liệu nội bộ của Manager,
 * không phải thứ để công khai cho khách vãng lai.
 */
public class HomeStatistics {

    private final long deliveredOrders;
    private final long activeCustomers;
    private final long ordersInTransit;

    /** Tỉ lệ giao thành công, tính trên các đơn đã kết thúc. Đơn vị phần trăm. */
    private final int successRate;

    public HomeStatistics(long deliveredOrders, long activeCustomers,
                          long ordersInTransit, int successRate) {
        this.deliveredOrders = deliveredOrders;
        this.activeCustomers = activeCustomers;
        this.ordersInTransit = ordersInTransit;
        this.successRate = successRate;
    }

    // ----- Getter -----

    public long getDeliveredOrders() {
        return deliveredOrders;
    }

    public long getActiveCustomers() {
        return activeCustomers;
    }

    public long getOrdersInTransit() {
        return ordersInTransit;
    }

    public int getSuccessRate() {
        return successRate;
    }
}
