package vn.edu.hcmute.uteexpress.service;

import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.DraftOrder;
import vn.edu.hcmute.uteexpress.entity.Order;

import java.math.BigDecimal;
import java.util.List;

/**
 * Nghiệp vụ "giỏ đơn chờ xác nhận" của người gửi hàng - TV1 phụ trách (việc 2).
 *
 * Luồng: gom nhiều đơn nháp vào giỏ → xem lại toàn bộ và tổng cước → xác nhận
 * (từng đơn hoặc cả giỏ một lần). Đơn nháp chỉ trở thành vận đơn thật khi được xác nhận,
 * lúc đó mới sinh mã tracking và mới hiện ra cho Shipper / Manager.
 */
public interface OrderDraftService {

    List<DraftOrder> findDrafts(String username);

    long countDrafts(String username);

    /** Tổng cước tạm tính của cả giỏ, để hiển thị trước khi người dùng bấm xác nhận. */
    BigDecimal totalEstimatedFee(String username);

    DraftOrder addDraft(OrderCreateRequest request, String username);

    void removeDraft(Long draftId, String username);

    /** Xoá sạch giỏ mà không tạo đơn nào. */
    void clearDrafts(String username);

    /**
     * Xác nhận một đơn nháp: tạo vận đơn thật rồi xoá đơn nháp khỏi giỏ.
     * Ném IllegalStateException nếu đơn nháp không thuộc về người dùng này.
     */
    Order confirmDraft(Long draftId, String username);

    /**
     * Xác nhận toàn bộ giỏ trong một giao dịch: hoặc tạo được hết, hoặc không đơn nào
     * được tạo (nếu giữa chừng lỗi thì rollback sạch, giỏ giữ nguyên để người dùng thử lại).
     * Ném IllegalStateException nếu giỏ đang trống.
     */
    List<Order> confirmAllDrafts(String username);
}
