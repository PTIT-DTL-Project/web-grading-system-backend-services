package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName = new HashMap<>();

    public ToolRegistry(List<AgentTool> tools) {
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
}
