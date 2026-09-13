package vn.edu.hcmute.uteexpress.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;
import vn.edu.hcmute.uteexpress.service.FileStorageService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    /** Thư mục con dành cho ảnh/video của đánh giá, nằm trong thư mục upload gốc. */
    private static final String REVIEW_SUB_FOLDER = "danh-gia";

    /** Đường dẫn công khai mà trình duyệt dùng để tải tệp về (xem WebMvcConfig). */
    private static final String PUBLIC_URL_PREFIX = "/uploads/";

    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024; // 10 MB

    /**
     * Danh sách trắng các đuôi tệp được phép, kèm loại tương ứng.
     * Dùng danh sách trắng chứ không phải danh sách đen: chỉ thứ có tên ở đây mới được lưu,
     * mọi đuôi lạ đều bị từ chối - an toàn hơn là đi liệt kê các đuôi nguy hiểm rồi bỏ sót.
     */
    private static final Map<String, ServiceReview.MediaType> ALLOWED_EXTENSIONS = Map.of(
            "jpg", ServiceReview.MediaType.IMAGE,
            "jpeg", ServiceReview.MediaType.IMAGE,
            "png", ServiceReview.MediaType.IMAGE,
            "gif", ServiceReview.MediaType.IMAGE,
            "webp", ServiceReview.MediaType.IMAGE,
            "mp4", ServiceReview.MediaType.VIDEO,
            "webm", ServiceReview.MediaType.VIDEO
    );

    private final Path uploadRoot;

    public FileStorageServiceImpl(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String storeReviewMedia(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalStateException("Tệp đính kèm đang trống, vui lòng chọn lại.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalStateException("Tệp đính kèm tối đa 10 MB. Vui lòng chọn tệp nhỏ hơn.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.containsKey(extension)) {
            throw new IllegalStateException(
                    "Chỉ nhận ảnh (jpg, jpeg, png, gif, webp) hoặc video (mp4, webm).");
        }

        // Tự sinh tên tệp thay vì dùng tên người dùng gửi lên. Tên do người dùng đặt có thể chứa
        // "../" để ghi đè tệp ở thư mục khác, hoặc trùng tên với tệp của người khác.
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path folder = uploadRoot.resolve(REVIEW_SUB_FOLDER);
        Path target = folder.resolve(storedName).normalize();

        // Chốt chặn cuối: đường dẫn sau khi ghép phải vẫn nằm trong thư mục upload.
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalStateException("Đường dẫn tệp không hợp lệ.");
        }

        try {
            Files.createDirectories(folder);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Không lưu được tệp đính kèm, vui lòng thử lại.", ex);
        }

        return PUBLIC_URL_PREFIX + REVIEW_SUB_FOLDER + "/" + storedName;
    }

    @Override
    public ServiceReview.MediaType detectMediaType(MultipartFile file) {
        String extension = extractExtension(file == null ? null : file.getOriginalFilename());
        ServiceReview.MediaType type = ALLOWED_EXTENSIONS.get(extension);
        if (type == null) {
            throw new IllegalStateException(
                    "Chỉ nhận ảnh (jpg, jpeg, png, gif, webp) hoặc video (mp4, webm).");
        }
        return type;
    }

    /** Lấy phần đuôi tệp, trả về chuỗi rỗng nếu tên tệp không có đuôi. */
    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
