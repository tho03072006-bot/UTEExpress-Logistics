package vn.edu.hcmute.uteexpress.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.springframework.security.core.Authentication;
import java.util.List;
import vn.edu.hcmute.uteexpress.dto.assistant.ChatConversationResponse.LinkResponse;
import vn.edu.hcmute.uteexpress.dto.assistant.ChatOrderPageResponse.OrderOptionResponse;
import java.time.Instant;

public interface AssistantToolService {
    record ToolOutput(JsonNode data,List<LinkResponse> links,String trackingCode) { }
    record OrderPage(int page,boolean hasNext,long total,Instant checkedAt,List<OrderOptionResponse> orders) { }
    OrderPage listOrderOptions(Authentication authentication,int page,String query);
    ArrayNode definitions(AssistantAccessService.Actor actor);
    ToolOutput execute(String name,JsonNode arguments,Authentication authentication);
    String getKnowledge(AssistantAccessService.Actor actor);
}
