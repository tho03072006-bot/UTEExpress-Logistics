package vn.edu.hcmute.uteexpress.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;

@Configuration
@PropertySource(value="file:./config/groq-local.properties",ignoreResourceNotFound=true)
public class AssistantConfig {
    @Bean
    public Settings assistantSettings(
            @Value("${ASSISTANT_ENABLED:${app.assistant.enabled:true}}") boolean enabled,
            @Value("${GROQ_API_KEY:${app.assistant.api-key:}}") String key,
            @Value("${GROQ_MODEL:${app.assistant.model:openai/gpt-oss-20b}}") String model,
            @Value("${app.assistant.timeout-seconds:18}") int timeout,
            @Value("${app.assistant.max-completion-tokens:1600}") int tokens,
            @Value("${app.assistant.requests-per-minute:3}") int rpm) {
        return new Settings(enabled,key.strip(),model.strip(),timeout,tokens,rpm);
    }
    public record Settings(boolean enabled,String apiKey,String model,int timeoutSeconds,int maxCompletionTokens,int requestsPerMinute) {
        public Settings {
            if (timeoutSeconds<3 || timeoutSeconds>20 || maxCompletionTokens<256 || maxCompletionTokens>2048 || requestsPerMinute<1 || requestsPerMinute>10)
                throw new IllegalArgumentException("Giới hạn chatbot không hợp lệ: timeout 3–20 giây, token 256–2048, lượt/phút 1–10.");
        }
        public boolean configured() {
            return enabled && apiKey!=null && apiKey.matches("gsk_[A-Za-z0-9_-]{16,250}")
                    && model!=null && model.matches("[a-zA-Z0-9][a-zA-Z0-9/_.-]{1,99}");
        }
        @Override public String toString() { return "AssistantSettings[credentials=REDACTED]"; }
    }
}
