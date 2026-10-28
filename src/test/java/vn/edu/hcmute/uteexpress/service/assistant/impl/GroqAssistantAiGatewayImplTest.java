package vn.edu.hcmute.uteexpress.service.assistant.impl;

import com.fasterxml.jackson.databind.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.hcmute.uteexpress.config.AssistantConfig;
import vn.edu.hcmute.uteexpress.service.assistant.AssistantFailure;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** HTTP giả ở lớp truyền tải, không gọi Groq thật và không cần secret. */
class GroqAssistantAiGatewayImplTest {
    final ObjectMapper mapper=new ObjectMapper();
    final HttpClient http=mock(HttpClient.class);
    final AssistantConfig.Settings settings=new AssistantConfig.Settings(true,"gsk_test_only_not_a_real_key_123456","openai/gpt-oss-20b",18,1600,3);
    GroqAssistantAiGatewayImpl gateway;
    @BeforeEach void setup() { gateway=new GroqAssistantAiGatewayImpl(settings,mapper,http); }
    @SuppressWarnings("unchecked") void respond(int status,String body,Map<String,List<String>> headers) {
        HttpResponse<byte[]> response=mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status); when(response.body()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(response.headers()).thenReturn(HttpHeaders.of(headers,(a,b)->true));
        when(http.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.completedFuture(response));
    }
    JsonNode call() { var messages=mapper.createArrayNode(); messages.addObject().put("role","user").put("content","Đóng gói thế nào?"); return gateway.complete(messages,mapper.createArrayNode(),"auto",3); }
    @Test void readsOnlyAssistantMessageAndUsesFixedEndpoint() {
        respond(200,"{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"role\":\"assistant\",\"content\":\"Hướng dẫn\"}}]}",Map.of());
        assertEquals("Hướng dẫn",call().path("content").asText());
        var request=org.mockito.ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).sendAsync(request.capture(),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
        assertEquals("https://api.groq.com/openai/v1/chat/completions",request.getValue().uri().toString());
        assertEquals("POST",request.getValue().method()); assertEquals(Duration.ofSeconds(3),request.getValue().timeout().orElseThrow());
        assertFalse(settings.toString().contains(settings.apiKey()));
    }
    @Test void acceptsLocalToolCalls() {
        respond(200,"{\"choices\":[{\"finish_reason\":\"tool_calls\",\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call_1\",\"type\":\"function\",\"function\":{\"name\":\"get_guidance\",\"arguments\":\"{}\"}}]}}]}",Map.of());
        assertEquals("get_guidance",call().path("tool_calls").path(0).path("function").path("name").asText());
    }
    @ParameterizedTest @ValueSource(ints={401,403}) void invalidKeyDoesNotExposeProviderError(int status) {
        respond(status,"SECRET upstream internals",Map.of());
        var failure=assertThrows(AssistantFailure.class,this::call);
        assertEquals("NOT_CONFIGURED",failure.getCode()); assertEquals(503,failure.getStatus());
        assertNull(failure.getCause()); assertFalse(failure.getMessage().contains("SECRET"));
    }
    @Test void honorsRetryAfterAndDistinguishesDailyQuota() {
        respond(429,"{\"error\":{\"code\":\"rate_limit_exceeded\"}}",Map.of("retry-after",List.of("12.5")));
        var failure=assertThrows(AssistantFailure.class,this::call); assertEquals("RATE_LIMIT",failure.getCode()); assertEquals(13,failure.getRetryAfter());
        respond(429,"{}",Map.of("retry-after",List.of("7200"),"x-ratelimit-remaining-requests",List.of("0")));
        failure=assertThrows(AssistantFailure.class,this::call); assertEquals("QUOTA",failure.getCode()); assertEquals(7200,failure.getRetryAfter());
    }
    @ParameterizedTest @ValueSource(ints={400,404,500,503}) void serverErrorsRemainSafe(int status) {
        respond(status,"raw SECRET",Map.of());
        assertEquals("UPSTREAM_ERROR",assertThrows(AssistantFailure.class,this::call).getCode());
    }
    @ParameterizedTest @ValueSource(strings={"not JSON","{}","{\"choices\":[{\"finish_reason\":\"length\",\"message\":{}}]}"})
    void malformedOrTruncatedResponseIsNotAnAnswer(String body) {
        respond(200,body,Map.of()); assertThrows(AssistantFailure.class,this::call);
    }
    @Test void requestTimeoutIsExplicitAndNotRetried() {
        when(http.sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
                .thenReturn(CompletableFuture.failedFuture(new HttpTimeoutException("SECRET")));
        var error=assertThrows(AssistantFailure.class,this::call); assertEquals("TIMEOUT",error.getCode()); assertEquals(504,error.getStatus());
        verify(http,times(1)).sendAsync(any(HttpRequest.class),org.mockito.ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
    }
    @Test void missingConfigNeverSendsHttp() {
        gateway=new GroqAssistantAiGatewayImpl(new AssistantConfig.Settings(true,"CHANGE_ME",settings.model(),18,1600,3),mapper,http);
        assertEquals("NOT_CONFIGURED",assertThrows(AssistantFailure.class,this::call).getCode()); verifyNoInteractions(http);
    }
    @Test void retryAfterAcceptsDatesAndBoundsInvalidValues() {
        assertEquals(60,GroqAssistantAiGatewayImpl.retryAfter("NaN")); assertEquals(60,GroqAssistantAiGatewayImpl.retryAfter("bad"));
        assertEquals(86400,GroqAssistantAiGatewayImpl.retryAfter("99999999999")); assertEquals(1,GroqAssistantAiGatewayImpl.retryAfter("-1"));
        String future=ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(120).format(DateTimeFormatter.RFC_1123_DATE_TIME);
        long delay=GroqAssistantAiGatewayImpl.retryAfter(future); assertTrue(delay>=118 && delay<=120);
    }
    @Test void oversizedProviderBodyCancelsBeforeUnboundedAllocation() {
        var body=new LimitedJsonBodySubscriber(); var subscription=mock(Flow.Subscription.class); body.onSubscribe(subscription);
        body.onNext(List.of(ByteBuffer.allocate(128*1024+1)));
        verify(subscription).cancel(); assertThrows(CompletionException.class,()->body.getBody().toCompletableFuture().join());
    }
}
