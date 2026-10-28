package vn.edu.hcmute.uteexpress.payment;

import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.edu.hcmute.uteexpress.service.impl.MomoHealthClientImpl;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MomoHealthClientTest {
    final HttpClient http = mock(HttpClient.class);
    final MomoHealthClientImpl client = new MomoHealthClientImpl(http);
    final URI origin = URI.create("https://uteexpress-momo-test.onrender.com");
    @SuppressWarnings("unchecked") private HttpResponse<byte[]> response(int status, String body) {
        HttpResponse<byte[]> response = mock(HttpResponse.class); when(response.statusCode()).thenReturn(status);
        when(response.headers()).thenReturn(HttpHeaders.of(java.util.Map.of("X-Momo-Protocol", List.of("3")), (name, value) -> true));
        if (status == 200) when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8)); return response;
    }
    @Test void startupOnlyGetsHealthWithoutCredentialsTicketsOrCustomerData() throws Exception {
        var result = CompletableFuture.completedFuture(response(200,"ok"));
        when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(result);
        assertTrue(client.isReachable(origin));
        var request = ArgumentCaptor.forClass(HttpRequest.class); verify(http).sendAsync(request.capture(), any(HttpResponse.BodyHandler.class));
        assertEquals("https://uteexpress-momo-test.onrender.com/healthz", request.getValue().uri().toString());
        assertEquals("GET", request.getValue().method()); assertTrue(request.getValue().bodyPublisher().isEmpty());
        assertTrue(request.getValue().headers().firstValue("Authorization").isEmpty());
        assertEquals(java.time.Duration.ofSeconds(90), request.getValue().timeout().orElseThrow());
    }
    @Test void loadingPageRedirectAndHttpFailureAreNotReportedAsOnline() throws Exception {
        for (int status : new int[]{200,302,404,500,503}) {
            var result = CompletableFuture.completedFuture(response(status,"<html>loading</html>"));
            when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(result);
            assertFalse(client.isReachable(origin));
        }
    }
    @Test void oldOrMissingProtocolIsNotAdvertisedAsCompatibleWithNewQrTickets() throws Exception {
        for (String protocol : new String[]{"", "2", "4"}) {
            var reply = response(200, "ok");
            when(reply.headers()).thenReturn(HttpHeaders.of(java.util.Map.of("X-Momo-Protocol", List.of(protocol)), (name, value) -> true));
            when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.completedFuture(reply));
            assertFalse(client.isReachable(origin));
        }
    }
    @Test void connectionFailureIsAnUnavailableResultNotASuccess() throws Exception {
        when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(CompletableFuture.failedFuture(new java.io.IOException("not connected")));
        assertFalse(client.isReachable(origin));
    }
    @Test void interruptedShutdownCancelsThePendingHttpRequest() throws Exception {
        var pending = new CompletableFuture<HttpResponse<byte[]>>();
        when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(pending);
        Thread.currentThread().interrupt();
        try { assertThrows(InterruptedException.class, () -> client.isReachable(origin)); assertTrue(pending.isCancelled()); }
        finally { Thread.interrupted(); }
    }
    @Test @SuppressWarnings("unchecked") void largeHealthBodyIsRejectedBeforeItIsBuffered() throws Exception {
        var result = CompletableFuture.completedFuture(response(200,"ok"));
        when(http.sendAsync(any(), any(HttpResponse.BodyHandler.class))).thenReturn(result);
        client.isReachable(origin);
        ArgumentCaptor<HttpResponse.BodyHandler<byte[]>> handler = ArgumentCaptor.forClass(HttpResponse.BodyHandler.class);
        verify(http).sendAsync(any(), handler.capture());
        var subscriber = handler.getValue().apply(mock(HttpResponse.ResponseInfo.class));
        var subscription = mock(java.util.concurrent.Flow.Subscription.class); subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(new byte[65]))); subscriber.onComplete();
        assertThrows(ExecutionException.class, () -> subscriber.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS));
        verify(subscription).cancel();
    }
}
