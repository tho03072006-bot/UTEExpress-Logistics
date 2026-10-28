package vn.edu.hcmute.momosimulator;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Import;
import vn.edu.hcmute.uteexpress.config.MomoConfiguration;
import vn.edu.hcmute.uteexpress.service.impl.MomoPaymentServiceImpl;
import vn.edu.hcmute.uteexpress.service.payment.MomoTicketCodec;

/** Điểm chạy riêng: tuyệt đối không scan controller/admin/mail của ứng dụng local. */
@SpringBootApplication(exclude = {MailSenderAutoConfiguration.class, UserDetailsServiceAutoConfiguration.class})
@Import({MomoConfiguration.class, MomoPaymentServiceImpl.class, MomoTicketCodec.class})
public class MomoSimulatorApplication {
    public static void main(String[] args) {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        var app = new SpringApplication(MomoSimulatorApplication.class);
        app.setDefaultProperties(Map.of("spring.config.name", "momo-simulator"));
        app.run(args);
    }
}
