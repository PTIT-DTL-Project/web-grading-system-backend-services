package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.WriteApprovalService;

import java.util.*;
import java.util.stream.Collectors;
import java.util.function.BiConsumer;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName = new HashMap<>();
    private final ObjectMapper mapper;
    private final WriteApprovalService writeApprovalService;

    public ToolRegistry(List<AgentTool> tools, ObjectMapper mapper) {
        this(tools, mapper, null);
    }

    @Autowired
    public ToolRegistry(List<AgentTool> tools, ObjectMapper mapper, WriteApprovalService writeApprovalService) {
        this.mapper = mapper;
        this.writeApprovalService = writeApprovalService;
        if (tools != null) {
            for (AgentTool tool : tools) {
                toolsByName.put(tool.getName(), tool);
            }
        }
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public List<AgentTool> getToolsForRoles(Set<String> userRoles) {
        if (userRoles == null || userRoles.isEmpty()) {
            return Collections.emptyList();
        }
        return toolsByName.values().stream()
                .filter(t -> t.getDefinition().requiredRoles() != null &&
                        t.getDefinition().requiredRoles().stream().anyMatch(userRoles::contains))
                .collect(Collectors.toList());
    }

    public List<AgentTool> getAllTools() {
        return new ArrayList<>(toolsByName.values());
    }

    public List<ToolDefinition> searchAuthorized(String query, Set<String> roles, int limit) {
        if (limit <= 0 || ToolSelector.terms(query).isEmpty()) return List.of();
        Set<String> terms = ToolSelector.terms(query);
        return getToolsForRoles(roles).stream()
                .filter(tool -> ToolSelector.score(terms, tool) > 0)
                .sorted(Comparator.comparingInt((AgentTool tool) -> ToolSelector.score(terms, tool)).reversed()
                        .thenComparing(AgentTool::getName))
                .limit(limit).map(AgentTool::getDefinition).toList();
    }

    public List<org.springframework.ai.tool.ToolCallback> callbacksForNames(
            Set<String> names, AgentContext context, BiConsumer<AgentTool, ToolResult> onResult) {
        if (names == null || names.isEmpty() || context == null) return List.of();
        Set<String> allowed = getToolsForRoles(context.roles()).stream().map(AgentTool::getName).collect(Collectors.toSet());
        return names.stream().filter(allowed::contains).sorted().map(toolsByName::get)
                .map(tool -> callbackFor(tool, context, onResult)).toList();
    }

    public List<org.springframework.ai.tool.ToolCallback> getToolCallbacksForRoles(Set<String> userRoles, AgentContext context) {
        return getToolCallbacksForRoles(userRoles, context, (tool, result) -> {});
    }

    public List<org.springframework.ai.tool.ToolCallback> getToolCallbacksForRoles(
            Set<String> userRoles, AgentContext context, BiConsumer<AgentTool, ToolResult> onResult) {
        List<AgentTool> authorizedTools = getToolsForRoles(userRoles);
        List<org.springframework.ai.tool.ToolCallback> callbacks = new ArrayList<>();
        for (AgentTool tool : authorizedTools) {
            callbacks.add(callbackFor(tool, context, onResult));
        }
        return callbacks;
    }

    private org.springframework.ai.tool.ToolCallback callbackFor(
            AgentTool tool, AgentContext context, BiConsumer<AgentTool, ToolResult> onResult) {
            return new org.springframework.ai.tool.ToolCallback() {
                @Override
                public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                    return tool.getToolDefinition();
                }

                @Override
                public String call(String toolInput) {
                    return call(toolInput, null);
                }

                @Override
                public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                    try {
                        com.fasterxml.jackson.databind.JsonNode args = (toolInput != null && !toolInput.isBlank())
                                ? mapper.readTree(toolInput)
                                : mapper.createObjectNode();
                        ToolResult res = tool.getDefinition().effectType() == EffectType.DOMAIN_WRITE
                                ? (writeApprovalService == null
                                        ? ToolResult.error("APPROVAL_UNAVAILABLE", "Chưa có dịch vụ duyệt thao tác", false)
                                        : writeApprovalService.propose(tool, args, context))
                                : tool.execute(args, context);
                        if (res == null) res = ToolResult.error("EMPTY_TOOL_RESULT", "Tool không trả về kết quả", false);
                        onResult.accept(tool, res);
                        return res.isSuccess() && res.getData() != null
                                ? mapper.writeValueAsString(res.getData())
                                : mapper.writeValueAsString(res);
                    } catch (Exception e) {
                        ToolResult error = ToolResult.error("TOOL_EXECUTION_ERROR", e.getMessage(), false);
                        onResult.accept(tool, error);
                        try {
                            return mapper.writeValueAsString(error);
                        } catch (Exception serializationError) {
                            return "{\"success\":false,\"code\":\"TOOL_EXECUTION_ERROR\"}";
                        }
                    }
                }
            };
    }
}
