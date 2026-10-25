package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Set;

/** Model-facing discovery callback bound to one run's authorized principal. */
public class SearchToolsTool {
    private final ToolRegistry registry;
    private final ObjectMapper mapper;
    private final int resultLimit;

    public SearchToolsTool(ToolRegistry registry, ObjectMapper mapper, int resultLimit) {
        this.registry = registry;
        this.mapper = mapper;
        this.resultLimit = resultLimit;
    }

    public ToolCallback callback(AgentContext context, Set<String> discoveredNames) {
        return new ToolCallback() {
            @Override public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return org.springframework.ai.tool.metadata.ToolMetadata.builder().returnDirect(true).build();
            }
            @Override public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name("search_tools")
                        .description("Tìm các tool phù hợp với bước hiện tại. Có thể gọi lại sau khi dùng tool trung gian.")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
                        .build();
            }
            @Override public String call(String toolInput) { return call(toolInput, null); }
            @Override public String call(String toolInput, ToolContext toolContext) {
                try {
                    JsonNode args = mapper.readTree(toolInput);
                    String query = args.path("query").asText("");
                    List<ToolDefinition> found = registry.searchAuthorized(query, context.roles(), resultLimit);
                    found.forEach(definition -> discoveredNames.add(definition.name()));
                    return mapper.writeValueAsString(found.stream().map(definition -> java.util.Map.of(
                            "name", definition.name(), "description", definition.description(),
                            "effectType", definition.effectType().name(),
                            "confirmationRequired", definition.effectType() == EffectType.DOMAIN_WRITE)).toList());
                } catch (Exception e) {
                    return "{\"error\":\"INVALID_SEARCH_QUERY\"}";
                }
            }
        };
    }
}
