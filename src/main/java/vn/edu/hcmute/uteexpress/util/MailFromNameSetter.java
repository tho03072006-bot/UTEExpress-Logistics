package vn.edu.hcmute.uteexpress.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Component;

/**
 * Đặt tên hiển thị của người gửi cho các email hệ thống gửi đi - TV1 phụ trách.
 *
 * Vì sao cần: nếu không đặt gì, Spring Mail lấy thẳng spring.mail.username làm người
 * gửi, nên người nhận thấy tên tài khoản Gmail cá nhân của bạn nào đang cấu hình máy
 * mình. Đặt tên cố định ở đây thì dù ai chạy, ai cấu hình, người nhận cũng luôn thấy
 * "UTEExpress" - chuyên nghiệp và thống nhất cho cả nhóm.
 *
 * Giới hạn của Gmail: KHÔNG đổi được phần địa chỉ, thư vẫn phải gửi từ đúng hộp đã
 * xác thực SMTP. Chỉ phần tên hiển thị phía trước là tự đặt được:
 *
 *     UTEExpress &lt;uteexpress8@gmail.com&gt;
 *
 * Tách thành lớp riêng thay vì chép vào từng service để sau này đổi tên chỉ phải sửa
 * một chỗ, và tránh lặp đoạn ghép chuỗi ở nhiều nơi.
 */
@Component
public class MailFromNameSetter {

    private final String fromName;
    private final String mailUsername;

    public MailFromNameSetter(@Value("${app.mail.from-name:}") String fromName,
                              @Value("${spring.mail.username:}") String mailUsername) {
        this.fromName = fromName;
        this.mailUsername = mailUsername;
    }

    /**
     * Gắn người gửi dạng "Tên hiển thị &lt;địa chỉ&gt;" vào thư.
     *
     * Thiếu một trong hai giá trị thì không đặt gì cả - để Spring tự xử lý như trước.
     * Làm vậy để máy nào chưa cấu hình mail vẫn chạy được, không ném lỗi thêm.
     */
    public void applyTo(SimpleMailMessage message) {
        if (fromName == null || fromName.isBlank()
                || mailUsername == null || mailUsername.isBlank()) {
            return;
        }
        message.setFrom(fromName.trim() + " <" + mailUsername.trim() + ">");
    }
}
