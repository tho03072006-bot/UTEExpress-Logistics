package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.DraftOrderArea;

/** Truy vấn dữ liệu DraftOrderArea; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface DraftOrderAreaRepository extends JpaRepository<DraftOrderArea, Long> {
    Optional<DraftOrderArea> findByDraftOrder_Id(Long draftOrderId);
}
