package vn.edu.hcmute.uteexpress.service.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

/** Chỉ kết nối AI; không có repository, SQL, shell, filesystem hoặc quyền nghiệp vụ. */
public interface AssistantAiGateway {
    JsonNode complete(ArrayNode messages,ArrayNode tools,String toolChoice,int timeoutSeconds);
}
