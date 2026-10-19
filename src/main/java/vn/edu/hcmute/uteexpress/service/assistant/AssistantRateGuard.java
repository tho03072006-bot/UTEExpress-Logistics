package vn.edu.hcmute.uteexpress.service.assistant;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.config.AssistantConfig;

/** Chỉ lưu bộ đếm và ID tài khoản, không cache nội dung chat/dữ liệu cá nhân giữa tài khoản. */
@Component
public class AssistantRateGuard {
    private static final int MAX_ACCOUNTS=2000;
    private final Map<Long,Gate> gates=new HashMap<>();
    private final Semaphore global=new Semaphore(2);
    private final Clock clock;
    private final AssistantConfig.Settings settings;
    private Instant providerBlockedUntil=Instant.EPOCH;
    private static final class Gate {
        final Deque<Instant> attempts=new ArrayDeque<>(); boolean busy; Instant touched=Instant.EPOCH;
    }
    public AssistantRateGuard(Clock clock,AssistantConfig.Settings settings) { this.clock=clock; this.settings=settings; }
    public synchronized AutoCloseable acquire(Long id) {
        Instant now=clock.instant();
        gates.entrySet().removeIf(entry -> !entry.getValue().busy && entry.getValue().touched.isBefore(now.minusSeconds(3600)));
        if (now.isBefore(providerBlockedUntil)) throw new AssistantFailure("RATE_LIMIT",429,remaining(providerBlockedUntil));
        var gate=gates.get(id);
        if (gate==null) { if (gates.size()>=MAX_ACCOUNTS) throw new AssistantFailure("BUSY",503,5); gate=new Gate(); gates.put(id,gate); }
        if (gate.busy) throw new AssistantFailure("BUSY",409,3);
        while (!gate.attempts.isEmpty() && !gate.attempts.peekFirst().isAfter(now.minusSeconds(3600))) gate.attempts.removeFirst();
        var minute=gate.attempts.stream().filter(at -> at.isAfter(now.minusSeconds(60))).toList();
        if (minute.size()>=settings.requestsPerMinute()) throw new AssistantFailure("ACCOUNT_RATE_LIMIT",429,remaining(minute.get(0).plusSeconds(60)));
        if (gate.attempts.size()>=30) throw new AssistantFailure("ACCOUNT_RATE_LIMIT",429,remaining(gate.attempts.peekFirst().plusSeconds(3600)));
        if (!global.tryAcquire()) throw new AssistantFailure("BUSY",503,3);
        gate.busy=true; gate.touched=now; gate.attempts.addLast(now); final Gate acquired=gate;
        return () -> { synchronized (this) { if (acquired.busy) { acquired.busy=false; acquired.touched=clock.instant(); global.release(); } } };
    }
    public synchronized void blockProvider(long seconds) {
        Instant until=clock.instant().plusSeconds(Math.min(86400,Math.max(1,seconds)));
        if (until.isAfter(providerBlockedUntil)) providerBlockedUntil=until;
    }
    private long remaining(Instant until) { return Math.max(1,(Duration.between(clock.instant(),until).toMillis()+999)/1000); }
}
