package vn.edu.hcmute.uteexpress.service.assistant;

import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import vn.edu.hcmute.uteexpress.dto.assistant.*;

public interface AssistantChatService {
    record Submission(ChatConversationResponse conversation,int status,long retryAfterSeconds) { }
    ChatConversationResponse readConversation(Authentication authentication,HttpSession session);
    ChatConversationResponse clearConversation(Authentication authentication,HttpSession session);
    Submission sendMessage(Authentication authentication,HttpSession session,ChatMessageRequest request);
}
