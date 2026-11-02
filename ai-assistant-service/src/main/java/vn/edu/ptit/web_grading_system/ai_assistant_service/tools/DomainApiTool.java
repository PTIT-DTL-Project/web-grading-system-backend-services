package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

/** A narrowly configured, caller-scoped wrapper around one public domain API. */
public class DomainApiTool implements AgentTool {
    private final String name, description, role, method, path;
    private final String[] pathKeys, queryKeys;
    private final boolean bodyRequired;
    private final String lookupId;
    private final RestClient client;
    private final ObjectMapper mapper;

    public DomainApiTool(String name, String description, String role, String method, String path,
                         String[] pathKeys, String[] queryKeys, boolean bodyRequired, String lookupId,
                         RestClient client, ObjectMapper mapper) {
        this.name = name;
        this.description = description;
        this.role = role;
        this.method = method;
        this.path = path;
        this.pathKeys = pathKeys;
        this.queryKeys = queryKeys;
        this.bodyRequired = bodyRequired;
        this.lookupId = lookupId;
        this.client = client;
        this.mapper = mapper;
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }

    @Override
    public vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolDefinition getDefinition() {
        return new vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolDefinition(
                name, description, "1.0", role.equals("ROLE_STUDENT") ? "student" : "lecturer",
                method.equals("GET") ? "read" : "write", "domain",
                method.equals("GET") ? EffectType.READ : EffectType.DOMAIN_WRITE,
                Set.of(role), Duration.ofSeconds(10), 12000);
    }

