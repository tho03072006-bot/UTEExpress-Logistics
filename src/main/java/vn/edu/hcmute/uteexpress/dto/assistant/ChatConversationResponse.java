package vn.edu.hcmute.uteexpress.dto.assistant;

import java.time.Instant;
import java.util.*;

public record ChatConversationResponse(UUID conversationId,String role,boolean configured,List<String> suggestions,List<TurnResponse> turns,
        String selectedOrderCode) {
    public record LinkResponse(String label,String path) { }
    public record ErrorResponse(String code,String message,long retryAfterSeconds) { }
    public record TurnResponse(UUID requestId,String question,String answer,String status,Instant checkedAt,
            List<LinkResponse> links,ErrorResponse error,String selectedOrderCode) { }
}
