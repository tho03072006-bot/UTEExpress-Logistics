package vn.edu.hcmute.uteexpress.service;

import org.springframework.web.multipart.MultipartFile;
import vn.edu.hcmute.uteexpress.entity.ServiceReview;

/**
 * Lưu tệp người dùng tải lên (ảnh/video đính kèm đánh giá) - TV1 phụ trách (việc 4).
 *
 * Tệp thật KHÔNG nằm trong src/main/resources/static: thư mục đó được đóng gói vào file jar
 * lúc build, ghi vào đấy lúc chạy thì file sẽ mất mỗi lần build lại. Tệp được lưu ra một thư mục
 * riêng ngoài mã nguồn (mặc định là thư mục uploads/ ở gốc project, đổi được bằng
 * app.upload.dir trong application.properties) và được phục vụ qua đường dẫn /uploads/**
 * do WebMvcConfig cấu hình.
 */
public interface FileStorageService {

    /**
     * Lưu tệp đính kèm của đánh giá và trả về đường dẫn công khai (dạng "/uploads/danh-gia/...").
     *
     * Ném IllegalStateException nếu tệp rỗng, quá dung lượng cho phép, hoặc không phải
     * ảnh/video thuộc danh sách định dạng được chấp nhận.
     */
    String storeReviewMedia(MultipartFile file);

    /**
     * Suy ra đây là ảnh hay video để giao diện biết dùng thẻ img hay thẻ video.
     * Ném IllegalStateException nếu không nhận ra định dạng.
     */
    ServiceReview.MediaType detectMediaType(MultipartFile file);
}
