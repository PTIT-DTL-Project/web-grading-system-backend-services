package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ToolResultStore;

import java.util.UUID;

public class GetToolResultPageTool {
    private final ToolResultStore store;
    private final ObjectMapper mapper;
    private final int pageTokenBudget;

    public GetToolResultPageTool(ToolResultStore store, ObjectMapper mapper, int pageTokenBudget) {
        this.store = store;
        this.mapper = mapper;
        this.pageTokenBudget = pageTokenBudget;
    }

    public ToolCallback callback(AgentContext context) {
        return new ToolCallback() {
            @Override public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name("get_tool_result_page")
                        .description("Đọc phần tiếp theo của kết quả tool đã lưu nguyên vẹn; không gọi lại tool nghiệp vụ.")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"storedId\":{\"type\":\"string\"},\"cursor\":{\"type\":\"string\"}},\"required\":[\"storedId\",\"cursor\"]}")
                        .build();
            }
            @Override public String call(String input) { return call(input, null); }
            @Override public String call(String input, ToolContext ignored) {
                try {
                    JsonNode args = mapper.readTree(input);
                    UUID id = UUID.fromString(args.path("storedId").asText());
                    String cursor = args.path("cursor").asText();
                    return mapper.writeValueAsString(store.page(id, cursor, context, pageTokenBudget).delivery());
                } catch (SecurityException e) {
                    return "{\"error\":\"FORBIDDEN\"}";
                } catch (Exception e) {
                    return "{\"error\":\"INVALID_RESULT_PAGE\"}";
                }
            }
        };
    }
}
