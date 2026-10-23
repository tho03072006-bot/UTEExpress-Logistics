package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.ShipperZone;

/** Truy vấn dữ liệu ShipperZone; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface ShipperZoneRepository extends JpaRepository<ShipperZone, Long> {
    boolean existsByShipper_Id(Long shipperId);
    List<ShipperZone> findAllByShipper_Id(Long shipperId);
}
