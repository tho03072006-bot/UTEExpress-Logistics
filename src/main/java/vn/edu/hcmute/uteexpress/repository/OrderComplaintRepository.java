package vn.edu.hcmute.uteexpress.repository;

import java.util.Optional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.dto.tracking.ComplaintSummary;

public interface OrderComplaintRepository extends JpaRepository<OrderComplaint, Long> {
    Optional<OrderComplaint> findByOrder(Order order);
    @Query("""
            select new vn.edu.hcmute.uteexpress.dto.tracking.ComplaintSummary(
                c.id, c.order.trackingCode, c.subject, c.status, c.createdAt)
            from OrderComplaint c where (:status is null or c.status = :status)
            """)
    Page<ComplaintSummary> search(@Param("status") OrderComplaint.Status status, Pageable pageable);
}
