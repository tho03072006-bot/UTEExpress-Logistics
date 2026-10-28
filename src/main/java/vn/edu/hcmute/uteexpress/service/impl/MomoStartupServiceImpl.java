package vn.edu.hcmute.uteexpress.service.impl;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.*;
import org.springframework.stereotype.Service;
import vn.edu.hcmute.uteexpress.service.payment.*;

/**
 * Đánh thức MoMo trên Render ở tác vụ nền: một lượt khi Run web local và một lượt mỗi khi khách mở trang thanh toán
 * (Render Free ngủ sau ~15 phút). Không deploy, không keep-alive định kỳ, không khởi động simulator trên máy.
 */
@Service
public class MomoStartupServiceImpl implements MomoStartupService, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MomoStartupServiceImpl.class);
    private final MomoSettings settings;
    private final MomoHealthClient client;
    private final Environment environment;
    private final long retryDelayMillis;
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Đang có một lượt kiểm tra chạy: không gửi chồng yêu cầu khi khách tải lại trang liên tục. */
    private final AtomicBoolean running = new AtomicBoolean();
    /** Render vừa phản hồi trong khoảng này thì chắc chắn còn thức, không cần gọi lại. */
    private static final long RECENTLY_ONLINE_NANOS = TimeUnit.MINUTES.toNanos(2);
    private volatile URI origin;
    private volatile long onlineAtNanos;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "momo-render-startup"); thread.setDaemon(true); return thread;
    });
    private volatile Snapshot snapshot = new Snapshot("NOT_CHECKED", "Render chưa được kiểm tra trong phiên chạy này.");

    @Autowired public MomoStartupServiceImpl(MomoSettings settings, MomoHealthClient client, Environment environment) {
        this(settings, client, environment, Duration.ofSeconds(30));
    }
    public MomoStartupServiceImpl(MomoSettings settings, MomoHealthClient client, Environment environment, Duration retryDelay) {
        this.settings = settings; this.client = client; this.environment = environment;
        if (retryDelay == null || retryDelay.isNegative() || retryDelay.compareTo(Duration.ofSeconds(30)) > 0)
            throw new IllegalArgumentException("Khoảng chờ kiểm tra Render phải từ 0–30 giây.");
        this.retryDelayMillis = retryDelay.toMillis();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (closed.get() || !started.compareAndSet(false, true)) return;
        if (environment.acceptsProfiles(Profiles.of("isolated-logistics"))
                || !environment.getProperty("app.momo.startup-check.enabled", Boolean.class, true)) {
            update("DISABLED", "Kiểm tra Render tự động đã được tắt. Không gửi yêu cầu đánh thức Render."); return;
        }
        final URI origin;
        try { settings.secretKey(); origin = URI.create(settings.baseUrl()); }
        catch (IllegalStateException exception) {
            update("NOT_CONFIGURED", "MoMo chưa sẵn sàng: cần deploy Render và điền URL/khóa trong config/momo-local.properties. COD và web local vẫn hoạt động."); return;
        }
        this.origin = origin;
        startCheck();
    }

    @Override public void wakeForPayment() {
        if (closed.get() || origin == null) return;
        String state = snapshot.state();
        if ("DISABLED".equals(state) || "NOT_CONFIGURED".equals(state)) return;
        if ("ONLINE".equals(state) && System.nanoTime() - onlineAtNanos < RECENTLY_ONLINE_NANOS) return;
        startCheck();
    }

    private void startCheck() {
        if (!running.compareAndSet(false, true)) return;
        update("CHECKING", "Đang đánh thức MoMo mô phỏng, thường mất 30–60 giây. Nếu điện thoại hiện màn hình chờ của Render, cứ để yên, trang thanh toán sẽ tự mở.");
        try { worker.submit(() -> { try { check(origin); } finally { running.set(false); } }); }
        catch (RejectedExecutionException exception) { running.set(false); }
    }

    private void check(URI origin) {
        try {
            // Tối đa ba lần, nghỉ 30 giây cho cold start; không phải vòng lặp giữ Render luôn thức.
            for (int attempt = 0; attempt < 3 && !closed.get(); attempt++) {
                boolean reachable;
                try { reachable = client.isReachable(origin); }
                catch (RuntimeException exception) { reachable = false; }
                if (closed.get()) return;
                if (reachable) {
                    onlineAtNanos = System.nanoTime();
                    update("ONLINE", "MoMo mô phỏng đã sẵn sàng. Quét QR để thanh toán."); return;
                }
                if (attempt < 2) TimeUnit.MILLISECONDS.sleep(retryDelayMillis);
            }
            if (!closed.get()) update("UNREACHABLE", "MoMo chưa sẵn sàng hoặc Render đang chạy bản cũ. Cần redeploy Render cùng phiên bản web local và kiểm tra mạng. Đơn chưa được coi là đã thanh toán.");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    @Override public Snapshot snapshot() { return snapshot; }
    private void update(String state, String message) { snapshot = new Snapshot(state, message); log.info("MoMo Render [{}]: {}", state, message); }
    @Override public String toString() { return "MomoStartupService[credentials=REDACTED]"; }
    @PreDestroy public void close() { closed.set(true); worker.shutdownNow(); }
}
