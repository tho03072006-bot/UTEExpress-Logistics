package vn.edu.hcmute.uteexpress.controller.api;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.edu.hcmute.uteexpress.dto.assistant.*;
import vn.edu.hcmute.uteexpress.service.assistant.AssistantChatService;

@RestController
@RequestMapping("/api/assistant")
public class AssistantChatController {
    private final AssistantChatService chat;
    public AssistantChatController(AssistantChatService chat) { this.chat=chat; }
    @GetMapping("/conversation")
    public ResponseEntity<ChatConversationResponse> read(Authentication authentication,HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.readConversation(authentication,session));
    }
    @DeleteMapping("/conversation")
    public ResponseEntity<ChatConversationResponse> clear(Authentication authentication,HttpSession session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.clearConversation(authentication,session));
    }
    @GetMapping("/orders")
    public ResponseEntity<ChatOrderPageResponse> orders(Authentication authentication,HttpSession session,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="") String query,
            @RequestParam org.springframework.util.MultiValueMap<String,String> parameters) {
        if(!java.util.Set.of("page","query").containsAll(parameters.keySet()) || parameters.values().stream().anyMatch(values->values.size()!=1))
            throw new vn.edu.hcmute.uteexpress.service.assistant.AssistantFailure("INVALID_ORDER_FILTER",400,0);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(chat.readOrderOptions(authentication,session,page,query));
    }
    @PostMapping("/messages")
    public ResponseEntity<ChatConversationResponse> send(@Valid @RequestBody ChatMessageRequest request,Authentication authentication,HttpSession session) {
        var result=chat.sendMessage(authentication,session,request);
        return ResponseEntity.status(result.status()).cacheControl(CacheControl.noStore())
                .header("Retry-After",Long.toString(result.retryAfterSeconds())).body(result.conversation());
    }
}
