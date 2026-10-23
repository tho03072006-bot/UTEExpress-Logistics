package vn.edu.hcmute.uteexpress.service.tracking;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.multipart.MultipartFile;

import vn.edu.hcmute.uteexpress.entity.DeliveryProof;
import vn.edu.hcmute.uteexpress.entity.Order;

public interface DeliveryProofService {

    Optional<DeliveryProof> findByOrder(Order order);

    boolean hasCompleteProof(Order order);

    /** Bằng chứng đủ ảnh và chữ ký đã lưu trên cloud, tức là mở xem được; tệp cũ chưa chuyển thì rỗng. */
    Optional<DeliveryProof> findViewableByOrder(Order order);

    /** Mã vận đơn -> id đơn, chỉ gồm các đơn có bằng chứng xem được; dùng cho bảng danh sách đơn. */
    Map<String, Long> orderIdsWithProof(Collection<String> trackingCodes);

    DeliveryProof saveProof(
            Long orderId,
            String shipperUsername,
            MultipartFile proofImage,
            String signatureData);
}