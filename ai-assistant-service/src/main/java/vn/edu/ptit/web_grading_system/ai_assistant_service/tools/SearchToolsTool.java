package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiProviderException;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Model-facing discovery callback bound to one run's authorized principal. */
public class SearchToolsTool {
    private final ToolRegistry registry;
    private final ObjectMapper mapper;
    private final int resultLimit;
    private final LlmClient llm;

    public SearchToolsTool(ToolRegistry registry, ObjectMapper mapper, int resultLimit) {
        this(registry, mapper, resultLimit, null);
    }

    public SearchToolsTool(ToolRegistry registry, ObjectMapper mapper, int resultLimit, LlmClient llm) {
        this.registry = registry;
        this.mapper = mapper;
        this.resultLimit = resultLimit;
        this.llm = llm;
    }

    public ToolCallback callback(AgentContext context, Set<String> discoveredNames) {
        return new ToolCallback() {
            @Override public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return org.springframework.ai.tool.metadata.ToolMetadata.builder().returnDirect(true).build();
            }
            @Override public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder()
                        .name("search_tools")
                        .description("Find relevant tools for the current task. Query briefly in English; include recent user intent. Can be called again after a tool result.")
                        .inputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}")
                        .build();
            }
            @Override public String call(String toolInput) { return call(toolInput, null); }
            @Override public String call(String toolInput, ToolContext toolContext) {
                try {
                    JsonNode args = mapper.readTree(toolInput);
                    String query = args.path("query").asText("");
                    List<ToolDefinition> found;
                    if (llm == null) {
                        found = registry.searchAuthorized(query, context.roles(), resultLimit);
                    } else {
                        List<AgentTool> authorized = registry.getToolsForRoles(context.roles());
                        List<String> names = llm.chooseRelevantTools(query, authorized);
                        if (names == null) {
                            found = registry.searchAuthorized(query, context.roles(), resultLimit);
                        } else {
                            Set<String> chosen = Set.copyOf(names);
                            found = authorized.stream().filter(tool -> chosen.contains(tool.getName()))
                                    .map(AgentTool::getDefinition).toList();
                        }
                    }
                    found.forEach(definition -> discoveredNames.add(definition.name()));
                    return mapper.writeValueAsString(found.stream().map(definition -> {
                        AgentTool tool = registry.find(definition.name()).orElseThrow();
                        String description = definition.description() == null ? "" : definition.description().replaceAll("\\s+", " ");
                        if (description.length() > 120) description = description.substring(0, 120);
                        return java.util.Map.of("name", definition.name(), "description", description,
                                "parameters", briefParameters(tool.getToolDefinition().inputSchema()),
                                "effectType", definition.effectType().name(),
                                "confirmationRequired", definition.effectType() == EffectType.DOMAIN_WRITE);
                    }).toList());
                } catch (AiProviderException e) {
                    throw e;
                } catch (Exception e) {
                    return "{\"error\":\"INVALID_SEARCH_QUERY\"}";
                }
            }
        };
    }

    private String briefParameters(String schema) {
        try {
            JsonNode root = mapper.readTree(schema);
            JsonNode properties = root.path("properties");
            Set<String> required = new java.util.HashSet<>();
            root.path("required").forEach(item -> required.add(item.asText()));
            List<String> fields = new ArrayList<>();
            properties.fields().forEachRemaining(entry -> fields.add(entry.getKey() + ":"
                    + entry.getValue().path("type").asText("value")
                    + (required.contains(entry.getKey()) ? " required" : " optional")));
            return fields.isEmpty() ? "none" : fields.stream().collect(Collectors.joining(", "));
        } catch (Exception ignored) {
            return "see tool schema";
        }
    }
}
