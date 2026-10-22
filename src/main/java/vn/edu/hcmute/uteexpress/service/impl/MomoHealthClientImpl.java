package vn.edu.hcmute.uteexpress.service.impl;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.service.payment.MomoHealthClient;

@Component
public class MomoHealthClientImpl implements MomoHealthClient {
    /**
     * Render Free mất ~30–60 giây để dậy sau khi ngủ và chỉ trả lời khi đã dậy xong. Chờ ngắn hơn thì yêu cầu bị hủy
     * giữa chừng, lượt sau lại bắt đầu từ đầu nên không bao giờ thấy "ok" (log cũ: 3 lần × 20 giây đều thất bại).
     */
    static final Duration WAKE_TIMEOUT = Duration.ofSeconds(90);
    private volatile HttpClient client;
    @Autowired public MomoHealthClientImpl() { }
    public MomoHealthClientImpl(HttpClient client) { this.client = client; }

    @Override public boolean isReachable(URI origin) throws InterruptedException {
        var request = HttpRequest.newBuilder(origin.resolve("/healthz"))
                .timeout(WAKE_TIMEOUT).header("Accept", "text/plain").header("Cache-Control", "no-cache").GET().build();
        CompletableFuture<HttpResponse<byte[]>> pending = httpClient().sendAsync(request, info -> new LimitedBodySubscriber());
        try {
            var response = pending.get(WAKE_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            return response.statusCode() == 200 && "ok".equals(new String(response.body(), StandardCharsets.UTF_8).strip());
        } catch (ExecutionException | TimeoutException exception) {
            return false;
        } finally {
            if (!pending.isDone()) pending.cancel(true);
        }
    }

    private HttpClient httpClient() {
        if (client == null) synchronized (this) {
            if (client == null) client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NEVER).build();
        }
        return client;
    }

    /** Không giữ HTML/trang lỗi lớn trong bộ nhớ khi Render đang ngủ hoặc URL trỏ nhầm service. */
    static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
        private Flow.Subscription subscription;
        private long received;
        private boolean complete;
        @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
        @Override public void onSubscribe(Flow.Subscription subscription) { this.subscription = subscription; delegate.onSubscribe(subscription); }
        @Override public void onNext(List<ByteBuffer> buffers) {
            if (complete) return;
            for (var buffer : buffers) received += buffer.remaining();
            if (received > 64) {
                complete = true; subscription.cancel(); delegate.onError(new IOException("Phản hồi kiểm tra Render vượt giới hạn."));
            } else delegate.onNext(buffers);
        }
        @Override public void onError(Throwable exception) { if (!complete) { complete = true; delegate.onError(exception); } }
        @Override public void onComplete() { if (!complete) { complete = true; delegate.onComplete(); } }
    }
}
