package vn.edu.hcmute.uteexpress.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.edu.hcmute.uteexpress.entity.DeliveryProof;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface DeliveryProofRepository
        extends JpaRepository<DeliveryProof, Long> {

    Optional<DeliveryProof> findByOrder(Order order);

    boolean existsByOrder(Order order);
}