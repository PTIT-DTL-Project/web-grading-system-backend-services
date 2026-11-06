package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunEvent;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunEventRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class RunTimelineService {
    private static final Pattern SECRET_KEY = Pattern.compile("(?i).*(secret|token|password|api.?key|authorization|cookie).*");
    private static final Pattern BEARER = Pattern.compile("(?i)(Bearer\\s+)\\S+");
    private static final Pattern INLINE_SECRET = Pattern.compile("(?i)((?:password|secret|token|api[_-]?key)\\s*[=:]\\s*)[^\\s,}]+");
    private static final Pattern PROVIDER_KEY = Pattern.compile("\\b(?:gsk_|sk-)[A-Za-z0-9_-]{8,}\\b");
    private final JdbcTemplate jdbc;
    private final RunEventRepository repository;
    private final SseBroadcaster broadcaster;
    private final ObjectMapper mapper;

    public RunTimelineService(JdbcTemplate jdbc, RunEventRepository repository,
                              SseBroadcaster broadcaster, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.repository = repository;
        this.broadcaster = broadcaster;
        this.mapper = mapper;
    }

    @Transactional
    public RunTimelineEvent append(UUID runId, String type, Integer modelTurn, String itemId, JsonNode data) {
        Integer sequence = jdbc.queryForObject(
                "UPDATE ai_run SET next_event_sequence = next_event_sequence + 1 WHERE id = ? RETURNING next_event_sequence - 1",
                Integer.class, runId);
        if (sequence == null) throw new IllegalStateException("Run missing while appending timeline event");
        RunTimelineEvent event = new RunTimelineEvent(1, runId, sequence, type, modelTurn,
                itemId, Instant.now(), redact(data));
        try {
            repository.save(RunEvent.builder().runId(runId).sequence(sequence).type(type)
                    .payloadJson(mapper.writeValueAsString(event)).build());
        } catch (Exception e) {
            throw new IllegalStateException("Cannot persist run timeline event", e);
        }
        Runnable publish = () -> broadcaster.sendTimelineEvent(runId, event);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { publish.run(); }
            });
        } else publish.run();
        return event;
    }

    public List<RunTimelineEvent> listAfter(UUID runId, int afterSequence) {
        return repository.findByRunIdAndSequenceGreaterThanOrderBySequenceAsc(runId, afterSequence).stream()
                .map(row -> {
                    try {
                        JsonNode json = mapper.readTree(row.getPayloadJson());
                        if (json.path("version").asInt() != 1) return null;
                        return mapper.treeToValue(json, RunTimelineEvent.class);
                    } catch (Exception ignored) { return null; }
                }).filter(java.util.Objects::nonNull).toList();
    }

    private JsonNode redact(JsonNode node) {
        if (node == null) return mapper.createObjectNode();
        if (node.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            node.properties().forEach(entry -> result.set(entry.getKey(), SECRET_KEY.matcher(entry.getKey()).matches()
                    ? mapper.getNodeFactory().textNode("[REDACTED]") : redact(entry.getValue())));
            return result;
        }
        if (node.isArray()) {
            var result = mapper.createArrayNode();
            node.forEach(value -> result.add(redact(value)));
            return result;
        }
        if (node.isTextual()) {
            String text = BEARER.matcher(node.asText()).replaceAll("$1[REDACTED]");
            text = INLINE_SECRET.matcher(text).replaceAll("$1[REDACTED]");
            text = PROVIDER_KEY.matcher(text).replaceAll("[REDACTED]");
            return mapper.getNodeFactory().textNode(text);
        }
        return node.deepCopy();
    }
}
