package vn.edu.hcmute.uteexpress.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra phần ghép tên người gửi cho email hệ thống.
 *
 * Không cần SMTP thật: lớp này chỉ ghép chuỗi rồi gắn vào SimpleMailMessage, nên đọc
 * lại getFrom() là biết đúng hay sai. Nhờ vậy vẫn kiểm được hành vi ngay cả khi máy
 * chưa cấu hình được hộp thư.
 */
class MailFromNameSetterTest {

    @Test
    @DisplayName("Ghép tên hiển thị với địa chỉ theo đúng dạng Gmail chấp nhận")
    void datTenHienThiKemDiaChi() {
        MailFromNameSetter setter = new MailFromNameSetter("UTEExpress", "uteexpress8@gmail.com");
        SimpleMailMessage message = new SimpleMailMessage();

        setter.applyTo(message);

        assertThat(message.getFrom()).isEqualTo("UTEExpress <uteexpress8@gmail.com>");
    }

    @Test
    @DisplayName("Cắt khoảng trắng thừa hai đầu trước khi ghép")
    void catKhoangTrangThua() {
        MailFromNameSetter setter = new MailFromNameSetter("  UTEExpress  ", "  uteexpress8@gmail.com  ");
        SimpleMailMessage message = new SimpleMailMessage();

        setter.applyTo(message);

        assertThat(message.getFrom()).isEqualTo("UTEExpress <uteexpress8@gmail.com>");
    }

    @Test
    @DisplayName("Máy chưa cấu hình hộp thư thì không đặt người gửi, để Spring tự xử lý")
    void chuaCauHinhThiKhongDatGi() {
        // Đây đúng là tình trạng của máy chưa điền spring.mail.username:
        // không được ném lỗi hay gắn người gửi cụt như "UTEExpress <>".
        MailFromNameSetter setter = new MailFromNameSetter("UTEExpress", "");
        SimpleMailMessage message = new SimpleMailMessage();

        setter.applyTo(message);

        assertThat(message.getFrom()).isNull();
    }

    @Test
    @DisplayName("Bỏ trống tên hiển thị thì cũng không đặt người gửi")
    void khongCoTenHienThiThiBoQua() {
        MailFromNameSetter setter = new MailFromNameSetter("   ", "uteexpress8@gmail.com");
        SimpleMailMessage message = new SimpleMailMessage();

        setter.applyTo(message);

        assertThat(message.getFrom()).isNull();
    }
}
