package vn.edu.hcmute.uteexpress.service.assistant.impl;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.io.IOException;

final class LimitedJsonBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private int size;
    private boolean stopped;
    @Override public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
    @Override public void onSubscribe(Flow.Subscription value) { subscription=value; delegate.onSubscribe(value); }
    @Override public void onNext(List<ByteBuffer> buffers) {
        if (stopped) return;
        for (var buffer : buffers) size+=buffer.remaining();
        if (size>128*1024) { stopped=true; subscription.cancel(); delegate.onError(new IOException("AI response limit exceeded.")); }
        else delegate.onNext(buffers);
    }
    @Override public void onError(Throwable value) { if (!stopped) delegate.onError(value); }
    @Override public void onComplete() { if (!stopped) delegate.onComplete(); }
}
