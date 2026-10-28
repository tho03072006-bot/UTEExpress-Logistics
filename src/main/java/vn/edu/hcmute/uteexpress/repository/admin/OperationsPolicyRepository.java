package vn.edu.hcmute.uteexpress.repository.admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import jakarta.persistence.LockModeType;
import vn.edu.hcmute.uteexpress.entity.admin.OperationsPolicy;
public interface OperationsPolicyRepository extends JpaRepository<OperationsPolicy, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from OperationsPolicy p where p.id = 1")
    Optional<OperationsPolicy> lockPolicy();
}
