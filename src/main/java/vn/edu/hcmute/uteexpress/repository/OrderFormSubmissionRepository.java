package vn.edu.hcmute.uteexpress.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.OrderFormSubmission;

public interface OrderFormSubmissionRepository extends JpaRepository<OrderFormSubmission, Long> {
    Optional<OrderFormSubmission> findByToken(String token);
}
