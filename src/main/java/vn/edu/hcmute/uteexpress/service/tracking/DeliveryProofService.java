package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import vn.edu.hcmute.uteexpress.entity.DeliveryProof;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface DeliveryProofService {

    Optional<DeliveryProof> findByOrder(Order order);

    boolean hasCompleteProof(Order order);

    DeliveryProof saveProof(
            Long orderId,
            String shipperUsername,
            MultipartFile proofImage,
            String signatureData);
}