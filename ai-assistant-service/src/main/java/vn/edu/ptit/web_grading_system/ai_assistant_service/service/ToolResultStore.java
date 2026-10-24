package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.StoredToolResult;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.TokenEstimator;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.StoredToolResultRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ToolResultStore {
    private final StoredToolResultRepository repository;
    private final TokenEstimator estimator;
    private final AgentProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolResultStore(StoredToolResultRepository repository, TokenEstimator estimator, AgentProperties properties) {
        this.repository = repository;
        this.estimator = estimator;
        this.properties = properties;
    }

    public record ToolResultDelivery(String content, boolean complete, UUID storedId,
                                     String nextCursor, String kind, int itemIndex) {}
    public record ToolResultPage(ToolResultDelivery delivery) {}

    @Transactional
    public ToolResultDelivery offer(String fullResult, AgentContext context, int availableToolTokens) {
        if (fullResult == null) fullResult = "";
        if (estimator.estimateTokens(fullResult) <= availableToolTokens) {
            return new ToolResultDelivery(fullResult, true, null, null, "full", 0);
        }
        StoredToolResult saved = saveFull(fullResult, context);
        return slice(saved, "i:0", availableToolTokens);
    }

    @Transactional
    public UUID reference(String fullResult, AgentContext context) {
        return saveFull(fullResult, context).getId();
    }

    private StoredToolResult saveFull(String fullResult, AgentContext context) {
        requireContext(context);
        return repository.save(StoredToolResult.builder().id(UUID.randomUUID())
                .runId(context.runId()).conversationId(context.conversationId()).ownerId(context.userId())
                .roleScope(roleScope(context.roles())).fullResult(fullResult)
                .expiresAt(Instant.now().plus(properties.getContext().getStoredToolResultTtlMinutes(), ChronoUnit.MINUTES))
                .build());
    }

    @Transactional(readOnly = true)
    public ToolResultPage page(UUID storedId, String cursor, AgentContext context, int availableToolTokens) {
        requireContext(context);
        StoredToolResult saved = repository.findById(storedId).orElseThrow(SecurityException::new);
        if (saved.getExpiresAt().isBefore(Instant.now()) || !saved.getRunId().equals(context.runId())
                || !saved.getConversationId().equals(context.conversationId())
                || !saved.getOwnerId().equals(context.userId())
                || !saved.getRoleScope().equals(roleScope(context.roles()))) throw new SecurityException("Stored result unavailable");
        return new ToolResultPage(slice(saved, cursor, availableToolTokens));
    }

    @Transactional
    @Scheduled(fixedDelayString = "PT10M")
    public void deleteExpired() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }

    private ToolResultDelivery slice(StoredToolResult saved, String cursor, int tokenBudget) {
        int reserveTokens = Math.min(800, Math.max(20, tokenBudget / 3));
        int maxChars = Math.max(1, (tokenBudget - reserveTokens) * 3);
        while (true) {
            ToolResultDelivery delivery = sliceAtSize(saved, cursor, maxChars);
            try {
                if (estimator.estimateTokens(mapper.writeValueAsString(delivery)) <= tokenBudget) {
                    return delivery;
                }
            } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                throw new IllegalStateException("Cannot encode tool result page", exception);
            }
            if (maxChars == 1) throw new IllegalArgumentException("Tool page budget too small");
            maxChars = Math.max(1, maxChars * 3 / 4);
        }
    }

    private ToolResultDelivery sliceAtSize(StoredToolResult saved, String cursor, int maxChars) {
        String full = saved.getFullResult();
        if (cursor != null && cursor.startsWith("t:")) {
            return chunk(saved.getId(), full, Integer.parseInt(cursor.substring(2)), maxChars, "t:", null, 0);
        }
        try {
            JsonNode root = mapper.readTree(full);
            if (root.isArray()) {
                int index = cursor != null && cursor.startsWith("i:") ? Integer.parseInt(cursor.substring(2)) : 0;
                if (cursor != null && cursor.startsWith("c:")) {
                    String[] parts = cursor.split(":");
                    return chunk(saved.getId(), root.get(Integer.parseInt(parts[1])).toString(),
                            Integer.parseInt(parts[2]), maxChars, "c:" + parts[1] + ":", "i:" + (Integer.parseInt(parts[1]) + 1),
                            Integer.parseInt(parts[1]));
                }
                if (index < 0 || index > root.size()) throw new IllegalArgumentException("Invalid cursor");
                List<String> elements = new ArrayList<>();
                int size = 2;
                int next = index;
                while (next < root.size()) {
                    String element = root.get(next).toString();
                    int cost = element.length() + (elements.isEmpty() ? 0 : 1);
                    if (size + cost > maxChars) break;
                    elements.add(element);
                    size += cost;
                    next++;
                }
                if (elements.isEmpty() && next < root.size()) {
                    // One element exceeds a page: stream the original JSON verbatim,
                    // including array brackets, so it can be reconstructed exactly.
                    return chunk(saved.getId(), full, 0, maxChars, "t:", null, 0);
                }
                String content = "[" + String.join(",", elements) + "]";
                return new ToolResultDelivery(content, next == root.size(), saved.getId(),
                        next == root.size() ? null : "i:" + next, "array", index);
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException ignored) {
            // Non-JSON tool results are exact text chunks.
        }
        int offset = cursor != null && cursor.startsWith("t:") ? Integer.parseInt(cursor.substring(2)) : 0;
        return chunk(saved.getId(), full, offset, maxChars, "t:", null, 0);
    }

    private ToolResultDelivery chunk(UUID id, String value, int codePointOffset, int maxChars,
                                     String cursorPrefix, String afterCursor, int itemIndex) {
        int[] points = value.codePoints().toArray();
        if (codePointOffset < 0 || codePointOffset > points.length) throw new IllegalArgumentException("Invalid cursor");
        int take = Math.min(Math.max(1, maxChars / 2), points.length - codePointOffset);
        String content = new String(points, codePointOffset, take);
        int next = codePointOffset + take;
        String cursor = next < points.length ? cursorPrefix + next : afterCursor;
        return new ToolResultDelivery(content, cursor == null, id, cursor,
                afterCursor == null ? "text_chunk" : "element_chunk", itemIndex);
    }

    private void requireContext(AgentContext context) {
        if (context == null || context.runId() == null || context.conversationId() == null
                || context.userId() == null || context.roles() == null || context.roles().isEmpty()) {
            throw new SecurityException("Missing result owner");
        }
    }

    private String roleScope(Set<String> roles) { return roles.stream().sorted().collect(Collectors.joining(",")); }
}
