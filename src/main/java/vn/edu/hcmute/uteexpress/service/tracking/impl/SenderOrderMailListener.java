package vn.edu.hcmute.uteexpress.service.tracking.impl;

import jakarta.annotation.PreDestroy;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.function.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import vn.edu.hcmute.uteexpress.dto.tracking.SenderOrderUpdateEvent;
import vn.edu.hcmute.uteexpress.service.profile.AccountProfileService;
import vn.edu.hcmute.uteexpress.util.MailFromNameSetter;

/**
 * Gửi email báo người gửi khi lấy hàng thất bại, giao thành công/thất bại hoặc hoàn trả.
 * Chạy sau commit trên một luồng riêng nên lỗi SMTP không làm hỏng thao tác của Shipper.
 * Máy chưa cấu hình mail (CHANGE_ME) hoặc địa chỉ thử nghiệm (.invalid, .test, example.com) thì bỏ qua.
 */
@Component
public class SenderOrderMailListener {
    private static final Logger log = LoggerFactory.getLogger(SenderOrderMailListener.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ObjectProvider<JavaMailSender> mailSender;
    private final MailFromNameSetter from;
    private final boolean enabled;
    private final Executor executor;
    /** Người gửi đã tắt "email cập nhật vận đơn" ở trang tài khoản thì không gửi. Kiểm tra trên luồng gửi mail. */
    private final Predicate<String> wantsEmail;

    @Autowired
    public SenderOrderMailListener(ObjectProvider<JavaMailSender> mailSender, MailFromNameSetter from,
            @Value("${app.mail.order-updates.enabled:true}") boolean enabled,
            @Value("${spring.mail.username:}") String username,
            ObjectProvider<AccountProfileService> profiles) {
        this(mailSender, from, enabled && configured(username), defaultExecutor(), email -> {
            AccountProfileService service = profiles.getIfAvailable();
            try { return service == null || service.wantsOrderEmail(email); }
            catch (RuntimeException ex) { return true; } // không đọc được tùy chọn thì giữ hành vi mặc định
        });
    }

    SenderOrderMailListener(ObjectProvider<JavaMailSender> mailSender, MailFromNameSetter from, boolean enabled,
            Executor executor) {
        this(mailSender, from, enabled, executor, email -> true);
    }

    SenderOrderMailListener(ObjectProvider<JavaMailSender> mailSender, MailFromNameSetter from, boolean enabled,
            Executor executor, Predicate<String> wantsEmail) {
        this.mailSender = mailSender; this.from = from; this.enabled = enabled; this.executor = executor;
        this.wantsEmail = wantsEmail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderUpdate(SenderOrderUpdateEvent event) {
        if (!enabled || !deliverable(event.email())) return;
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) return;
        try {
            executor.execute(() -> { if (wantsEmail.test(event.email())) send(sender, event); });
        } catch (RejectedExecutionException ex) {
            log.warn("Hang doi email day, bo qua email cap nhat van don {}", event.trackingCode());
        }
    }

    SimpleMailMessage message(SenderOrderUpdateEvent event) {
        var message = new SimpleMailMessage();
        from.applyTo(message);
        message.setTo(event.email().strip());
        message.setSubject("[UTEExpress] Vận đơn " + oneLine(event.trackingCode()) + " " + event.kind().subject());
        var body = new StringBuilder("Chào ").append(oneLine(event.recipientName()).isEmpty() ? "bạn" : oneLine(event.recipientName()))
                .append(",\n\nVận đơn ").append(oneLine(event.trackingCode())).append(": ").append(event.kind().headline()).append('\n');
        if (event.reason() != null) body.append("Lý do: ").append(oneLine(event.reason())).append('\n');
        if (event.nextAttemptAt() != null) body.append(event.kind() == SenderOrderUpdateEvent.Kind.PICKUP_FAILED
                ? "Hẹn lấy hàng lại: " : "Hẹn giao lại: ").append(TIME.format(event.nextAttemptAt())).append(" (giờ Việt Nam)\n");
        body.append("\nĐăng nhập UTEExpress, mục Đơn hàng của tôi để xem hành trình chi tiết.\n")
                .append("Email này được gửi tự động, vui lòng không trả lời.");
        message.setText(body.toString());
        return message;
    }

    private void send(JavaMailSender sender, SenderOrderUpdateEvent event) {
        try {
            sender.send(message(event));
        } catch (MailException ex) {
            log.warn("Khong gui duoc email cap nhat van don {}: {}", event.trackingCode(), ex.getClass().getSimpleName());
        }
    }

    static boolean configured(String username) {
        return username != null && username.contains("@") && !username.toUpperCase(Locale.ROOT).contains("CHANGE_ME");
    }

    static boolean deliverable(String email) {
        if (email == null || email.isBlank() || email.length() > 254) return false;
        String domain = email.strip().toLowerCase(Locale.ROOT);
        int at = domain.lastIndexOf('@');
        if (at < 1 || at == domain.length() - 1 || domain.chars().anyMatch(Character::isWhitespace)) return false;
        domain = domain.substring(at + 1);
        return !(domain.endsWith(".invalid") || domain.endsWith(".test") || domain.endsWith(".localhost")
                || domain.endsWith(".example") || domain.equals("localhost")
                || domain.matches("(.+\\.)?example\\.(com|net|org)"));
    }

    private static String oneLine(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").strip();
    }

    private static Executor defaultExecutor() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(200), task -> {
            Thread thread = new Thread(task, "sender-order-mail");
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        if (executor instanceof ExecutorService service) service.shutdown();
    }
}
