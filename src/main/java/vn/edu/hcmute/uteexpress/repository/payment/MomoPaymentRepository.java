package vn.edu.hcmute.uteexpress.repository.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;

public interface MomoPaymentRepository extends Repository<OrderPayment, Long> {
    @Query("select p from OrderPayment p join fetch p.order o join fetch o.sender where o.id = :id")
    Optional<OrderPayment> find(@Param("id") long orderId);
}
