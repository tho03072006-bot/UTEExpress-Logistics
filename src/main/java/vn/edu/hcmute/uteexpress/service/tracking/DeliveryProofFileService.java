package vn.edu.hcmute.uteexpress.service.tracking;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.edu.hcmute.uteexpress.repository.DeliveryProofRepository;

@Service
@Transactional(readOnly = true)
public class DeliveryProofFileService {
    public record FileData(byte[] bytes, MediaType type) { }
    private final DeliveryProofRepository proofs;
    private final OrderJourneyService journey;
    private final vn.edu.hcmute.uteexpress.service.media.CloudMediaStorageService cloud;
    public DeliveryProofFileService(DeliveryProofRepository proofs, OrderJourneyService journey,
            vn.edu.hcmute.uteexpress.service.media.CloudMediaStorageService cloud) {
        this.proofs = proofs; this.journey = journey; this.cloud = cloud;
    }
    public FileData read(Long id, String username, String kind) {
        var order = journey.accessibleOrder(id, username);
        if (!kind.equals("anh") && !kind.equals("chu-ky")) throw missing();
        var proof = proofs.findByOrder(order).orElseThrow(DeliveryProofFileService::missing);
        String stored = kind.equals("anh") ? proof.getProofImagePath() : proof.getSignaturePath();
        if (stored == null) throw missing();
        if (!stored.startsWith("cld1:")) throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Tệp cũ chưa có trên kho cloud chung; cần bản gốc để chuyển dữ liệu.");
        var file = cloud.read(stored,kind.equals("anh") ? vn.edu.hcmute.uteexpress.service.media.MediaPurpose.PROOF
                : vn.edu.hcmute.uteexpress.service.media.MediaPurpose.SIGNATURE);
        return new FileData(file.bytes(),file.type());
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa có ảnh bằng chứng hợp lệ."); }
}
