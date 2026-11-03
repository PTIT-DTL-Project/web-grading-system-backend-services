package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Collects each run's events into one indented JSON block in the dedicated trace file. */
public final class AgentTrace {
    private static final Logger LOG = LoggerFactory.getLogger("AI_AGENT_TRACE");
    private static final ThreadLocal<AgentContext> CURRENT = new ThreadLocal<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<UUID, RunTrace> ACTIVE_RUNS = new ConcurrentHashMap<>();
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i).*(?:secret|token|password|api.?key|authorization|cookie).*");
    private static final Pattern BEARER = Pattern.compile("(?i)(\\bBearer\\s+)\\S+");
    private static final Pattern PROVIDER_KEY = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}\\b");
    private static final Pattern INLINE_SECRET = Pattern.compile(
            "(?i)((?:secret|token|password|api[_-]?key|authorization|cookie)\\s*[=:]\\s*[\\\"']?)([^\\s,}\\\"']+)");

    private AgentTrace() {}

    interface Scope extends AutoCloseable {
        @Override void close();
    }

    static Scope bind(AgentContext context) {
        AgentContext previous = CURRENT.get();
        CURRENT.set(context);
        return () -> {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        };
    }

    public static void recordCurrent(String event, Map<String, ?> details) {
        AgentContext context = CURRENT.get();
        if (context != null) record(context.runId(), context.gatewaySecret(), event, details);
    }

    public static ReasoningCapture captureReasoning() {
        return new ReasoningCapture(CURRENT.get());
    }

    public static void recordCurrentReasoning(ChatResponse response) {
        ReasoningCapture capture = captureReasoning();
        capture.accept(response);
        capture.finish("complete");
    }

    public static final class ReasoningCapture {
        private final AgentContext context;
        private final StringBuilder content = new StringBuilder();
        private final ReasoningChunkNormalizer normalizer = new ReasoningChunkNormalizer();
        private boolean finished;

        private ReasoningCapture(AgentContext context) {
            this.context = context;
        }

        public void accept(ChatResponse response) {
            if (context == null || response == null || response.getResult() == null
                    || response.getResult().getOutput() == null) return;
            Object value = response.getResult().getOutput().getMetadata().get("reasoningContent");
            if (!(value instanceof String)) value = response.getResult().getOutput().getMetadata().get("reasoning_content");
            if (!(value instanceof String)) value = response.getResult().getOutput().getMetadata().get("reasoning");
            if (value instanceof String reasoning && !reasoning.isEmpty()) {
                content.append(normalizer.accept(reasoning, true));
            }
        }

        public void finish(String termination) {
            if (context == null || finished) return;
            finished = true;
            record(context.runId(), context.gatewaySecret(), "model.reasoning", Map.of(
                    "available", !content.isEmpty(),
                    "content", content.toString(),
                    "source", "provider_metadata.reasoningContent",
                    "termination", termination));
        }
    }

    static void modelRequest(AgentContext context, int modelStep, int attempt,
                             String systemPrompt, List<Message> messages,
                             List<ToolCallback> callbacks, String toolChoice) {
        try {
            List<Map<String, String>> messagePayload = messages.stream()
                    .map(message -> Map.of(
                            "role", message.getMessageType().name(),
                            "content", message.getText() == null ? "" : message.getText()))
                    .toList();
            List<Map<String, String>> tools = callbacks.stream()
                    .map(callback -> Map.of(
                            "name", String.valueOf(callback.getToolDefinition().name()),
                            "description", String.valueOf(callback.getToolDefinition().description()),
                            "inputSchema", String.valueOf(callback.getToolDefinition().inputSchema())))
                    .toList();
            record(context.runId(), context.gatewaySecret(), "model.request", Map.of(
                    "modelStep", modelStep,
                    "attempt", attempt,
                    "systemPrompt", systemPrompt == null ? "" : systemPrompt,
                    "messages", messagePayload,
                    "tools", tools,
                    "toolChoice", toolChoice == null ? "auto" : toolChoice));
        } catch (RuntimeException error) {
            LOG.warn("Could not capture model request runId={} errorType={}",
                    context.runId(), error.getClass().getSimpleName());
        }
    }

    static void record(UUID runId, String gatewaySecret, String event, Map<String, ?> details) {
        if (!LOG.isInfoEnabled()) return;
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("time", Instant.now().toString());
        line.put("runId", runId == null ? null : runId.toString());
        line.put("event", event);
        try {
            for (var entry : details.entrySet()) {
                line.put(entry.getKey(), redact(entry.getKey(), entry.getValue(), gatewaySecret));
            }
            RunTrace runTrace = "run.started".equals(event) && runId != null
                    ? ACTIVE_RUNS.computeIfAbsent(runId, ignored -> new RunTrace())
                    : runId == null ? null : ACTIVE_RUNS.get(runId);
            if (runTrace != null) {
                synchronized (runTrace) {
                    if (ACTIVE_RUNS.get(runId) == runTrace) {
                        runTrace.add(line);
                        if ("run.completed".equals(event) || "run.failed".equals(event)
                                || "run.cancelled".equals(event)) {
                            ACTIVE_RUNS.remove(runId, runTrace);
                            LOG.info(MAPPER.writerWithDefaultPrettyPrinter()
                                    .writeValueAsString(readable(runTrace.toBlock(runId))));
                        }
                        return;
                    }
                }
            }
            LOG.info(MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(readable(line)));
        } catch (Exception error) {
            LOG.warn("Could not serialize AI trace event={} runId={} errorType={}",
                    event, runId, error.getClass().getSimpleName());
        }
    }

    private static final class RunTrace {
        private final List<Map<String, Object>> events = new ArrayList<>();
        private String conversationId = "";
        private String userPrompt = "";
        private String startedAt = "";
        private String finishedAt = "";
        private String status = "RUNNING";

        void add(Map<String, Object> entry) {
            events.add(entry);
            String event = String.valueOf(entry.get("event"));
            if ("run.started".equals(event)) {
                startedAt = String.valueOf(entry.get("time"));
                conversationId = String.valueOf(entry.getOrDefault("conversationId", ""));
            } else if ("agent.start".equals(event)) {
                userPrompt = String.valueOf(entry.getOrDefault("userPrompt", ""));
            } else if ("run.completed".equals(event) || "run.failed".equals(event)
                    || "run.cancelled".equals(event)) {
                finishedAt = String.valueOf(entry.get("time"));
                status = String.valueOf(entry.getOrDefault("status", event.substring(4).toUpperCase()));
            }
        }

        Map<String, Object> toBlock(UUID runId) {
            Map<String, Object> block = new LinkedHashMap<>();
            block.put("runId", runId.toString());
            block.put("conversationId", conversationId);
            block.put("userPrompt", userPrompt);
            block.put("startedAt", startedAt);
            block.put("finishedAt", finishedAt);
            block.put("status", status);
            block.put("eventCount", events.size());
            block.put("events", events);
            return block;
        }
    }

    private static Object readable(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((rawKey, entryValue) -> {
                String key = String.valueOf(rawKey);
                result.put(key, readable(entryValue));
                if (entryValue instanceof String text) {
                    if (text.contains("\n") || text.length() > 180) {
                        result.put(key + "Lines", displayLines(text));
                    }
                    String trimmed = text.stripLeading();
                    if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                        try {
                            Object parsed = MAPPER.readValue(text, Object.class);
                            if (parsed instanceof Map<?, ?> || parsed instanceof List<?>) {
                                result.put(key + "Json", readable(parsed));
                            }
                        } catch (JsonProcessingException ignored) {
                            // A text response can begin with a brace without being JSON.
                        }
                    }
                }
            });
            return result;
        }
        if (value instanceof List<?> list) return list.stream().map(AgentTrace::readable).toList();
        return value;
    }

    private static List<String> displayLines(String text) {
        List<String> lines = new ArrayList<>();
        for (String original : text.split("\\R", -1)) {
            String remaining = original;
            while (remaining.length() > 180) {
                int split = remaining.lastIndexOf(' ', 180);
                if (split < 80) split = 180;
                lines.add(remaining.substring(0, split));
                remaining = remaining.substring(split).stripLeading();
            }
            lines.add(remaining);
        }
        return lines;
    }

    private static Object redact(String key, Object value, String gatewaySecret) {
        if (SENSITIVE_KEY.matcher(key).matches()) return "[REDACTED]";
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> safe = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> safe.put(String.valueOf(nestedKey),
                    redact(String.valueOf(nestedKey), nestedValue, gatewaySecret)));
            return safe;
        }
        if (value instanceof Iterable<?> values) {
            List<Object> safe = new ArrayList<>();
            for (Object item : values) safe.add(redact("item", item, gatewaySecret));
            return safe;
        }
        if (value instanceof String text) {
            String safe = gatewaySecret == null || gatewaySecret.isBlank()
                    ? text : text.replace(gatewaySecret, "[REDACTED]");
            String trimmed = safe.stripLeading();
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                try {
                    Object parsed = MAPPER.readValue(safe, Object.class);
                    if (parsed instanceof Map<?, ?> || parsed instanceof List<?>) {
                        safe = MAPPER.writeValueAsString(redact("json", parsed, gatewaySecret));
                    }
                } catch (JsonProcessingException ignored) {
                    // Plain prose may begin with a brace; keep the original text.
                }
            }
            safe = BEARER.matcher(safe).replaceAll("$1[REDACTED]");
            safe = PROVIDER_KEY.matcher(safe).replaceAll("[REDACTED]");
            return INLINE_SECRET.matcher(safe).replaceAll("$1[REDACTED]");
        }
        return value;
    }
}
