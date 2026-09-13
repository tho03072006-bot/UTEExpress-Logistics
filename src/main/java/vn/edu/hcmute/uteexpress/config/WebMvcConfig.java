package vn.edu.hcmute.uteexpress.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Cho phép trình duyệt xem được ảnh/video người dùng tải lên - TV1 phụ trách (việc 4).
 *
 * Tệp tải lên được lưu ra thư mục uploads/ ở ngoài mã nguồn chứ không nằm trong
 * src/main/resources/static (thư mục đó bị đóng gói vào jar lúc build, ghi vào lúc chạy
 * thì mất file mỗi lần build lại). Vì nằm ngoài nên phải khai báo ở đây thì đường dẫn
 * /uploads/** mới trỏ tới được.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final Path uploadRoot;

    public WebMvcConfig(@Value("${app.upload.dir:uploads}") String uploadDir) {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // toUri() tự sinh đúng dạng "file:///..." cho cả Windows lẫn Linux,
        // khỏi phải tự nối chuỗi đường dẫn rồi lệch dấu gạch chéo giữa hai hệ điều hành.
        String location = uploadRoot.toUri().toString();

        // BẮT BUỘC có dấu "/" ở cuối. Thiếu dấu này, Spring ghép thẳng tên tệp vào sau
        // đường dẫn thư mục (".../uploadsdanh-gia/anh.png") và luôn báo không tìm thấy tệp.
        if (!location.endsWith("/")) {
            location = location + "/";
        }

        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
