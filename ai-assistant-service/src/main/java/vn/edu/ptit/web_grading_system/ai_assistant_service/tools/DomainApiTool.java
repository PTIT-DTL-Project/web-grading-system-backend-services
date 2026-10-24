package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            return ToolResult.error("DOMAIN_REJECTED", "API từ chối: " + e.getStatusCode(), false);
        } catch (org.springframework.web.client.ResourceAccessException e) {
            return method.equals("GET")
                    ? ToolResult.error("DOMAIN_API_UNAVAILABLE", "Không kết nối được API để tra cứu", true)
                    : ToolResult.error("UNKNOWN_OUTCOME", "Mất kết nối; chưa xác định API đã ghi dữ liệu hay chưa", false);
        } catch (Exception e) {
            return ToolResult.error("DOMAIN_API_ERROR", "Không xác nhận được kết quả từ API: " + e.getMessage(), false);
        }
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
        return null;
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
                "config", Map.of("type", "object"), "weight", integer, "timeoutMs", integer,
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
                    "config", Map.of("type", "object"), "weight", integer), "required", List.of("name"));
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
}
