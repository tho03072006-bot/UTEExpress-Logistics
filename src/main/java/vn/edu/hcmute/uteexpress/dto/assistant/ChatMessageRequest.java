package vn.edu.hcmute.uteexpress.dto.assistant;

import java.util.UUID;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(using=ChatMessageRequestReader.class)
public class ChatMessageRequest {
    @NotNull private UUID conversationId;
    @NotNull private UUID requestId;
    @NotBlank @Size(max=1200) private String question;
    private boolean retry;
    @Pattern(regexp="[A-Za-z0-9][A-Za-z0-9-]{2,19}") private String selectedOrderCode;
    public UUID getConversationId() { return conversationId; }
    public void setConversationId(UUID value) { conversationId=value; }
    public UUID getRequestId() { return requestId; }
    public void setRequestId(UUID value) { requestId=value; }
    public String getQuestion() { return question; }
    public void setQuestion(String value) { question=value; }
    public boolean isRetry() { return retry; }
    public void setRetry(boolean value) { retry=value; }
    public String getSelectedOrderCode() { return selectedOrderCode; }
    public void setSelectedOrderCode(String value) { selectedOrderCode=value; }
    @JsonAnySetter public void rejectUnknown(String name,Object value) { throw new IllegalArgumentException("Tham số chatbot không được hỗ trợ."); }
}
