package vn.edu.hcmute.uteexpress.service;

import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;

/**
 * Lưu tệp người dùng tải lên (ảnh/video đính kèm đánh giá) - TV1 phụ trách (việc 4).
 *
 * Tệp lưu authenticated trong Cloudinary chung. Aiven chỉ giữ mã tham chiếu;
 * trình duyệt xem qua /tep-danh-gia/{id}, không nhận khóa hay URL quản trị cloud.
 */
public interface FileStorageService {

    /**
     * Lưu tệp và trả về mã CloudMediaReference, không phải đường dẫn ổ đĩa/URL công khai.
     *
     * Ném IllegalArgumentException nếu tệp rỗng, quá dung lượng cho phép, hoặc không phải
     * ảnh/video thuộc danh sách định dạng được chấp nhận.
     */
    String storeReviewMedia(MultipartFile file);

    /**
     * Suy ra đây là ảnh hay video để giao diện biết dùng thẻ img hay thẻ video.
     * Ném IllegalArgumentException nếu không nhận ra định dạng.
     */
    ServiceReview.MediaType detectMediaType(MultipartFile file);
}
