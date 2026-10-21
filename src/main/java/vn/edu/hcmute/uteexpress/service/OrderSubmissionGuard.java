package vn.edu.hcmute.uteexpress.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.hcmute.uteexpress.dto.OrderCreateRequest;
import vn.edu.hcmute.uteexpress.entity.AppUser;
import vn.edu.hcmute.uteexpress.entity.OrderFormSubmission;
import vn.edu.hcmute.uteexpress.entity.OrderFormSubmission.Purpose;
import vn.edu.hcmute.uteexpress.repository.OrderFormSubmissionRepository;
import vn.edu.hcmute.uteexpress.repository.OrderRepository;

/** Gọi sau khi khóa người gửi. UNIQUE token phân xử cả hai tài khoản hoặc hai luồng khác nhau. */
@Service @Transactional(propagation = Propagation.MANDATORY)
public class OrderSubmissionGuard {
    private final OrderFormSubmissionRepository submissions;
    private final OrderRepository orders;
    private final Clock clock;
    public OrderSubmissionGuard(OrderFormSubmissionRepository submissions, OrderRepository orders, Clock clock) {
        this.submissions = submissions; this.orders = orders; this.clock = clock;
    }
    public static boolean validToken(String token) {
        return token != null && token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }
    public OrderFormSubmission reserve(OrderCreateRequest request, AppUser user, Purpose purpose) {
        if (!validToken(request.getSubmissionToken()))
            throw new IllegalStateException("Phiên biểu mẫu không hợp lệ. Hãy mở lại trang tạo đơn.");
        String hash = fingerprint(request);
        var existing = submissions.findByToken(request.getSubmissionToken());
        if (existing.isPresent()) {
            var record = existing.get();
            if (!record.getUser().getId().equals(user.getId()) || !purpose.name().equals(record.getPurpose())
                    || !hash.equals(record.getRequestHash()) || record.getResourceId() == null)
                throw reused();
            return record;
        }
        // Các đơn được tạo trước migration đã có creation_token nhưng chưa có dấu vết mới.
        if (purpose == Purpose.DRAFT && orders.findByCreationToken(request.getSubmissionToken()).isPresent()) throw reused();
        return submissions.saveAndFlush(new OrderFormSubmission(request.getSubmissionToken(), user, hash, purpose, LocalDateTime.now(clock)));
    }
    public static IllegalStateException reused() {
        return new IllegalStateException("Phiên biểu mẫu đã được xử lý hoặc dùng cho dữ liệu khác. Hãy mở lại trang tạo đơn.");
    }
    public static String fingerprint(OrderCreateRequest request) {
        var canonical = new StringBuilder();
        for (String part : List.of(request.getSenderName(), request.getSenderAddress(), request.getReceiverName(),
                request.getReceiverPhone(), request.getReceiverAddress(), BigDecimal.valueOf(request.getWeightKg()).stripTrailingZeros().toPlainString(),
                request.getServiceType().name(), request.getPaymentMethod().name(),
                request.getPromoCode() == null ? "" : request.getPromoCode().toUpperCase(Locale.ROOT)))
            canonical.append(part.length()).append(':').append(part);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("Không hỗ trợ SHA-256.", ex); }
    }
}
