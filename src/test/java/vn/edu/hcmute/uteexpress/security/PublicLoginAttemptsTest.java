package vn.edu.hcmute.uteexpress.security;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicLoginAttemptsTest {
    @Test void fifthFailureBlocksOnlyMatchingAccountAndSourceAndSuccessClearsIt() {
        var attempts = new PublicLoginAttempts(Clock.fixed(Instant.parse("2026-10-12T07:00:00Z"), ZoneOffset.UTC));
        for (int i = 0; i < 5; i++) attempts.fail("thotest", "127.0.0.1");
        assertTrue(attempts.blocked("THOTEST", "127.0.0.1"));
        assertFalse(attempts.blocked("thotest", "another-source"));
        assertFalse(attempts.blocked("another-account", "127.0.0.1"));
        attempts.clear("thotest", "127.0.0.1");
        assertFalse(attempts.blocked("thotest", "127.0.0.1"));
    }
}
