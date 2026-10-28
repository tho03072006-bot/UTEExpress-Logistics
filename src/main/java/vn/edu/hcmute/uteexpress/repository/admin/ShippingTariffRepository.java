package vn.edu.hcmute.uteexpress.repository.admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.entity.admin.ShippingTariff;
public interface ShippingTariffRepository extends JpaRepository<ShippingTariff, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select t from ShippingTariff t where t.serviceType = :type")
    Optional<ShippingTariff> lockByServiceType(@org.springframework.data.repository.query.Param("type") Order.ServiceType type);
    Optional<ShippingTariff> findByServiceType(Order.ServiceType serviceType);
}
