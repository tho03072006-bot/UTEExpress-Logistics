package vn.edu.hcmute.uteexpress.security.admin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class StaffLoginAttemptService {
    private static final int MAX_ATTEMPTS = 5;
    private static final int MAX_TRACKED_NAMES = 10000;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Map<String, Attempt> attempts = new HashMap<>();
    private final Clock clock;

    public StaffLoginAttemptService(Clock clock) { this.clock = clock; }

    public synchronized boolean isBlocked(String username) {
        clearExpired();
        Attempt attempt = attempts.get(normalize(username));
        return attempt != null ? attempt.count >= MAX_ATTEMPTS : attempts.size() >= MAX_TRACKED_NAMES;
    }

    public synchronized void recordFailure(String username) {
        clearExpired();
        String key = normalize(username);
        Attempt old = attempts.get(key);
        if (old == null && attempts.size() >= MAX_TRACKED_NAMES) { return; }
        attempts.put(key, old == null ? new Attempt(1, clock.instant().plus(WINDOW))
                : new Attempt(Math.min(MAX_ATTEMPTS, old.count + 1), old.expiresAt));
    }

    public synchronized void clearFailures(String username) { attempts.remove(normalize(username)); }

    private void clearExpired() {
        Instant now = clock.instant();
        attempts.values().removeIf(a -> !a.expiresAt.isAfter(now));
    }
    private String normalize(String username) {
        String value = username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
        return value.substring(0, Math.min(50, value.length()));
    }
    private record Attempt(int count, Instant expiresAt) { }
}
