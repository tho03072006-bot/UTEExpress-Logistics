package vn.edu.hcmute.uteexpress.repository.admin;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.admin.ComplaintActivity;
public interface ComplaintActivityRepository extends JpaRepository<ComplaintActivity, Long> {
    Page<ComplaintActivity> findByComplaintIdOrderByCreatedAtAscIdAsc(Long complaintId, Pageable pageable);
}
