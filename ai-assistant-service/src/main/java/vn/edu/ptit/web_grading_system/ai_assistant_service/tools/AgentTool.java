package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.Set;
import java.util.UUID;

public interface AgentTool extends ToolCallback {

    ObjectMapper TOOL_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    String getName();

    String getDescription();

    ToolDefinition getDefinition();

    ToolResult execute(JsonNode arguments, AgentContext context);

    @Override
    default org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
        return org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name(getName())
                .description(getDescription())
                .inputSchema("{}")
                .build();
    }

    @Override
    default String call(String toolInput) {
        return call(toolInput, null);
    }

    @Override
    default String call(String toolInput, ToolContext toolContext) {
        UUID uid = null;
        if (toolContext != null && toolContext.getContext().get("userId") != null) {
            try {
                uid = UUID.fromString(toolContext.getContext().get("userId").toString());
            } catch (Exception ignored) {
            }
        }
        String role = toolContext != null && toolContext.getContext().get("role") != null
                ? toolContext.getContext().get("role").toString() : "ROLE_USER";
        AgentContext context = new AgentContext(uid, "", Set.of(role), null, null, null);
        try {
            JsonNode args = toolInput != null && !toolInput.isBlank() ? TOOL_MAPPER.readTree(toolInput) : TOOL_MAPPER.createObjectNode();
            ToolResult res = execute(args, context);
            return res != null && res.getData() != null ? TOOL_MAPPER.writeValueAsString(res.getData()) : "";
        } catch (Exception e) {
            return "{\"error\":\"" + e.getMessage() + "\"}";
        }
    }
}
