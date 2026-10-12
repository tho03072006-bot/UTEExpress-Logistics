package vn.edu.hcmute.uteexpress.controller.tracking;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import vn.edu.hcmute.uteexpress.repository.DeliveryProofRepository;
import vn.edu.hcmute.uteexpress.security.OrderAccessPolicy;

@Controller
public class DeliveryProofFileController {
    private final OrderAccessPolicy access;
    private final DeliveryProofRepository proofs;
    private final Path folder;
    public DeliveryProofFileController(OrderAccessPolicy access, DeliveryProofRepository proofs,
            @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.access = access; this.proofs = proofs;
        this.folder = Path.of(uploadDir).toAbsolutePath().normalize().resolve("giao-hang");
    }

    @GetMapping("/tep-giao-hang/{orderId}/{kind}")
    public ResponseEntity<FileSystemResource> read(@PathVariable long orderId, @PathVariable String kind,
            Authentication authentication) throws IOException {
        if (orderId <= 0 || !(kind.equals("anh") || kind.equals("chu-ky"))) { throw missing(); }
        var order = access.requireReadableOrder(orderId, authentication);
        var proof = proofs.findByOrder(order).orElseThrow(this::missing);
        String stored = kind.equals("anh") ? proof.getProofImagePath() : proof.getSignaturePath();
        String prefix = "/uploads/giao-hang/";
        if (stored == null || !stored.startsWith(prefix)) { throw missing(); }
        String filename = stored.substring(prefix.length());
        if (!filename.matches("[a-fA-F0-9]{32}\\.(jpg|jpeg|png)")) { throw missing(); }
        Path target = folder.resolve(filename).normalize();
        if (!Files.isRegularFile(target) || Files.isSymbolicLink(target)
                || !target.toRealPath().startsWith(folder.toRealPath())) { throw missing(); }
        MediaType type = filename.endsWith("png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).header(HttpHeaders.CACHE_CONTROL, "no-store, private")
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(new FileSystemResource(target));
    }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp."); }
}
