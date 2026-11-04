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
    public Set<String> resolvedIdentifiers() {
        if (!method.equals("GET")) return Set.of();
        return switch (name) {
            case "listTestPlans", "listMyTestPlans" -> Set.of("planId");
            case "listTestSteps" -> Set.of("stepId");
            case "listMySubmissions" -> Set.of("submissionId");
            case "getClassRoster" -> Set.of("studentCode");
            default -> Set.of();
        };
    }

    @Override
    public Set<String> lookupPrerequisites() {
        return Set.of(pathKeys);
    }

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
            if (name.equals("createTestSteps")) {
                JsonNode payload = args.path("payload");
                JsonNode steps = payload.has("steps") ? payload.path("steps") : payload;
                if (!steps.isArray() || steps.isEmpty()) {
                    return ToolResult.error("INVALID_ARGUMENTS", "payload.steps phải là mảng không rỗng", false);
                }
                ArrayNode created = mapper.createArrayNode();
                for (JsonNode step : steps) {
                    RestClient.RequestBodySpec req = client.method(HttpMethod.POST)
                            .uri(builder -> builder.path(path).build(variables))
                            .header("X-User-Id", context.userId().toString())
                            .header("X-User-Roles", role)
                            .header("X-User-Email", context.email() == null ? "" : context.email())
                            .header("X-Gateway-Secret", context.gatewaySecret() == null ? "" : context.gatewaySecret())
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(mapper.writeValueAsString(step));
                    String raw = req.retrieve().body(String.class);
                    JsonNode res = raw == null || raw.isBlank() ? mapper.createObjectNode().put("completed", true) : mapper.readTree(raw);
                    if (res.has("data")) res = res.path("data");
                    created.add(res);
                }
                return ToolResult.ok(created);
            }
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
                        ? !args.path("payload").isArray()
                        : (name.equals("createTestSteps")
                                ? (!args.path("payload").isObject() && !args.path("payload").isArray())
                                : !args.path("payload").isObject())))) {
            return ToolResult.error("INVALID_ARGUMENTS", "payload không đúng cấu trúc JSON", false);
        }
        if (name.equals("createTestQuestion")) {
            JsonNode plan = args.path("payload").path("plan");
            JsonNode steps = args.path("payload").path("steps");
            if (!plan.isObject() || plan.path("name").asText().isBlank() || !plan.path("sequenceOrder").isInt())
                return invalidStep("payload.plan", "cần name và sequenceOrder");
            if (!steps.isArray() || steps.isEmpty()) return invalidStep("payload.steps", "cần mảng bước không rỗng");
            for (int i = 0; i < steps.size(); i++) {
                ToolResult invalid = validateStepConfig(steps.get(i), "payload.steps[" + i + "]");
                if (invalid != null) return invalid;
            }
        } else if (name.equals("createTestStep")) {
            ToolResult invalid = validateStepConfig(args.path("payload"), "payload");
            if (invalid != null) return invalid;
        } else if (name.equals("createTestSteps")) {
            JsonNode payload = args.path("payload");
            JsonNode steps = payload.has("steps") ? payload.path("steps") : payload;
            if (!steps.isArray() || steps.isEmpty()) return invalidStep("payload.steps", "cần mảng bước không rỗng");
            for (int i = 0; i < steps.size(); i++) {
                ToolResult invalid = validateStepConfig(steps.get(i), "payload.steps[" + i + "]");
                if (invalid != null) return invalid;
            }
        }
        if (name.equals("createTestQuestion") || name.equals("createTestStep") || name.equals("createTestSteps") || name.equals("updateTestStep"))
            return validateStepPayloads(args);
        return null;
    }

    /**
     * Calls course-service {@code POST /api/v1/internal/test-plans/validate} or
     * {@code /validate-step} so a malformed step config is rejected *before*
     * {@link WriteApprovalService#propose} persists a pending action.
     */
    private ToolResult validateStepPayloads(JsonNode args) {
        JsonNode payload = args.path("payload");
        if (payload == null || (!payload.isObject() && !payload.isArray())) {
            return ToolResult.error("INVALID_ARGUMENTS", "payload không phải JSON object hoặc array", false);
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
            } else if (name.equals("createTestSteps")) {
                ObjectNode validateBody = mapper.createObjectNode();
                JsonNode steps = payload.has("steps") ? payload.path("steps") : payload;
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
        if (name.equals("createTestQuestion") || name.equals("createTestSteps")) {
            JsonNode steps = payload.has("steps") ? payload.path("steps") : payload;
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
        return validateStepConfig(step, location);
    }

    private ToolResult validateStepConfig(JsonNode step, String location) {
        if (!step.path("stepOrder").isInt() || step.path("stepOrder").intValue() < 0)
            return invalidStep(location, "stepOrder phải là số nguyên không âm");
        if (step.path("name").asText().isBlank()) return invalidStep(location, "thiếu name");
        String stepType = step.path("stepType").asText();
        JsonNode config = step.path("config");
        if (!config.isObject()) return invalidStep(location, "config phải là JSON object");
        switch (stepType) {
            case "HTTP_REQUEST" -> {
                String method = config.path("method").asText();
                if (method.isBlank()) return invalidStep(location, "config thiếu 'method' cho HTTP_REQUEST");
                if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")
                        .contains(method.toUpperCase(Locale.ROOT))) return invalidStep(location, "config.method không hợp lệ");
                String path = config.path("path").asText();
                if (path.isBlank()) return invalidStep(location, "config thiếu 'path' cho HTTP_REQUEST");
                if (!path.startsWith("/")) return invalidStep(location, "config.path phải bắt đầu bằng /");
                JsonNode status = config.path("expected_status");
                if (!status.isMissingNode() && !status.isNull() && (!status.isInt() || status.intValue() < 100 || status.intValue() > 599))
                    return invalidStep(location, "config.expected_status phải từ 100 đến 599");
                if (config.hasNonNull("query_params") && !config.path("query_params").isObject())
                    return invalidStep(location, "config.query_params phải là object");
                if (config.hasNonNull("headers") && !config.path("headers").isObject())
                    return invalidStep(location, "config.headers phải là object");
                JsonNode data = config.path("data");
                if (!data.isMissingNode() && !data.isNull()) {
                    if (!data.isArray() || data.isEmpty() || data.size() > 10)
                        return invalidStep(location, "config.data phải là mảng 1–10 object");
                    for (JsonNode item : data) if (!item.isObject())
                        return invalidStep(location, "config.data chỉ nhận object");
                }
                JsonNode autoHeaders = config.path("auto_inject_headers");
                if (!autoHeaders.isMissingNode() && !autoHeaders.isNull()) {
                    if (!autoHeaders.isObject()) return invalidStep(location, "config.auto_inject_headers phải là object");
                    for (var header : autoHeaders.properties()) if (!header.getValue().isTextual())
                        return invalidStep(location, "config.auto_inject_headers chỉ nhận chuỗi");
                }
                JsonNode extract = config.path("extract");
                if (!extract.isMissingNode() && !extract.isNull()) {
                    if (!extract.isArray()) return invalidStep(location, "config.extract phải là mảng");
                    for (JsonNode item : extract) if (item.path("name").asText().isBlank()
                            || item.path("from").asText().isBlank() || item.path("expression").asText().isBlank())
                        return invalidStep(location, "config.extract cần name, from, expression");
                }
                JsonNode assertions = config.path("assertions");
                if (!assertions.isMissingNode() && !assertions.isNull()) {
                    if (!assertions.isArray()) return invalidStep(location, "config.assertions phải là mảng");
                    ToolResult invalid = validateAssertions(assertions, location);
                    if (invalid != null) return invalid;
                }
            }
            case "DB_QUERY", "DB_SCHEMA_CHECK", "DB_MIGRATION" -> {
                ToolResult connectionError = validateDbConnection(config.path("connection"), location);
                if (connectionError != null) return connectionError;
                if (stepType.equals("DB_QUERY")) {
                    String query = config.path("query").asText();
                    if (query.isBlank()) return invalidStep(location, "config thiếu 'query' cho DB_QUERY");
                    JsonNode expected = config.path("expected");
                    if (!expected.isMissingNode() && !expected.isNull() && !expected.isObject())
                        return invalidStep(location, "config.expected phải là object");
                    if (query.matches("(?is).*\\bcount\\s*\\(\\s*\\*\\s*\\).*" )
                            && expected.path("row_count").isNumber() && expected.path("row_count").asInt() == 0)
                        return invalidStep(location, "COUNT(*) luôn trả một dòng; không dùng row_count: 0");
                } else if (stepType.equals("DB_SCHEMA_CHECK")) {
                    JsonNode checks = config.path("checks");
                    if (!checks.isArray() || checks.isEmpty()) return invalidStep(location, "config.checks phải là mảng không rỗng");
                    for (JsonNode check : checks) {
                        String kind = check.path("kind").asText();
                        if (!Set.of("TABLE_EXISTS", "COLUMN_EXISTS", "INDEX_EXISTS", "PRIMARY_KEY").contains(kind))
                            return invalidStep(location, "config.checks.kind không hợp lệ");
                        if ((kind.equals("TABLE_EXISTS") || kind.equals("COLUMN_EXISTS") || kind.equals("PRIMARY_KEY"))
                                && check.path("table_name").asText().isBlank())
                            return invalidStep(location, "config.checks cần table_name");
                        if (kind.equals("COLUMN_EXISTS") && check.path("column_name").asText().isBlank())
                            return invalidStep(location, "COLUMN_EXISTS cần column_name");
                        if (kind.equals("INDEX_EXISTS") && (check.path("table_name").asText().isBlank()
                                || check.path("index_name").asText().isBlank()))
                            return invalidStep(location, "INDEX_EXISTS cần table_name và index_name");
                        if (kind.equals("PRIMARY_KEY") && check.path("column").asText().isBlank())
                            return invalidStep(location, "PRIMARY_KEY cần column");
                    }
                } else {
                    JsonNode statements = config.path("statements");
                    if (!statements.isArray() || statements.isEmpty()) return invalidStep(location, "config.statements phải là mảng không rỗng");
                    for (JsonNode sql : statements) if (!sql.isTextual() || sql.asText().isBlank())
                        return invalidStep(location, "config.statements chỉ nhận chuỗi SQL không rỗng");
                }
            }
            default -> { return invalidStep(location, "stepType không được hỗ trợ"); }
        }
        return null;
    }

    private ToolResult validateAssertions(JsonNode assertions, String location) {
        for (JsonNode assertion : assertions) {
            String kind = assertion.path("kind").asText();
            if (!Set.of("status", "json_path", "contains", "body_structure", "body_equals", "field_equals",
                    "array_length", "contains_item", "json_path_equals", "greater_than", "less_than",
                    "matches_regex", "any_of").contains(kind)) return invalidStep(location, "assertions.kind không hợp lệ");
            if (Set.of("json_path", "field_equals", "array_length", "contains_item", "json_path_equals",
                    "greater_than", "less_than", "matches_regex").contains(kind)
                    && assertion.path("path").asText().isBlank()) return invalidStep(location, kind + " cần path");
            if (Set.of("status", "field_equals", "array_length", "contains_item", "json_path_equals").contains(kind)
                    && assertion.path("equals").isMissingNode()) return invalidStep(location, kind + " cần equals");
            if (kind.equals("contains") && assertion.path("text").asText().isBlank()) return invalidStep(location, "contains cần text");
            if (Set.of("body_equals", "body_structure").contains(kind) && assertion.path("json").isMissingNode())
                return invalidStep(location, kind + " cần json");
            if (Set.of("greater_than", "less_than").contains(kind) && assertion.path("value").isMissingNode())
                return invalidStep(location, kind + " cần value");
            if (kind.equals("matches_regex") && assertion.path("pattern").asText().isBlank())
                return invalidStep(location, "matches_regex cần pattern");
            if (kind.equals("any_of")) {
                JsonNode children = assertion.path("assertions");
                if (!children.isArray() || children.isEmpty()) return invalidStep(location, "any_of cần assertions không rỗng");
                ToolResult nested = validateAssertions(children, location);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private ToolResult validateDbConnection(JsonNode connection, String location) {
        if (connection.isMissingNode() || connection.isNull()) return null;
        if (!connection.isObject()) return invalidStep(location, "config.connection phải là object");
        String dbType = connection.path("db_type").asText();
        if (!dbType.isBlank() && !Set.of("postgres", "mysql", "mariadb").contains(dbType.toLowerCase(Locale.ROOT)))
            return invalidStep(location, "config.connection.db_type không được hỗ trợ");
        JsonNode port = connection.path("db_port");
        if (!port.isMissingNode() && !port.isNull() && (!port.isInt() || port.intValue() < 1 || port.intValue() > 65535))
            return invalidStep(location, "config.connection.db_port phải là port nội bộ 1–65535");
        return null;
    }

    private ToolResult invalidStep(String location, String reason) {
        return ToolResult.error("INVALID_ARGUMENTS", location + "." + reason, false);
    }

    /**
     * Human-readable preview of a write payload, used by AgentRuntime.formatPendingQuestion
     * to render a numbered step table instead of raw JSON.
     */
    @Override
    public String summarize(JsonNode arguments) {
        if (arguments == null || !arguments.isObject()) return null;
        JsonNode payload = arguments.path("payload");
        if (payload == null || (!payload.isObject() && !payload.isArray())) return null;

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
        } else if (name.equals("createTestSteps")) {
            JsonNode steps = payload.has("steps") ? payload.path("steps") : payload;
            sb.append("Mình đã chuẩn bị tạo **").append(steps.size()).append(" bước chấm** cho câu kiểm tra:\n\n");
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
            sb.append("\nSau khi tạo xong các bước, gõ **tiếp** để làm câu kế tiếp.");
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
            case "createTestSteps" -> Map.of("type", "object", "properties", Map.of(
                    "steps", Map.of("type", "array", "items", step, "minItems", 1)),
                    "required", List.of("steps"));
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
        Map<String, Object> properties = new LinkedHashMap<>();
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> object = Map.of("type", "object");
        properties.put("method", Map.of("type", "string", "enum", List.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS")));
        properties.put("path", Map.of("type", "string", "pattern", "^/"));
        properties.put("headers", object);
        properties.put("query_params", object);
        properties.put("body", Map.of("description", "JSON body tùy endpoint"));
        properties.put("expected_status", Map.of("type", "integer", "minimum", 100, "maximum", 599));
        properties.put("assertions", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                "kind", Map.of("type", "string", "enum", List.of("status", "json_path", "contains", "body_structure", "body_equals", "field_equals", "array_length", "contains_item", "json_path_equals", "greater_than", "less_than", "matches_regex", "any_of")),
                "path", text, "equals", Map.of("description", "Giá trị mong đợi"), "text", text,
                "json", Map.of("description", "JSON mong đợi"), "value", Map.of("description", "Ngưỡng so sánh"),
                "pattern", text, "exists", Map.of("type", "boolean"), "assertions", Map.of("type", "array")),
                "required", List.of("kind"))));
        properties.put("extract", Map.of("type", "array", "items", Map.of("type", "object", "properties", Map.of(
                "name", text, "from", text, "expression", text, "as", Map.of("type", "string", "enum", List.of("scalar", "json")), "fallback", text),
                "required", List.of("name", "from", "expression"))));
        properties.put("data", Map.of("type", "array", "minItems", 1, "maxItems", 10, "items", object));
        properties.put("auto_inject_headers", object);
        properties.put("required", Map.of("type", "boolean"));
        properties.put("timeoutMs", Map.of("type", "integer", "minimum", 1));
        properties.put("connection", Map.of("type", "object", "description", "db_type postgres/mysql/mariadb; db_service là tên service Compose; db_port là port DB nội bộ; database/username/password phải khớp Compose"));
        properties.put("query", text);
        properties.put("expected", object);
        properties.put("checks", Map.of("type", "array", "items", object));
        properties.put("statements", Map.of("type", "array", "items", text));
        return Map.of("type", "object", "properties", properties, "additionalProperties", true, "description",
                "Theo stepType: HTTP_REQUEST cần method và path (đường dẫn bắt đầu bằng /, không dùng url); "
                        + "query_params dùng giá trị thô; extract trước khi dùng ${...}; "
                        + "DB_QUERY cần query (câu SQL, không dùng sql), row_count đếm số dòng kết quả; "
                        + "DB_SCHEMA_CHECK cần checks (mảng không rỗng), PRIMARY_KEY cần table_name và column; "
                        + "DB_MIGRATION cần statements (mảng không rỗng). DB connection phải khớp Docker Compose.");
    }
}
