package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.RouteTariff;

/** Truy vấn dữ liệu RouteTariff; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface RouteTariffRepository extends JpaRepository<RouteTariff, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from RouteTariff t where t.id = :id")
    Optional<RouteTariff> lockById(Long id);
    Optional<RouteTariff> findByOriginBranch_IdAndDestBranch_IdAndServiceType(Long originId, Long destId, vn.edu.hcmute.uteexpress.entity.Order.ServiceType serviceType);
}
