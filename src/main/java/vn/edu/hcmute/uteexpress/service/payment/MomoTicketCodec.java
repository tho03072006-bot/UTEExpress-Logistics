package vn.edu.hcmute.uteexpress.service.payment;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.entity.OrderPayment;

/** Vé AES-256-GCM: không để lộ id/tài khoản trong QR, không nhận số tiền từ trình duyệt. */
@Component
public class MomoTicketCodec {
    public static final Duration LIFETIME = Duration.ofMinutes(10);
    private static final byte[] AAD = "UTEExpress:momo-simulation:v1".getBytes(StandardCharsets.UTF_8);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final MomoSettings settings;
    public MomoTicketCodec(MomoSettings settings) { this.settings = settings; }

    public record Ticket(long orderId, long paymentId, long ownerId, long accessVersion,
                         long amount, long issuedAt, long expiresAt, String passwordDigest) { }

    public String issue(OrderPayment payment, Instant now) {
        if (now.getEpochSecond() < 0 || now.plus(LIFETIME).getEpochSecond() > 0xffffffffL)
            throw new IllegalStateException("Thời gian hệ thống không hợp lệ để tạo phiên thanh toán.");
        var user = payment.getOrder().getSender();
        var ticket = new Ticket(payment.getOrder().getId(), payment.getId(), user.getId(), user.getAccessVersion(),
                payment.getAmount().longValueExact(), now.getEpochSecond(), now.plus(LIFETIME).getEpochSecond(), digest(user.getPassword()));
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                out.writeByte(2); out.writeLong(ticket.orderId()); out.writeLong(ticket.paymentId());
                out.writeLong(ticket.ownerId()); out.writeLong(ticket.accessVersion()); out.writeLong(ticket.amount());
                out.writeInt((int) ticket.issuedAt()); out.writeInt((int) ticket.expiresAt());
                out.write(HexFormat.of().parseHex(ticket.passwordDigest()));
            }
            byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce);
            var encoded = new ByteArrayOutputStream(); encoded.write(nonce); encoded.write(cipher.doFinal(bytes.toByteArray()));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded.toByteArray());
        } catch (GeneralSecurityException | IOException exception) {
            throw new IllegalStateException("Không tạo được QR thanh toán. Vui lòng thử lại.", exception);
        }
    }

    public Ticket verify(String token, Instant now) {
        // Không ghi token hoặc exception giải mã vào log/response.
        if (token == null || token.length() < 100 || token.length() > 512 || !token.matches("[A-Za-z0-9_-]+")) throw invalid();
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            byte[] plain = cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(bytes, 12))
                    .doFinal(Arrays.copyOfRange(bytes, 12, bytes.length));
            try (var in = new DataInputStream(new ByteArrayInputStream(plain))) {
                if (in.readByte() != 2) throw invalid();
                var ticket = new Ticket(in.readLong(), in.readLong(), in.readLong(), in.readLong(), in.readLong(),
                        Integer.toUnsignedLong(in.readInt()), Integer.toUnsignedLong(in.readInt()), HexFormat.of().formatHex(in.readNBytes(32)));
                if (in.available() != 0 || ticket.orderId() <= 0 || ticket.paymentId() <= 0 || ticket.ownerId() <= 0
                        || ticket.accessVersion() < 0 || ticket.amount() < 0 || ticket.amount() > 999999999999L
                        || ticket.expiresAt() - ticket.issuedAt() != LIFETIME.toSeconds()
                        || ticket.issuedAt() > now.getEpochSecond() + 30 || ticket.expiresAt() <= now.getEpochSecond()
                        || !ticket.passwordDigest().matches("[a-f0-9]{64}")) throw invalid();
                return ticket;
            }
        } catch (GeneralSecurityException | IOException | IllegalArgumentException exception) { throw invalid(); }
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, settings.secretKey(), new GCMParameterSpec(128, nonce)); cipher.updateAAD(AAD);
        return cipher;
    }

    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Liên kết thanh toán không hợp lệ hoặc đã hết hạn. Hãy tạo QR mới từ UTEExpress."); }
}
