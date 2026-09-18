package vn.edu.hcmute.uteexpress.dto;

import java.time.LocalDateTime;

/**
 * Một mốc trên hành trình đơn hàng, dùng cho timeline ở trang chi tiết đơn - TV1 phụ trách.
 *
 * LƯU Ý VỀ MỐC THỜI GIAN: hệ thống hiện chỉ lưu trạng thái MỚI NHẤT của đơn (cột status),
 * không có bảng lịch sử ghi lại từng lần đổi trạng thái. Vì vậy chỉ hai mốc có thời gian
 * thật: mốc tạo đơn (lấy từ created_at) và mốc đang đứng (lấy từ updated_at). Các mốc ở
 * giữa để trống thời gian thay vì bịa ra một con số - hiển thị sai còn tệ hơn bỏ trống.
 *
 * Muốn mọi mốc đều có giờ chính xác thì phải thêm bảng order_status_history ghi lại mỗi
 * lần Shipper đổi trạng thái; đó là việc chung với TV2, chưa làm trong phạm vi này.
 */
public class OrderTimelineStep {

    private final String label;
    private final String description;

    /** Mốc này đã đi qua chưa (dùng để tô đậm phần đã hoàn thành). */
    private final boolean done;

    /** Có phải mốc đơn đang đứng hay không (dùng để làm nổi bật). */
    private final boolean current;

    /** Thời điểm xảy ra, null nghĩa là không có dữ liệu chứ không phải chưa xảy ra. */
    private final LocalDateTime at;

    public OrderTimelineStep(String label, String description, boolean done, boolean current,
                             LocalDateTime at) {
        this.label = label;
        this.description = description;
        this.done = done;
        this.current = current;
        this.at = at;
    }

    // ----- Getter -----

    public String getLabel() {
        return label;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDone() {
        return done;
    }

    public boolean isCurrent() {
        return current;
    }

    public LocalDateTime getAt() {
        return at;
    }
}
