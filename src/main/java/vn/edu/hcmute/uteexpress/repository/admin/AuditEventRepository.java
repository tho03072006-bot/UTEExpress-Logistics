package vn.edu.hcmute.uteexpress.repository.admin;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.admin.AuditEvent;
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    Page<AuditEvent> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
