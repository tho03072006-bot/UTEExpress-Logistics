package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.LinehaulItem;
import vn.edu.hcmute.uteexpress.entity.routing.LinehaulTrip;

/** Truy vấn dữ liệu LinehaulItem; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface LinehaulItemRepository extends JpaRepository<LinehaulItem, Long> {
    List<LinehaulItem> findAllByTrip_Id(Long tripId);
    List<LinehaulItem> findAllByTrip_IdIn(List<Long> tripIds);
    boolean existsByOrder_IdAndTrip_Status(Long orderId, LinehaulTrip.Status status);
    /** Kiện đã ghi thiếu khi nhận chuyến, còn chờ nhận bổ sung tại kho này. */
    List<LinehaulItem> findAllByTrip_ToBranch_IdAndTrip_StatusAndReceivedFalseOrderByIdDesc(Long branchId, LinehaulTrip.Status status);
}
