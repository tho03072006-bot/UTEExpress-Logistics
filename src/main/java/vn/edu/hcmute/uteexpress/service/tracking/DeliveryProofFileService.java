package vn.edu.hcmute.uteexpress.service.tracking;

import java.nio.file.*;
import org.springframework.beans.factory.annotation.Value;
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
    private final Path uploadRoot;
    public DeliveryProofFileService(DeliveryProofRepository proofs, OrderJourneyService journey,
            @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.proofs = proofs; this.journey = journey; this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
    }
    public FileData read(Long id, String username, String kind) {
        var order = journey.accessibleOrder(id, username);
        if (!kind.equals("anh") && !kind.equals("chu-ky")) throw missing();
        var proof = proofs.findByOrder(order).orElseThrow(DeliveryProofFileService::missing);
        String stored = kind.equals("anh") ? proof.getProofImagePath() : proof.getSignaturePath();
        if (stored == null || !stored.matches("(/uploads/giao-hang/|/private-proof/)[a-fA-F0-9]{32}\\.(png|jpg|jpeg)")) throw missing();
        boolean legacy = stored.startsWith("/uploads/");
        Path root = legacy ? uploadRoot : uploadRoot.resolveSibling(uploadRoot.getFileName() + "-private");
        Path file = root.resolve("giao-hang").resolve(stored.substring(stored.lastIndexOf('/') + 1));
        try {
            if (!file.toRealPath().startsWith(root.toRealPath())) throw missing();
            byte[] bytes;
            try (var stream = Files.newInputStream(file)) { bytes = stream.readNBytes(5 * 1024 * 1024 + 1); }
            if (bytes.length < 8 || bytes.length > 5 * 1024 * 1024) throw missing();
            MediaType type;
            if ((bytes[0] & 255) == 255 && (bytes[1] & 255) == 216) type = MediaType.IMAGE_JPEG;
            else if ((bytes[0] & 255) == 137 && bytes[1] == 80 && bytes[2] == 78 && bytes[3] == 71) type = MediaType.IMAGE_PNG;
            else throw missing();
            return new FileData(bytes, type);
        } catch (java.io.IOException ex) { throw missing(); }
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Chưa có ảnh bằng chứng hợp lệ."); }
}
