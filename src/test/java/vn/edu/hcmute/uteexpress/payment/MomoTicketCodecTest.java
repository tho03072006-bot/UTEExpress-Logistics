package vn.edu.hcmute.uteexpress.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.*;
import org.springframework.mock.env.MockEnvironment;
import vn.edu.hcmute.uteexpress.entity.*;
import vn.edu.hcmute.uteexpress.entity.Order;
import vn.edu.hcmute.uteexpress.service.payment.*;
import static org.junit.jupiter.api.Assertions.*;

class MomoTicketCodecTest {
    static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);
    final MomoSettings settings = new MomoSettings(new MockEnvironment().withProperty("MOMO_SIM_SECRET", KEY)
            .withProperty("MOMO_SIMULATOR_URL", "https://uteexpress-momo-test.onrender.com"));
    final MomoTicketCodec codec = new MomoTicketCodec(settings);
    final Instant now = Instant.parse("2026-10-19T10:00:00Z");
    OrderPayment payment;
    @BeforeEach void seed() {
        var user = new AppUser("alice", "private-bcrypt-hash", "alice@test.invalid"); user.setId(12L); user.revokeSessions(); user.revokeSessions(); user.revokeSessions();
        var order = new Order(); order.setId(44L); order.setSender(user);
        payment = new OrderPayment(); payment.setId(55L); payment.setOrder(order); payment.setAmount(new BigDecimal("18000"));
    }
    @Test void encryptedTicketBindsAllPaymentAndAccountFields() {
        String encoded = codec.issue(payment, now); var result = codec.verify(encoded, now);
        assertEquals(44, result.orderId()); assertEquals(55, result.paymentId()); assertEquals(12, result.ownerId());
        assertEquals(3, result.accessVersion()); assertEquals(18000, result.amount()); assertEquals(now.plusSeconds(600).getEpochSecond(), result.expiresAt());
        assertFalse(encoded.contains("alice")); assertNotEquals(encoded, codec.issue(payment, now));
    }
    @Test void everyModifiedCiphertextByteIsRejected() {
        byte[] bytes = Base64.getUrlDecoder().decode(codec.issue(payment, now));
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] ^= 1;
            String forged = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            assertThrows(IllegalArgumentException.class, () -> codec.verify(forged, now)); bytes[i] ^= 1;
        }
    }
    @Test void expirationHasNoGracePeriodForPayment() {
        String encoded = codec.issue(payment, now);
        assertDoesNotThrow(() -> codec.verify(encoded, now.plusSeconds(599)));
        assertThrows(IllegalArgumentException.class, () -> codec.verify(encoded, now.plusSeconds(600)));
        assertThrows(IllegalArgumentException.class, () -> codec.verify(encoded, now.minusSeconds(31)));
    }
    @Test void differentKeyCannotReadOrConfirm() {
        byte[] bytes = new byte[32]; bytes[0] = 1;
        var other = new MomoTicketCodec(new MomoSettings(new MockEnvironment().withProperty("MOMO_SIM_SECRET", Base64.getEncoder().encodeToString(bytes))));
        assertThrows(IllegalArgumentException.class, () -> other.verify(codec.issue(payment, now), now));
    }
    @Test void malformedAndHugeInputRejected() {
        for (String value : new String[]{"", "abc", "A".repeat(513), "<script>alert(1)</script>", "!".repeat(200)})
            assertThrows(IllegalArgumentException.class, () -> codec.verify(value, now));
        assertThrows(IllegalArgumentException.class, () -> codec.verify(null, now));
    }
    @Test void urlOnlyAllowsActualHttpsRenderOrigin() {
        assertEquals("https://uteexpress-momo-test.onrender.com", settings.baseUrl());
        for (String value : new String[]{"http://localhost:8080", "https://localhost", "https://fake.onrender.com@evil.com", "https://valid.onrender.com?x=1",
                "https://valid.onrender.com/path", "https://valid.onrender.com#token", "https://valid.onrender.com:123", "https://fake.onrender.com.evil.com"}) {
            assertThrows(IllegalStateException.class, () -> new MomoSettings(new MockEnvironment().withProperty("MOMO_SIMULATOR_URL", value)).baseUrl());
        }
    }
    @Test void missingSecretHasNoDefault() {
        assertThrows(IllegalStateException.class, () -> new MomoSettings(new MockEnvironment()).secretKey());
    }
}
