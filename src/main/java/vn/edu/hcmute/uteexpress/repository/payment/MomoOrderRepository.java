package vn.edu.hcmute.uteexpress.repository.payment;

import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface MomoOrderRepository extends Repository<Order, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id = :id")
    Optional<Order> lock(@Param("id") long id);
}
