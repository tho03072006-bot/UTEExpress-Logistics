package vn.edu.hcmute.uteexpress.controller.profile;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.edu.hcmute.uteexpress.service.profile.AccountProfileService;

/**
 * Trả ảnh đại diện của chính người đang đăng nhập. Ảnh nằm ở Cloudinary dạng riêng tư nên phải đi qua
 * máy chủ; ETag theo phiên bản hồ sơ giúp trình duyệt dùng lại ảnh đã tải, không tải lại mỗi trang.
 */
public final class AvatarResponses {
    private static final CacheControl SHORT = CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate();
    private static final CacheControl LONG = CacheControl.maxAge(Duration.ofHours(1)).cachePrivate();

    /** Thuộc tính phiên mà fragments/base.html đọc để gắn ?v= vào ảnh đại diện; -1 nghĩa là chưa có ảnh. */
    public static final String SESSION_VERSION = "avatarVersion";

    private AvatarResponses() { }

    /** Ghi nhớ phiên bản ảnh trong phiên đăng nhập: đổi ảnh thì đường dẫn đổi theo, không có ảnh thì menu khỏi tải. */
    public static void remember(HttpSession session, AccountProfileService.View profile) {
        if (session != null && profile != null)
            session.setAttribute(SESSION_VERSION, profile.hasAvatar() ? profile.avatarVersion() : -1L);
    }

    public static ResponseEntity<byte[]> own(AccountProfileService profiles, String username, String ifNoneMatch) {
        var ref = profiles.avatarRef(username);
        if (ref.isEmpty()) return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(SHORT).build();
        String etag = "\"av-" + ref.get().version() + "\"";
        if (etag.equals(ifNoneMatch)) return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).cacheControl(LONG).build();
        try {
            var file = profiles.readAvatar(ref.get());
            return ResponseEntity.ok().contentType(file.type()).eTag(etag).cacheControl(LONG)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline").header("X-Content-Type-Options", "nosniff")
                    .body(file.bytes());
        } catch (RuntimeException ex) {
            // Cloud tạm lỗi: trang vẫn hiện chữ cái đầu thay cho ảnh, không hiện trang lỗi.
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore()).build();
        }
    }
}
