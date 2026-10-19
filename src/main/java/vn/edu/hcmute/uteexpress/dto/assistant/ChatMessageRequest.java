package vn.edu.hcmute.uteexpress.dto.assistant;

import java.util.UUID;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;

public class ChatMessageRequest {
    @NotNull private UUID conversationId;
    @NotNull private UUID requestId;
    @NotBlank @Size(max=1200) private String question;
    private boolean retry;
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID value) { conversationId=value; }
    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID value) { requestId=value; }
    public String getQuestion() { return question; }
    public void setQuestion(String value) { question=value; }
    public boolean isRetry() { return retry; }
    public void setRetry(boolean value) { retry=value; }
    @JsonAnySetter public void rejectUnknown(String name,Object value) { throw new IllegalArgumentException("Tham số chatbot không được hỗ trợ."); }
}
