package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.Branch;

/** Truy vấn dữ liệu Branch; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface BranchRepository extends JpaRepository<Branch, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select b from Branch b where b.id = :id")
    Optional<Branch> lockById(Long id);
    Optional<Branch> findByCode(String code);
    List<Branch> findAllByActiveTrueOrderByCode();
}