    @Override
    public ToolDefinition getToolDefinition() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (String key : pathKeys) {
            properties.put(key, Map.of("type", "string", "description", key.equals("studentCode") ? "Mã sinh viên" : "UUID"));
            required.add(key);
        }
        for (String key : queryKeys) properties.put(key, Map.of("type", key.equals("page") || key.equals("size") ? "integer" : key.equals("includeSteps") ? "boolean" : "string"));
        if (bodyRequired) {
            properties.put("payload", payloadSchema());
            required.add("payload");
        }
        try {
            String schema = mapper.writeValueAsString(Map.of("type", "object", "properties", properties,
                    "required", required, "additionalProperties", false));
            return ToolDefinition.builder().name(name).description(description).inputSchema(schema).build();
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    @Override
    public ToolResult execute(JsonNode args, AgentContext context) {
        if (context.userId() == null || context.roles() == null || !context.roles().contains(role)) {
            return ToolResult.error("FORBIDDEN", "Không có quyền gọi " + name, false);
        }
        ToolResult invalid = validateArguments(args);
        if (invalid != null) return invalid;
        Map<String, Object> variables = new HashMap<>();
        for (String key : pathKeys) {
            String value = args.path(key).asText();
            if (!key.equals("studentCode")) value = UUID.fromString(value).toString();
            variables.put(key, value);
        }
        try {
            RestClient.RequestBodySpec request = client.method(HttpMethod.valueOf(method))
                    .uri(builder -> {
                        builder.path(path);
                        for (String key : queryKeys) if (args.hasNonNull(key)) builder.queryParam(key, args.path(key).asText());
                        return builder.build(variables);
                    })
                    .header("X-User-Id", context.userId().toString())
                    .header("X-User-Roles", role)
                    .header("X-User-Email", context.email() == null ? "" : context.email())
                    .header("X-Gateway-Secret", context.gatewaySecret() == null ? "" : context.gatewaySecret());
            if (bodyRequired) request.contentType(MediaType.APPLICATION_JSON).body(mapper.writeValueAsString(args.path("payload")));
            String raw = request.retrieve().body(String.class);
            JsonNode result = raw == null || raw.isBlank() ? mapper.createObjectNode().put("completed", true) : mapper.readTree(raw);
            if (result.has("data")) result = result.path("data");
            if (lookupId != null) {
                JsonNode selected = null;
                if (result.isArray()) for (JsonNode item : result) {
                    if (item.path("id").asText().equals(args.path(lookupId).asText())) { selected = item; break; }
                }
                if (selected == null) return ToolResult.error("NOT_FOUND", "Không tìm thấy " + lookupId, false);
                result = selected;
            }
            return ToolResult.ok(result);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ToolResult.error("DOMAIN_REJECTED", rejectionMessage(e), false);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            return method.equals("GET")
                    ? ToolResult.error("DOMAIN_API_UNAVAILABLE", "Không kết nối được API để tra cứu", true)
                    : ToolResult.error("UNKNOWN_OUTCOME", "Mất kết nối; chưa xác định API đã ghi dữ liệu hay chưa", false);
        } catch (Exception e) {
            return ToolResult.error("DOMAIN_API_ERROR", "Không xác nhận được kết quả từ API: " + e.getMessage(), false);
        }
    }

    private String rejectionMessage(org.springframework.web.client.HttpClientErrorException e) {
        String message = "API từ chối (" + e.getStatusCode() + ")";
        try {
            JsonNode body = mapper.readTree(e.getResponseBodyAsString());
            String reason = body.path("message").asText("").trim();
            if (reason.isBlank()) reason = body.path("error").asText("").trim();
            if (!reason.isBlank() && !reason.equals("null")) {
                reason = reason.replaceAll("[\\p{Cntrl}]+", " ");
                return message + ": " + reason.substring(0, Math.min(reason.length(), 400));
            }
        } catch (Exception ignored) {
            // A non-JSON response should not be copied into a user-facing chat message.
        }
        return message;
    }

    public ToolResult validateArguments(JsonNode args) {
        if (args == null || !args.isObject()) return ToolResult.error("INVALID_ARGUMENTS", "Cần JSON object", false);
        for (String key : pathKeys) {
            if (!args.hasNonNull(key) || args.path(key).asText().isBlank())
                return ToolResult.error("INVALID_ARGUMENTS", "Thiếu " + key, false);
            if (!key.equals("studentCode")) {
                try { UUID.fromString(args.path(key).asText()); }
                catch (IllegalArgumentException e) { return ToolResult.error("INVALID_UUID", key + " không hợp lệ", false); }
            }
        }
        if (bodyRequired && (!args.hasNonNull("payload") ||
                ((name.equals("setScoreComponents") || name.equals("setStudentScores"))
                        ? !args.path("payload").isArray() : !args.path("payload").isObject()))) {
            return ToolResult.error("INVALID_ARGUMENTS", "payload không đúng cấu trúc JSON", false);
        }
        if (name.equals("createTestQuestion") || name.equals("createTestStep") || name.equals("updateTestStep")) {
            return validateStepPayloads(args);
        }
        return null;
    }

    /**
     * Calls course-service {@code POST /api/v1/internal/test-plans/validate} or
     * {@code /validate-step} so a malformed step config is rejected *before*
     * {@link WriteApprovalService#propose} persists a pending action.
     */
    private ToolResult validateStepPayloads(JsonNode args) {
        JsonNode payload = args.path("payload");
        if (payload == null || !payload.isObject()) {
            return ToolResult.error("INVALID_ARGUMENTS", "payload không phải JSON object", false);
        }
        try {
            if (name.equals("createTestQuestion")) {
                ObjectNode validateBody = (ObjectNode) payload;
                String raw = client.method(HttpMethod.POST)
                        .uri("/api/v1/internal/test-plans/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(validateBody))
                        .retrieve().body(String.class);
                JsonNode result = raw == null || raw.isBlank()
                        ? mapper.createObjectNode().put("valid", true)
                        : mapper.readTree(raw);
                boolean valid = result.path("valid").asBoolean(false);
                if (valid) return null;
                return validationError(result);
            } else if (name.equals("createTestStep")) {
                ObjectNode validateBody = mapper.createObjectNode();
                ArrayNode steps = mapper.createArrayNode();
                steps.add(payload);
                validateBody.set("steps", steps);
                String raw = client.method(HttpMethod.POST)
                        .uri("/api/v1/internal/test-plans/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(validateBody))
                        .retrieve().body(String.class);
                JsonNode result = raw == null || raw.isBlank()
                        ? mapper.createObjectNode().put("valid", true)
                        : mapper.readTree(raw);
                boolean valid = result.path("valid").asBoolean(false);
                if (valid) return null;
                return validationError(result);
            } else if (name.equals("updateTestStep")) {
                if (!payload.hasNonNull("config")) {
                    return null;
                }
                if (!payload.hasNonNull("stepType")) {
                    return ToolResult.error("INVALID_ARGUMENTS", "payload.stepType bắt buộc khi cập nhật config", false);
                }
                ObjectNode validateBody = mapper.createObjectNode();
                validateBody.put("stepType", payload.path("stepType").asText());
                validateBody.set("config", payload.path("config"));
                String raw = client.method(HttpMethod.POST)
                        .uri("/api/v1/internal/test-plans/validate-step")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(mapper.writeValueAsString(validateBody))
                        .retrieve().body(String.class);
                JsonNode result = raw == null || raw.isBlank()
                        ? mapper.createObjectNode().put("valid", true)
                        : mapper.readTree(raw);
                boolean valid = result.path("valid").asBoolean(false);
                if (valid) return null;
                return validationError(result);
            }
            return null;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            return ToolResult.error("VALIDATION_REJECTED",
                    "course-service từ chối yêu cầu validate (" + e.getStatusCode() + ")", false);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            if (name.equals("createTestQuestion")) {
                return fallbackValidateStepConfig(payload);
            }
            if (name.equals("createTestStep")) {
                return fallbackValidateStepConfig(payload);
            }
            if (name.equals("updateTestStep")) {
                if (payload.hasNonNull("config")) {
                    return ToolResult.error("VALIDATION_REJECTED",
                            "Không thể xác minh config khi course-service không khả dụng", false);
                }
                return null;
            }
            return fallbackValidateStepConfig(payload);
        } catch (Exception e) {
            return ToolResult.error("VALIDATION_ERROR", "Không thể validate config: " + e.getMessage(), false);
        }
    }

    private ToolResult validationError(JsonNode result) {
        JsonNode errors = result.path("errors");
        List<String> messages = new ArrayList<>();
        if (errors.isArray()) {
            for (JsonNode err : errors) {
                String path = err.path("path").asText("");
                String message = err.path("message").asText("");
                if (!path.isBlank()) messages.add(path + ": " + message);
                else if (!message.isBlank()) messages.add(message);
            }
        }
        String detail = messages.isEmpty()
                ? "course-service từ chối config (không có chi tiết lỗi)"
                : String.join("; ", messages.subList(0, Math.min(messages.size(), 5)));
        return ToolResult.error("INVALID_ARGUMENTS", "Config không hợp lệ: " + detail, false);
    }

    /** Fallback kiểm tra cơ bản khi course-service không phản hồi validate endpoint. */
    private ToolResult fallbackValidateStepConfig(JsonNode payload) {
        if (name.equals("createTestQuestion")) {
            JsonNode steps = payload.path("steps");
            if (steps.isArray()) {
                for (int i = 0; i < steps.size(); i++) {
                    ToolResult invalid = checkStepConfig(steps.get(i), "payload.steps[" + i + "]");
                    if (invalid != null) return invalid;
                }
            }
        } else {
            return checkStepConfig(payload, "payload");
        }
        return null;
    }

    private ToolResult checkStepConfig(JsonNode step, String location) {
        String stepType = step.path("stepType").asText();
        JsonNode config = step.path("config");
        String missing = switch (stepType) {
            case "HTTP_REQUEST" -> config.path("method").asText().isBlank() ? "method"
                    : config.path("path").asText().isBlank() ? "path" : null;
            case "DB_QUERY" -> config.path("query").asText().isBlank() ? "query" : null;
            default -> null;
        };
        return missing == null ? null : ToolResult.error("INVALID_ARGUMENTS",
                location + ".config thiếu '" + missing + "' cho " + stepType, false);
    }

    /**
     * Human-readable preview of a write payload, used by AgentRuntime.formatPendingQuestion
     * to render a numbered step table instead of raw JSON.
     */
    @Override
    public String summarize(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) return null;
        JsonNode payload = arguments.path("payload");
        if (payload == null || !payload.isObject()) return null;

        StringBuilder sb = new StringBuilder();
        if (name.equals("createTestQuestion")) {
            JsonNode plan = payload.path("plan");
            String planName = plan.path("name").asText("câu kiểm tra");
            Integer seq = plan.path("sequenceOrder").asInt();
            Integer weight = plan.path("weight").asInt(1);
            JsonNode steps = payload.path("steps");
            sb.append("Mình đã chuẩn bị tạo câu kiểm tra **").append(planName).append("**");
            if (seq != null) sb.append(" (sequenceOrder=").append(seq).append(")");
            sb.append(", weight=").append(weight).append(", ").append(steps.size()).append(" bước.\n\n");
            sb.append("| # | Bước | Loại | Kiểm tra chính |\n");
            sb.append("|---|------|------|----------------|\n");
            if (steps.isArray()) {
                int i = 0;
                for (JsonNode step : steps) {
                    i++;
                    String stepName = step.path("name").asText("bước " + i);
                    String stepType = step.path("stepType").asText("");
                    JsonNode config = step.path("config");
                    String typeDesc;
                    String checkDesc;
                    if ("HTTP_REQUEST".equals(stepType)) {
                        String method = config.path("method").asText("");
                        String path = config.path("path").asText("");
                        typeDesc = method.isBlank() ? "HTTP" : method + " " + path;
                        checkDesc = checkSummary(config);
                    } else {
                        typeDesc = stepType.isBlank() ? "DB" : stepType;
                        checkDesc = dbCheckSummary(stepType, config);
                    }
                    sb.append("| ").append(i).append(" | ").append(escape(stepName))
                            .append(" | ").append(typeDesc).append(" | ").append(checkDesc).append(" |\n");
                }
            }
            sb.append("\nSau khi tạo, gõ **tiếp** để làm câu kế tiếp.");
        } else if (name.equals("createTestStep")) {
            String stepName = payload.path("name").asText("bước");
            String stepType = payload.path("stepType").asText("");
            JsonNode config = payload.path("config");
            String typeDesc, checkDesc;
            if ("HTTP_REQUEST".equals(stepType)) {
                typeDesc = config.path("method").asText("HTTP") + " " + config.path("path").asText("");
                checkDesc = checkSummary(config);
            } else {
                typeDesc = stepType.isBlank() ? "DB" : stepType;
                checkDesc = dbCheckSummary(stepType, config);
            }
            sb.append("Mình đã chuẩn bị tạo bước **").append(escape(stepName)).append("** (")
                    .append(typeDesc).append("): ").append(checkDesc).append(".\n");
            sb.append("\nSau khi tạo, gõ **tiếp** để làm câu kế tiếp.");
        } else {
            return null;
        }
        return sb.toString();
    }

    private String checkSummary(JsonNode config) {
        List<String> items = new ArrayList<>();
        JsonNode es = config.path("expected_status");
        if (!es.isMissingNode() && !es.isNull()) items.add(es.asText());
        JsonNode assertions = config.path("assertions");
        if (assertions.isArray()) {
            for (JsonNode a : assertions) {
                String kind = a.path("kind").asText("");
                if (!kind.isBlank()) items.add(kind);
            }
        }
        JsonNode extract = config.path("extract");
        if (extract.isArray()) {
            for (JsonNode ex : extract) {
                String n = ex.path("name").asText("");
                if (!n.isBlank()) items.add("extract " + n);
            }
        }
        return items.isEmpty() ? "không có kiểm tra" : String.join(", ", items);
    }

    private String dbCheckSummary(String stepType, JsonNode config) {
        if ("DB_SCHEMA_CHECK".equals(stepType)) {
            JsonNode checks = config.path("checks");
            List<String> items = new ArrayList<>();
            if (checks.isArray()) {
                for (JsonNode c : checks) items.add(c.path("kind").asText("check"));
            }
            return items.isEmpty() ? "checks trống" : String.join(", ", items);
        }
        if ("DB_QUERY".equals(stepType)) {
            String q = config.path("query").asText("");
            return q.length() > 60 ? q.substring(0, 60) + "…" : q;
        }
        if ("DB_MIGRATION".equals(stepType)) {
            JsonNode stmts = config.path("statements");
            return stmts.isArray() ? stmts.size() + " câu SQL" : "câu SQL";
        }
        return "config";
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("|", "\\|");
    }

    private Map<String, Object> payloadSchema() {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> integer = Map.of("type", "integer");
        Map<String, Object> plan = Map.of("type", "object", "properties", Map.of(
                "name", text, "description", text, "sequenceOrder", integer, "weight", integer),
                "required", List.of("name", "sequenceOrder"));
        Map<String, Object> step = Map.of("type", "object", "properties", Map.of(
                "stepOrder", integer, "name", text, "description", text,
                "stepType", Map.of("type", "string", "enum", List.of("HTTP_REQUEST", "DB_QUERY", "DB_SCHEMA_CHECK", "DB_MIGRATION")),
                "config", stepConfigSchema(), "weight", integer, "timeoutMs", integer,
                "required", Map.of("type", "boolean"), "expectedResult", Map.of("type", "object")),
                "required", List.of("stepOrder", "name", "stepType", "config"));
        return switch (name) {
            case "createTestPlan" -> plan;
            case "createTestQuestion" -> Map.of("type", "object", "properties", Map.of(
                    "plan", plan, "steps", Map.of("type", "array", "items", step, "minItems", 1)),
                    "required", List.of("plan", "steps"));
            case "createTestStep" -> step;
            case "updateTestPlan" -> Map.of("type", "object", "properties", Map.of(
                    "name", text, "description", text, "sequenceOrder", integer, "weight", integer),
                    "required", List.of("name"));
            case "updateTestStep" -> Map.of("type", "object", "properties", Map.of(
                    "name", text, "description", text, "stepOrder", integer, "stepType", text,
                    "config", stepConfigSchema(), "weight", integer), "required", List.of("name"));
            case "updateAssignment" -> Map.of("type", "object", "properties", Map.of(
                    "title", text, "description", text, "classId", text, "gradingStrategy", text),
                    "required", List.of("title"));
            case "setScoreComponents" -> Map.of("type", "array", "items", Map.of("type", "object",
                    "properties", Map.of("type", text, "weight", Map.of("type", "number")),
                    "required", List.of("type", "weight")));
            case "setStudentScores" -> Map.of("type", "array", "items", Map.of("type", "object",
                    "properties", Map.of("componentType", text, "score", Map.of("type", "number")),
                    "required", List.of("componentType", "score")));
            default -> Map.of("type", "object");
        };
    }

    private Map<String, Object> stepConfigSchema() {
        return Map.of("type", "object", "description",
                "Theo stepType: HTTP_REQUEST cần method và path (đường dẫn bắt đầu bằng /, không dùng url); "
                        + "DB_QUERY cần query (câu SQL, không dùng sql); "
                        + "DB_SCHEMA_CHECK cần checks (mảng không rỗng); "
                        + "DB_MIGRATION cần statements (mảng không rỗng).");
    }
}
