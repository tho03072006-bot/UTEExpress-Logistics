package vn.edu.hcmute.uteexpress.config;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Phiên bản tài nguyên tĩnh gắn vào link CSS/JS chung (?v=...). Mỗi lần khởi động lại app là một phiên bản mới,
 * nên trình duyệt tải CSS/JS vừa sửa thay vì dùng bản cũ trong bộ nhớ đệm (không cần Ctrl+F5).
 * Trang không đi qua controller (trang lỗi) không có giá trị này và dùng phiên bản dự phòng trong base.html.
 */
@ControllerAdvice
public class AssetVersion {
    private static final String VERSION = Long.toString(System.currentTimeMillis(), 36);

    @ModelAttribute("assetVersion")
    public String assetVersion() { return VERSION; }
}
