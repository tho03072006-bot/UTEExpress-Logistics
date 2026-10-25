package vn.edu.hcmute.uteexpress.repository.routing;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.routing.ShipperZone;

/** Truy vấn dữ liệu ShipperZone; quyền và nghiệp vụ được kiểm tra tại tầng service. */
public interface ShipperZoneRepository extends JpaRepository<ShipperZone, Long> {
    boolean existsByShipper_Id(Long shipperId);
    List<ShipperZone> findAllByShipper_Id(Long shipperId);
    /** Khu vực của nhiều shipper một lần, nạp sẵn khu vực (khóa area_code không phải khóa chính nên không lazy được). */
    @org.springframework.data.jpa.repository.Query("select z from ShipperZone z join fetch z.area where z.shipper.id in :ids")
    List<ShipperZone> findWithAreaByShipperIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<Long> ids);
}
