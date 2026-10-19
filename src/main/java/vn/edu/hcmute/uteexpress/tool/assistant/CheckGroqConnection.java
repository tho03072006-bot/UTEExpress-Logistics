package vn.edu.hcmute.uteexpress.tool.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Properties;
import vn.edu.hcmute.uteexpress.config.AssistantConfig;
import vn.edu.hcmute.uteexpress.service.assistant.AssistantFailure;
import vn.edu.hcmute.uteexpress.service.assistant.impl.GroqAssistantAiGatewayImpl;

/** Chỉ thử hội thoại và tool calling bằng dữ liệu mẫu; không khởi động Spring hoặc kết nối Aiven. */
public final class CheckGroqConnection {
    private CheckGroqConnection() { }
    public static void main(String[] args) {
        if (args.length!=0) { System.err.println("Không truyền key hoặc dữ liệu riêng tư qua tham số CLI."); System.exit(1); }
        try {
            var properties=new Properties();
            var config=Path.of("config/groq-local.properties");
            if (Files.exists(config)) try (var reader=Files.newBufferedReader(config,StandardCharsets.UTF_8)) { properties.load(reader); }
            var settings=new AssistantConfig.Settings(Boolean.parseBoolean(value(properties,"ASSISTANT_ENABLED","app.assistant.enabled","true")),
                    value(properties,"GROQ_API_KEY","app.assistant.api-key",""),
                    value(properties,"GROQ_MODEL","app.assistant.model","openai/gpt-oss-20b"),18,1600,3);
            properties.clear();
            var mapper=new ObjectMapper(); var gateway=new GroqAssistantAiGatewayImpl(settings,mapper);
            var messages=mapper.createArrayNode(); messages.addObject().put("role","user").put("content","Call get_demo_health once. Then reply OK based on the tool result. Do not call any other tool.");
            var tools=(com.fasterxml.jackson.databind.node.ArrayNode)mapper.readTree("""
                [{"type":"function","function":{"name":"get_demo_health","description":"Read the demo health flag.",
                "parameters":{"type":"object","properties":{},"required":[],"additionalProperties":false}}}]
                """);
            var first=gateway.complete(messages,tools,"required",18);
            var calls=first.path("tool_calls");
            if (calls.size()!=1 || !calls.path(0).path("function").path("name").asText().equals("get_demo_health")) throw new IllegalStateException();
            messages.addObject().put("role","assistant").set("tool_calls",calls);
            messages.addObject().put("role","tool").put("tool_call_id",calls.path(0).path("id").asText()).put("content","{\"ok\":true}");
            var answer=gateway.complete(messages,tools,"none",18);
            if (answer.path("content").asText("").isBlank()) throw new IllegalStateException();
            System.out.println("Groq: xác thực API, gọi tool và nhận trả lời thành công. Không gửi dữ liệu nghiệp vụ, không in key.");
        } catch (Exception ex) {
            System.err.println("Groq check chưa thành công: "+(ex instanceof AssistantFailure failure ? failure.getCode() : "CONFIG_OR_PROTOCOL_ERROR")+". Chi tiết/key được ẩn.");
            System.exit(1);
        }
    }
    private static String value(Properties properties,String env,String property,String fallback) {
        String external=System.getenv(env); return (external==null ? properties.getProperty(property,fallback) : external).strip();
    }
}
