package vn.edu.hcmute.uteexpress.util;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Bounded limits for one application instance. Keys never contain email or OTP in logs.
 * At capacity, requests are denied until old entries expire instead of evicting live limits.
 * Restart clears counters; consumed OTPs and expired codes are still removed from the DB.
 */
public final class OtpGuard {
    private static final int MAX_KEYS = 10_000;
    private final Clock clock;
    private final Map<String, Window> windows = new HashMap<>();

    public OtpGuard() { this(Clock.systemUTC()); }

    public OtpGuard(Clock clock) { this.clock = clock; }

    /** Returns remaining requests, or -1 if the limit is exhausted. */
    public synchronized int take(String key, int maximum, Duration duration) {
        long now = clock.millis();
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        Window window = windows.get(key);
        if (window == null) {
            if (windows.size() >= MAX_KEYS) return -1;
            window = new Window(now + duration.toMillis());
            windows.put(key, window);
        }
        if (window.count >= maximum) return -1;
        return maximum - ++window.count;
    }

    private static final class Window {
        private final long expiresAt;
        private int count;
        private Window(long expiresAt) { this.expiresAt = expiresAt; }
    }
}
