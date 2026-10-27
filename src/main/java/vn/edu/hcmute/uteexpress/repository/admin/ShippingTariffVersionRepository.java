package vn.edu.hcmute.uteexpress.repository.admin;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.ShippingTariffVersion;
public interface ShippingTariffVersionRepository extends JpaRepository<ShippingTariffVersion, Long> {
    @Query("select v from ShippingTariffVersion v where v.serviceType = :type and v.cancelled = false and v.startsAt <= :at and v.endsAt > :at")
    List<ShippingTariffVersion> active(@Param("type") Order.ServiceType type, @Param("at") LocalDateTime at);
    /** Phiên bản đang hiệu lực của mọi dịch vụ trong một truy vấn (bảng cước trên trang tạo đơn, ước tính cước). */
    @Query("select v from ShippingTariffVersion v where v.cancelled = false and v.startsAt <= :at and v.endsAt > :at")
    List<ShippingTariffVersion> activeAt(@Param("at") LocalDateTime at);
    @Query("select count(v) from ShippingTariffVersion v where v.serviceType = :type and v.cancelled = false and v.startsAt < :end and v.endsAt > :start")
    long overlaps(@Param("type") Order.ServiceType type, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
    Page<ShippingTariffVersion> findAllByOrderByStartsAtDescIdDesc(Pageable pageable);
}
