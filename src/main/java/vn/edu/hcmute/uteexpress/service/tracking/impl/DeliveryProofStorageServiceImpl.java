package vn.edu.hcmute.uteexpress.service.tracking.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import vn.edu.hcmute.uteexpress.service.tracking.DeliveryProofStorageService;

@Service
public class DeliveryProofStorageServiceImpl
        implements DeliveryProofStorageService {

    private static final String PROOF_FOLDER = "giao-hang";
    private static final String PRIVATE_PATH_PREFIX = "/private-proof/";
    private static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;
    private static final long MAX_SIGNATURE_SIZE = 2L * 1024 * 1024;

    private static final Set<String> ALLOWED_EXTENSIONS =
            Set.of("jpg", "jpeg", "png");

    private final Path uploadRoot;

    public DeliveryProofStorageServiceImpl(
            @Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir)
                .toAbsolutePath()
                .normalize().resolveSibling(Paths.get(uploadDir).toAbsolutePath().normalize().getFileName() + "-private");
    }

    @Override
    public String storeProofImage(MultipartFile proofImage) {
        if (proofImage == null || proofImage.isEmpty()) {
            throw new IllegalStateException(
                    "Vui lòng chọn ảnh bằng chứng giao hàng.");
        }

        if (proofImage.getSize() > MAX_IMAGE_SIZE) {
            throw new IllegalStateException(
                    "Ảnh bằng chứng không được vượt quá 5 MB.");
        }

        String extension =
                extractExtension(proofImage.getOriginalFilename());

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new IllegalStateException(
                    "Ảnh bằng chứng chỉ nhận JPG, JPEG hoặc PNG.");
        }

        validateImage(proofImage);

        Path target = createTarget(extension);

        try {
            try (InputStream input = proofImage.getInputStream()) {
                Files.copy(
                        input,
                        target,
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không lưu được ảnh bằng chứng.", exception);
        }

        return toStoredPath(target);
    }

    @Override
    public String storeSignature(String signatureData) {
        String prefix = "data:image/png;base64,";

        if (signatureData == null
                || !signatureData.startsWith(prefix)) {
            throw new IllegalStateException(
                    "Vui lòng ký xác nhận trước khi lưu.");
        }

        if (signatureData.length() > prefix.length() + (MAX_SIGNATURE_SIZE + 2) / 3 * 4) {
            throw new IllegalStateException("Chữ ký vượt quá 2 MB.");
        }
        byte[] imageBytes;

        try {
            imageBytes = Base64.getDecoder().decode(
                    signatureData.substring(prefix.length()));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Dữ liệu chữ ký không hợp lệ.", exception);
        }

        if (imageBytes.length == 0
                || imageBytes.length > MAX_SIGNATURE_SIZE) {
            throw new IllegalStateException(
                    "Chữ ký không hợp lệ hoặc vượt quá 2 MB.");
        }

        validateImageBytes(imageBytes);

        Path target = createTarget("png");

        try {
            Files.write(target, imageBytes);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không lưu được chữ ký.", exception);
        }

        return toStoredPath(target);
    }

    private void validateImage(MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            readBoundedImage(input, false);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không đọc được ảnh bằng chứng.", exception);
        }
    }

    private void validateImageBytes(byte[] imageBytes) {
        try (ByteArrayInputStream input =
                     new ByteArrayInputStream(imageBytes)) {
            readBoundedImage(input, true);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không đọc được dữ liệu chữ ký.", exception);
        }
    }

    private void readBoundedImage(InputStream source, boolean signature) throws IOException {
        try (var input = ImageIO.createImageInputStream(source)) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IllegalStateException("Dữ liệu không phải ảnh hợp lệ.");
            var reader = readers.next();
            try {
                var format = reader.getFormatName();
                if (!format.equalsIgnoreCase("PNG") && (signature || !format.equalsIgnoreCase("JPEG")))
                    throw new IllegalStateException("Ảnh chỉ nhận JPG/PNG; chữ ký cần PNG.");
                reader.setInput(input);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 4096 || height > 4096 || (long) width * height > 12_000_000)
                    throw new IllegalStateException("Ảnh tối đa 4096 px mỗi chiều và 12 triệu điểm ảnh.");
                reader.read(0);
            } finally { reader.dispose(); }
        }
    }

    private Path createTarget(String extension) {
        String storedName =
                UUID.randomUUID().toString().replace("-", "")
                        + "." + extension;

        Path folder = uploadRoot.resolve(PROOF_FOLDER).normalize();
        Path target = folder.resolve(storedName).normalize();

        if (!target.startsWith(uploadRoot)) {
            throw new IllegalStateException(
                    "Đường dẫn lưu tệp không hợp lệ.");
        }

        try {
            Files.createDirectories(folder);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Không tạo được thư mục lưu bằng chứng.",
                    exception);
        }

        return target;
    }

    private String toStoredPath(Path target) {
        return PRIVATE_PATH_PREFIX + target.getFileName();
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }

        int dot = originalFilename.lastIndexOf('.');

        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }

        return originalFilename
                .substring(dot + 1)
                .toLowerCase(Locale.ROOT);
    }
}
