package vn.edu.hcmute.uteexpress.service.assistant.impl;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import vn.edu.hcmute.uteexpress.config.AssistantConfig;
import vn.edu.hcmute.uteexpress.service.assistant.*;

@Component
public class GroqAssistantAiGatewayImpl implements AssistantAiGateway {
    private static final URI ENDPOINT=URI.create("https://api.groq.com/openai/v1/chat/completions");
    private final AssistantConfig.Settings settings;
    private final ObjectMapper mapper;
    private final HttpClient http;
    @org.springframework.beans.factory.annotation.Autowired
    public GroqAssistantAiGatewayImpl(AssistantConfig.Settings settings,ObjectMapper mapper) {
        this(settings,mapper,HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    GroqAssistantAiGatewayImpl(AssistantConfig.Settings settings,ObjectMapper mapper,HttpClient http) {
        this.settings=settings; this.mapper=mapper; this.http=http;
    }
    @Override public JsonNode complete(ArrayNode messages,ArrayNode tools,String toolChoice,int timeoutSeconds) {
        if (!settings.configured()) throw new AssistantFailure("NOT_CONFIGURED",503,0);
        try {
            var body=mapper.createObjectNode(); body.put("model",settings.model()); body.set("messages",messages);
            body.put("max_completion_tokens",settings.maxCompletionTokens()); body.put("temperature",0.2);
            if (settings.model().startsWith("openai/gpt-oss-")) body.put("reasoning_effort","low");
            body.set("tools",tools); body.put("tool_choice",toolChoice); body.put("parallel_tool_calls",false); body.put("stream",false);
            var request=HttpRequest.newBuilder(ENDPOINT).timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Authorization","Bearer "+settings.apiKey()).header("Content-Type","application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body))).build();
            var future=http.sendAsync(request,info -> new LimitedJsonBodySubscriber());
            HttpResponse<byte[]> response;
            try { response=future.get(timeoutSeconds,TimeUnit.SECONDS); }
            finally { if (!future.isDone()) future.cancel(true); }
            int status=response.statusCode();
            if (status==401 || status==403) throw new AssistantFailure("NOT_CONFIGURED",503,0);
            if (status==429) {
                long delay=retryAfter(response.headers().firstValue("retry-after").orElse("60"));
                boolean quota=response.headers().firstValue("x-ratelimit-remaining-requests").orElse("").equals("0");
                try { String code=mapper.readTree(response.body()).path("error").path("code").asText(""); quota|=code.contains("quota"); }
                catch (Exception invalidBody) { quota|=delay>=3600; }
                throw new AssistantFailure(quota ? "QUOTA" : "RATE_LIMIT",429,quota ? Math.max(300,delay) : delay);
            }
            if (status!=200) throw new AssistantFailure("UPSTREAM_ERROR",502,5);
            var result=mapper.readTree(response.body()); var choice=result.path("choices").path(0);
            if (choice.path("finish_reason").asText().equals("length") || !choice.path("message").isObject())
                throw new AssistantFailure("INVALID_REPLY",502,5);
            return choice.path("message");
        } catch (AssistantFailure ex) { throw ex; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new AssistantFailure("TIMEOUT",504,5); }
        catch (TimeoutException ex) { throw new AssistantFailure("TIMEOUT",504,5); }
        catch (ExecutionException ex) {
            if (ex.getCause() instanceof HttpTimeoutException || ex.getCause() instanceof java.net.SocketTimeoutException)
                throw new AssistantFailure("TIMEOUT",504,5);
            throw new AssistantFailure("UPSTREAM_ERROR",502,5);
        }
        catch (Exception ex) { throw new AssistantFailure("UPSTREAM_ERROR",502,5); }
    }
    static long retryAfter(String value) {
        try { double seconds=Double.parseDouble(value); return Double.isFinite(seconds) ? Math.min(86400,Math.max(1,(long)Math.ceil(seconds))) : 60; }
        catch (NumberFormatException ex) {
            try { return Math.min(86400,Math.max(1,Duration.between(Instant.now(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).getSeconds())); }
            catch (RuntimeException invalid) { return 60; }
        }
    }
}
