package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/** One JSON line per agent event, routed to the dedicated rolling trace file. */
public final class AgentTrace {
    private static final Logger LOG = LoggerFactory.getLogger("AI_AGENT_TRACE");
    private static final ThreadLocal<AgentContext> CURRENT = new ThreadLocal<>();
    private static final ObjectMapper MAPPER = new ObjectMapper();
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
            LOG.info(MAPPER.writeValueAsString(line));
        } catch (Exception error) {
            LOG.warn("Could not serialize AI trace event={} runId={} errorType={}",
                    event, runId, error.getClass().getSimpleName());
        }
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
