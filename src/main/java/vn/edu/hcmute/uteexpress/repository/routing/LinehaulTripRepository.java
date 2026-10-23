package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import vn.edu.hcmute.uteexpress.entity.routing.LinehaulTrip;

/** Truy vấn dữ liệu LinehaulTrip; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface LinehaulTripRepository extends JpaRepository<LinehaulTrip, Long> {
    Optional<LinehaulTrip> findByCode(String code);
    boolean existsByCode(String code);
    List<LinehaulTrip> findAllByFromBranch_IdAndStatusOrderByIdDesc(Long branchId, LinehaulTrip.Status status);
    List<LinehaulTrip> findAllByToBranch_IdAndStatusOrderByIdDesc(Long branchId, LinehaulTrip.Status status);
    List<LinehaulTrip> findTop10ByToBranch_IdAndStatusOrderByArrivedAtDesc(Long branchId, LinehaulTrip.Status status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from LinehaulTrip t where t.id = :id")
    Optional<LinehaulTrip> findLockedById(Long id);
}
