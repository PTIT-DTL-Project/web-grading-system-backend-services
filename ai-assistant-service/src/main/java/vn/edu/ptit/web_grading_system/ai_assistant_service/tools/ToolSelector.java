package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;

/** Selects role-scoped tools proposed by the model. */
@Component
public class ToolSelector {
    private static final Set<String> STOP = Set.of("cua", "toi", "minh", "cho", "trong", "va", "la", "nhung", "cac", "mot", "duoc");
    private final ToolRegistry registry;

    public ToolSelector(ToolRegistry registry) {
        this.registry = registry;
    }

    public List<String> selectFromModel(String request, Set<String> roles, LlmClient llm) {
        List<AgentTool> authorized = registry.getToolsForRoles(roles);
        if (authorized.isEmpty()) return List.of();
        List<String> proposed = llm.chooseRelevantTools(request, authorized);
        if (proposed == null) return List.of();
        Set<String> allowed = authorized.stream().map(AgentTool::getName).collect(Collectors.toSet());
        return proposed.stream().filter(allowed::contains).distinct().toList();
    }

    static int score(Set<String> query, AgentTool tool) {
        Set<String> metadata = terms(tool.getName() + " " + tool.getDescription() + " " + tool.getDefinition().capabilityGroup());
        int result = 0;
        for (String term : query) if (metadata.contains(term)) result++;
        return result;
    }

    static Set<String> terms(String input) {
        if (input == null || input.isBlank()) return Set.of();
        String normalized = Normalizer.normalize(input.replaceAll("([a-z])([A-Z])", "$1 $2")
                        .toLowerCase().replace('đ', 'd'), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(term -> term.length() > 1 && !STOP.contains(term))
                .collect(Collectors.toSet());
    }

}
