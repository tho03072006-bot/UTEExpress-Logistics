package vn.edu.hcmute.uteexpress.service.media.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class BoundedBodySubscriberTest {
    @Test void rejectsOversizedResponseAndCancelsDownload() {
        var subscriber = new CloudinaryMediaGatewayImpl.BoundedBodySubscriber(4);
        var subscription = mock(Flow.Subscription.class); subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[5])));
        verify(subscription).cancel();
        assertThrows(ExecutionException.class,() -> subscriber.getBody().toCompletableFuture().get(1,TimeUnit.SECONDS));
    }
    @Test void permitsBoundedChunksAndCompletes() throws Exception {
        var subscriber = new CloudinaryMediaGatewayImpl.BoundedBodySubscriber(4);
        subscriber.onSubscribe(mock(Flow.Subscription.class));
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[]{1,2}),ByteBuffer.wrap(new byte[]{3,4})));
        subscriber.onComplete();
        assertArrayEquals(new byte[]{1,2,3,4},subscriber.getBody().toCompletableFuture().get(1,TimeUnit.SECONDS));
    }
}
