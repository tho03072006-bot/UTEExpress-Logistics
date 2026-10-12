package vn.edu.hcmute.uteexpress.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Giới hạn theo cặp tài khoản/nguồn, tránh khóa tài khoản bởi một IP khác. */
@Component
public class PublicLoginAttempts {
    private record Window(int failures, Instant start) { }
    private final Map<String, Window> attempts = new HashMap<>();
    private final Clock clock;
    public PublicLoginAttempts() { this(Clock.systemUTC()); }
    public PublicLoginAttempts(Clock clock) { this.clock = clock; }
    public synchronized boolean blocked(String username, String source) {
        prune();
        Window window = attempts.get(key(username, source));
        return window != null ? window.failures() >= 5 : attempts.size() >= 10000;
    }
    public synchronized void fail(String username, String source) {
        prune(); String key = key(username, source); Window previous = attempts.get(key);
        if (previous == null && attempts.size() >= 10000) return;
        attempts.put(key, new Window(previous == null ? 1 : previous.failures() + 1,
                previous == null ? clock.instant() : previous.start()));
    }
    public synchronized void clear(String username, String source) { attempts.remove(key(username, source)); }
    private void prune() { Instant cutoff = clock.instant().minus(Duration.ofMinutes(15)); attempts.values().removeIf(w -> !w.start().isAfter(cutoff)); }
    private String key(String username, String source) {
        String value = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        if (value.length() > 50) value = value.substring(0, 50);
        return value + "|" + (source == null ? "" : source);
    }
}
