package vn.edu.hcmute.uteexpress.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import vn.edu.hcmute.uteexpress.entity.DeliveryProof;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface DeliveryProofRepository
        extends JpaRepository<DeliveryProof, Long> {

    Optional<DeliveryProof> findByOrder(Order order);

    boolean existsByOrder(Order order);

    /** Mã vận đơn, id đơn, tham chiếu ảnh và chữ ký của những đơn trong danh sách đã có bằng chứng. */
    @Query("select p.order.trackingCode, p.order.id, p.proofImagePath, p.signaturePath"
            + " from DeliveryProof p where p.order.trackingCode in :codes")
    List<Object[]> findProofsByTrackingCodes(@Param("codes") Collection<String> codes);
}