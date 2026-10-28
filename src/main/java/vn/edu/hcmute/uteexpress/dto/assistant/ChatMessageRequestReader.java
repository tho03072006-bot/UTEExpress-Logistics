package vn.edu.hcmute.uteexpress.dto.assistant;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/** Kiểm tra kiểu JSON riêng endpoint chat, không thay ObjectMapper của các thành viên khác. */
public final class ChatMessageRequestReader extends StdDeserializer<ChatMessageRequest> {
    private static final Set<String> FIELDS=Set.of("conversationId","requestId","question","retry","selectedOrderCode");
    public ChatMessageRequestReader() { super(ChatMessageRequest.class); }
    @Override public ChatMessageRequest deserialize(JsonParser parser,DeserializationContext context) throws IOException {
        parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        JsonNode value=parser.readValueAsTree();
        if (value==null || !value.isObject()) throw invalid(parser);
        var names=value.fieldNames(); while(names.hasNext()) if(!FIELDS.contains(names.next())) throw invalid(parser);
        if (!value.path("question").isTextual() || (value.has("retry") && !value.path("retry").isBoolean())
                || (value.hasNonNull("selectedOrderCode") && !value.path("selectedOrderCode").isTextual())) throw invalid(parser);
        var result=new ChatMessageRequest();
        result.setConversationId(uuid(value.path("conversationId"),parser)); result.setRequestId(uuid(value.path("requestId"),parser));
        result.setQuestion(value.path("question").textValue()); result.setRetry(value.path("retry").asBoolean(false));
        result.setSelectedOrderCode(value.hasNonNull("selectedOrderCode") ? value.path("selectedOrderCode").textValue() : null);
        return result;
    }
    private UUID uuid(JsonNode value,JsonParser parser) throws JsonMappingException {
        if(!value.isTextual() || !value.textValue().matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")) throw invalid(parser);
        try { return UUID.fromString(value.textValue()); } catch (IllegalArgumentException error) { throw invalid(parser); }
    }
    private JsonMappingException invalid(JsonParser parser) { return JsonMappingException.from(parser,"Dữ liệu yêu cầu chatbot không hợp lệ."); }
}
