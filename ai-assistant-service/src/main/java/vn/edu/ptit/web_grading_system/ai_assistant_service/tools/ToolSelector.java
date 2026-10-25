package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Deterministic, bounded first-turn selection. Discovery remains available for misses. */
@Component
public class ToolSelector {
    private static final Set<String> STOP = Set.of("cua", "toi", "minh", "cho", "trong", "va", "la", "nhung", "cac", "mot", "duoc");
    private final ToolRegistry registry;

    public ToolSelector(ToolRegistry registry) {
        this.registry = registry;
    }

    public List<String> selectInitial(String userText, Set<String> roles, int limit) {
        if (limit <= 0) return List.of();
        Set<String> terms = terms(userText);
        if (terms.isEmpty()) return List.of();
        return registry.getToolsForRoles(roles).stream()
                .map(tool -> new Scored(tool.getName(), score(terms, tool)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed().thenComparing(Scored::name))
                .limit(limit).map(Scored::name).toList();
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

    private record Scored(String name, int score) {}
}
