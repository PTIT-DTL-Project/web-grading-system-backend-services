package vn.edu.ptit.web_grading_system.course_service.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.util.StringUtils;
import vn.edu.ptit.web_grading_system.course_service.entity.StepType;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Structural validation of test-step config JSON per step type.
 * Pure static utility — no Spring context needed. Unknown keys are tolerated
 * (forward compatible); wrong structure fails with a message naming the key.
 */
public final class StepConfigValidator {

    private StepConfigValidator() {
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> HTTP_METHODS =
            Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> ASSERTION_KINDS =
            Set.of("status", "body_structure", "body_equals", "json_path", "contains", "field_equals",
                    "array_length", "contains_item", "json_path_equals", "greater_than", "less_than", "matches_regex", "any_of");
    private static final Set<String> SCHEMA_CHECK_KINDS =
            Set.of("TABLE_EXISTS", "COLUMN_EXISTS", "INDEX_EXISTS", "PRIMARY_KEY");
    /* Allowed engines — matches the executor's DbDialectRegistry keys
     * (mariadb rides the mysql dialect, wire-compatible). The executor
     * resolves its message from the same set; the two services cannot be
     * unit-tested against each other (separate Maven projects). */
    private static final Set<String> DB_TYPES =
            Set.of("postgres", "mysql", "mariadb");

    /** @return canonical config JSON string; throws IllegalArgumentException on invalid structure. */
    public static String validateAndSerialize(StepType type, JsonNode config) {
        if (config == null || !config.isObject()) {
            throw new IllegalArgumentException("config must be a JSON object");
        }
        switch (type) {
            case HTTP_REQUEST -> validateHttp(config);
            case DB_QUERY -> validateDbQuery(config);
            case DB_SCHEMA_CHECK -> validateSchemaCheck(config);
            case DB_MIGRATION -> validateMigration(config);
            case EXTRACT -> throw new IllegalArgumentException(
                    "standalone EXTRACT steps are not runnable; use the HTTP step's extract[] instead");
            case DELAY -> throw new IllegalArgumentException(
                    "standalone DELAY steps are not supported");
        }
        try {
            return MAPPER.writeValueAsString(config);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize config: " + e.getMessage());
        }
    }

    private static void requireText(JsonNode node, String field, String ctx) {
        if (!node.hasNonNull(field) || !StringUtils.hasText(node.get(field).asString())) {
            throw new IllegalArgumentException(ctx + ": missing or empty '" + field + "'");
        }
    }

    private static void validateHttp(JsonNode c) {
        String ctx = "HTTP_REQUEST";
        requireText(c, "method", ctx);
        if (!HTTP_METHODS.contains(c.get("method").asString().toUpperCase())) {
            throw new IllegalArgumentException(ctx + ": unknown method '" + c.get("method").asString() + "'");
        }
        requireText(c, "path", ctx);
        if (!c.get("path").asString().startsWith("/")) {
            throw new IllegalArgumentException(ctx + ": path must start with '/'");
        }
        if (c.hasNonNull("headers") && !c.get("headers").isObject()) {
            throw new IllegalArgumentException(ctx + ": headers must be an object of strings");
        }
        if (c.hasNonNull("query_params") && !c.get("query_params").isObject()) {
            throw new IllegalArgumentException(ctx + ": query_params must be an object");
        }
        if (c.hasNonNull("expected_status")) {
            JsonNode es = c.get("expected_status");
            if (!es.isInt() || es.asInt() < 100 || es.asInt() > 599) {
                throw new IllegalArgumentException(ctx + ": expected_status must be an integer 100–599");
            }
        }
        if (c.hasNonNull("assertions") && c.get("assertions").isArray()) {
            for (JsonNode a : c.get("assertions")) {
                requireText(a, "kind", ctx + ".assertions");
                String kind = a.get("kind").asString();
                if (!ASSERTION_KINDS.contains(kind)) {
                    throw new IllegalArgumentException(ctx + ".assertions: unknown kind '" + kind + "'");
                }
                switch (kind) {
                    case "status" -> requireText(a, "equals", ctx + ".assertions[status]");
                    case "json_path" -> requireText(a, "path", ctx + ".assertions[json_path]");
                    case "contains" -> requireText(a, "text", ctx + ".assertions[contains]");
                    case "field_equals" -> {
                        requireText(a, "path", ctx + ".assertions[field_equals]");
                        requireText(a, "equals", ctx + ".assertions[field_equals]");
                    }
                    case "array_length" -> {
                        requireText(a, "path", ctx + ".assertions[array_length]");
                        requireText(a, "equals", ctx + ".assertions[array_length]");
                    }
                    case "contains_item" -> {
                        requireText(a, "path", ctx + ".assertions[contains_item]");
                        requireText(a, "equals", ctx + ".assertions[contains_item]");
                    }
                    case "json_path_equals" -> {
                        requireText(a, "path", ctx + ".assertions[json_path_equals]");
                        requireText(a, "equals", ctx + ".assertions[json_path_equals]");
                    }
                    case "greater_than", "less_than" -> {
                        requireText(a, "path", ctx + ".assertions[" + kind + "]");
                        requireText(a, "value", ctx + ".assertions[" + kind + "]");
                    }
                    case "matches_regex" -> {
                        requireText(a, "path", ctx + ".assertions[matches_regex]");
                        requireText(a, "pattern", ctx + ".assertions[matches_regex]");
                    }
                    case "any_of" -> {
                        if (!a.hasNonNull("assertions") || !a.get("assertions").isArray() || a.get("assertions").isEmpty()) {
                            throw new IllegalArgumentException(ctx + ".assertions[any_of]: 'assertions' must be a non-empty array");
                        }
                    }
                    default -> { /* body_structure / body_equals carry 'json' of any shape */ }
                }
            }
        }
        if (c.hasNonNull("extract") && c.get("extract").isArray()) {
            for (JsonNode ex : c.get("extract")) {
                requireText(ex, "name", ctx + ".extract");
                requireText(ex, "from", ctx + ".extract");
                requireText(ex, "expression", ctx + ".extract");
            }
        }
        // --- Phase 3: Loop Safety Governor ---
        // Inline data[] is capped at 10 entries. Mirrors executor Constant.Loop.DATA_INLINE_MAX
        // (course-service and executor-service are separate modules, so the limit is stated here).
        if (c.hasNonNull("data")) {
            JsonNode data = c.get("data");
            if (!data.isArray() || data.isEmpty()) {
                throw new IllegalArgumentException(ctx + ": data must be a non-empty array");
            }
            if (data.size() > 10) {
                throw new IllegalArgumentException(ctx
                        + ": data must contain at most 10 entries (loop safety governor, got " + data.size() + ")");
            }
            for (JsonNode entry : data) {
                if (!entry.isObject()) {
                    throw new IllegalArgumentException(ctx + ": data entries must be objects");
                }
            }
        }
        // --- Phase 3: auto_inject_headers must be an object of header name -> value template ---
        if (c.hasNonNull("auto_inject_headers")) {
            JsonNode ai = c.get("auto_inject_headers");
            if (!ai.isObject()) {
                throw new IllegalArgumentException(ctx + ": auto_inject_headers must be an object of strings");
            }
            ai.properties().forEach(e -> {
                if (!e.getValue().isTextual()) {
                    throw new IllegalArgumentException(ctx
                            + ": auto_inject_headers." + e.getKey() + " must be a string");
                }
            });
        }
    }

    private static void validateDbQuery(JsonNode c) {
        validateConnection(c, "DB_QUERY");
        requireText(c, "query", "DB_QUERY");
        if (c.hasNonNull("expected") && !c.get("expected").isObject()) {
            throw new IllegalArgumentException("DB_QUERY: expected must be an object");
        }
    }

    /**
     * The `connection` block is optional (unknown keys tolerated, forward
     * compatible) but when present:
     * <ul>
     *   <li>{@code db_type} must be a known engine key
     *       ({@code postgres}, {@code mysql}, {@code mariadb}) — the executor
     *       maps it to a JDBC dialect; unknown values 400 at save time.</li>
     *   <li>{@code database}, when present, must be a bare identifier
     *       ({@code [A-Za-z0-9_$]+}) — it is concatenated into the JDBC URL,
     *       so a value with {@code ?}&amp;#&amp;/&amp;:&amp;} would inject
     *       connection properties. Review: 2026-09-26, Pullfrog PR #17 (F4).</li>
     *   <li>{@code db_port}, when present, must be an integer in 1–65535
     *       (mirrors the {@code expected_status} range check). Out-of-range
     *       values are unrecoverable at boot time, so they 400 here.</li>
     * </ul>
     */
    private static void validateConnection(JsonNode c, String ctx) {
        if (!c.hasNonNull("connection")) {
            return;
        }
        JsonNode conn = c.get("connection");
        if (!conn.isObject()) {
            throw new IllegalArgumentException(ctx + ": connection must be an object");
        }
        if (conn.hasNonNull("db_type")) {
            String dbType = conn.get("db_type").asString().trim().toLowerCase();
            if (!DB_TYPES.contains(dbType)) {
                throw new IllegalArgumentException(ctx
                        + ": unknown connection.db_type '" + conn.get("db_type").asString()
                        + "' (allowed: " + String.join(", ", DB_TYPES.stream().sorted().toList()) + ")");
            }
        }
        if (conn.hasNonNull("database")) {
            String database = conn.get("database").asString();
            if (!database.matches("[A-Za-z0-9_$]+")) {
                throw new IllegalArgumentException(ctx
                        + ": connection.database must be a bare identifier [A-Za-z0-9_$]+");
            }
        }
        if (conn.hasNonNull("db_port")) {
            JsonNode port = conn.get("db_port");
            if (!port.isInt() || port.asInt() < 1 || port.asInt() > 65535) {
                throw new IllegalArgumentException(ctx
                        + ": connection.db_port must be an integer 1–65535");
            }
        }
    }

    private static void validateSchemaCheck(JsonNode c) {
        validateConnection(c, "DB_SCHEMA_CHECK");
        JsonNode checks = c.get("checks");
        if (checks == null || !checks.isArray() || checks.isEmpty()) {
            throw new IllegalArgumentException("DB_SCHEMA_CHECK: 'checks' must be a non-empty array");
        }
        List<String> errors = new ArrayList<>();
        for (JsonNode chk : checks) {
            String kind = chk.path("kind").asString("");
            if (!SCHEMA_CHECK_KINDS.contains(kind)) {
                errors.add("unknown check kind '" + kind + "'");
                continue;
            }
            switch (kind) {
                case "TABLE_EXISTS" -> requireText(chk, "table_name", "DB_SCHEMA_CHECK.TABLE_EXISTS");
                case "COLUMN_EXISTS" -> {
                    requireText(chk, "table_name", "DB_SCHEMA_CHECK.COLUMN_EXISTS");
                    requireText(chk, "column_name", "DB_SCHEMA_CHECK.COLUMN_EXISTS");
                }
                case "INDEX_EXISTS" -> requireText(chk, "index_name", "DB_SCHEMA_CHECK.INDEX_EXISTS");
                case "PRIMARY_KEY" -> requireText(chk, "column", "DB_SCHEMA_CHECK.PRIMARY_KEY");
                default -> { }
            }
        }
        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("DB_SCHEMA_CHECK.checks: " + String.join("; ", errors));
        }
    }

    private static void validateMigration(JsonNode c) {
        validateConnection(c, "DB_MIGRATION");
        JsonNode statements = c.get("statements");
        if (statements == null || !statements.isArray() || statements.isEmpty()) {
            throw new IllegalArgumentException("DB_MIGRATION: 'statements' must be a non-empty array");
        }
        for (JsonNode st : statements) {
            if (!st.isString() || !StringUtils.hasText(st.asString())) {
                throw new IllegalArgumentException("DB_MIGRATION.statements: entries must be non-empty strings");
            }
        }
    }

    // Review: 2026-10-11, phase A3 — validateExtract/validateDelay removed:
    // standalone EXTRACT/DELAY steps have no executor and are rejected above.

    /* ========================================================================
     * Phase 7 — JSON Schema generation
     * ======================================================================== */

    /**
     * Returns a JSON Schema (draft-07) describing the HTTP_REQUEST step config
     * structure accepted by {@link #validateAndSerialize}. The schema is
     * authoritative: any config that passes this schema's structural rules
     * should also pass {@link #validateAndSerialize}, and vice versa (modulo
     * cross-field constraints such as "data entries must be objects").
     *
     * <p>Returned node is a fresh tree each call — safe to mutate by callers.
     */
    public static JsonNode generateHttpSchema()
    {
        ObjectNode schema = MAPPER.createObjectNode();
        schema.put("$schema", "http://json-schema.org/draft-07/schema#");
        schema.put("title", "HTTP_REQUEST step config");
        schema.put("type", "object");
        schema.put("description", "Configuration for an HTTP_REQUEST grading step.");

        ArrayNode required = MAPPER.createArrayNode();
        required.add("method");
        required.add("path");
        schema.set("required", required);

        ObjectNode properties = MAPPER.createObjectNode();

        // method
        ObjectNode methodProp = MAPPER.createObjectNode();
        methodProp.put("type", "string");
        methodProp.put("description", "HTTP method");
        ArrayNode methodEnum = MAPPER.createArrayNode();
        HTTP_METHODS.forEach(methodEnum::add);
        methodProp.set("enum", methodEnum);
        properties.set("method", methodProp);

        // path
        ObjectNode pathProp = MAPPER.createObjectNode();
        pathProp.put("type", "string");
        pathProp.put("pattern", "^/");
        pathProp.put("description", "URL path, must start with '/'");
        properties.set("path", pathProp);

        // expected_status
        ObjectNode esProp = MAPPER.createObjectNode();
        esProp.put("type", "integer");
        esProp.put("minimum", 100);
        esProp.put("maximum", 599);
        esProp.put("description", "Expected HTTP status code");
        properties.set("expected_status", esProp);

        // headers
        ObjectNode headersProp = MAPPER.createObjectNode();
        headersProp.put("type", "object");
        headersProp.put("description", "Request headers, name -> string value");
        properties.set("headers", headersProp);

        // query_params
        ObjectNode qpProp = MAPPER.createObjectNode();
        qpProp.put("type", "object");
        qpProp.put("description", "Query parameters, name -> string value");
        properties.set("query_params", qpProp);

        // body
        ObjectNode bodyProp = MAPPER.createObjectNode();
        bodyProp.put("description", "Request body (JSON), optional");
        properties.set("body", bodyProp);

        // assertions
        properties.set("assertions", assertionSchema());

        // extract
        properties.set("extract", extractSchema());

        // data
        properties.set("data", dataSchema());

        // auto_inject_headers
        properties.set("auto_inject_headers", autoInjectHeadersSchema());

        schema.set("properties", properties);
        return schema;
    }

    private static ObjectNode assertionSchema()
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "array");
        node.put("description", "Assertion conditions, evaluated against the response");

        ObjectNode items = MAPPER.createObjectNode();
        items.put("type", "object");
        items.put("description", "A single assertion");

        ArrayNode itemRequired = MAPPER.createArrayNode();
        itemRequired.add("kind");
        items.set("required", itemRequired);

        ObjectNode itemProps = MAPPER.createObjectNode();

        ObjectNode kindProp = MAPPER.createObjectNode();
        kindProp.put("type", "string");
        kindProp.put("description", "Assertion kind");
        ArrayNode kindEnum = MAPPER.createArrayNode();
        ASSERTION_KINDS.forEach(kindEnum::add);
        kindProp.set("enum", kindEnum);
        itemProps.set("kind", kindProp);

        ObjectNode pathProp = MAPPER.createObjectNode();
        pathProp.put("type", "string");
        pathProp.put("description", "JSONPath expression");
        itemProps.set("path", pathProp);

        ObjectNode equalsProp = MAPPER.createObjectNode();
        equalsProp.put("description", "Expected value (string or number)");
        itemProps.set("equals", equalsProp);

        ObjectNode textProp = MAPPER.createObjectNode();
        textProp.put("type", "string");
        textProp.put("description", "Substring to search for");
        itemProps.set("text", textProp);

        ObjectNode patternProp = MAPPER.createObjectNode();
        patternProp.put("type", "string");
        patternProp.put("description", "Regex pattern");
        itemProps.set("pattern", patternProp);

        ObjectNode valueProp = MAPPER.createObjectNode();
        valueProp.put("description", "Numeric comparison value");
        itemProps.set("value", valueProp);

        ObjectNode existsProp = MAPPER.createObjectNode();
        existsProp.put("type", "boolean");
        existsProp.put("description", "Whether the path should exist (default true)");
        itemProps.set("exists", existsProp);

        ObjectNode jsonProp = MAPPER.createObjectNode();
        jsonProp.put("description", "Expected JSON (for body_equals / body_structure)");
        itemProps.set("json", jsonProp);

        // any_of sub-assertions recurse into this same assertion schema
        ObjectNode subAssertionsProp = MAPPER.createObjectNode();
        subAssertionsProp.put("type", "array");
        subAssertionsProp.put("description", "Sub-assertions for any_of (OR)");
        subAssertionsProp.set("items", items);
        itemProps.set("assertions", subAssertionsProp);

        items.set("properties", itemProps);
        node.set("items", items);
        return node;
    }

    private static ObjectNode extractSchema()
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "array");
        node.put("description", "Variables extracted from the response");

        ObjectNode items = MAPPER.createObjectNode();
        items.put("type", "object");

        ArrayNode itemRequired = MAPPER.createArrayNode();
        itemRequired.add("name");
        itemRequired.add("from");
        itemRequired.add("expression");
        items.set("required", itemRequired);

        ObjectNode itemProps = MAPPER.createObjectNode();

        ObjectNode nameProp = MAPPER.createObjectNode();
        nameProp.put("type", "string");
        nameProp.put("description", "Variable name");
        itemProps.set("name", nameProp);

        ObjectNode fromProp = MAPPER.createObjectNode();
        fromProp.put("type", "string");
        fromProp.put("description", "Source (currently only 'response_body')");
        itemProps.set("from", fromProp);

        ObjectNode exprProp = MAPPER.createObjectNode();
        exprProp.put("type", "string");
        exprProp.put("description", "JsonPath expression");
        itemProps.set("expression", exprProp);

        ObjectNode asProp = MAPPER.createObjectNode();
        asProp.put("type", "string");
        ArrayNode asEnum = MAPPER.createArrayNode();
        asEnum.add("scalar");
        asEnum.add("json");
        asProp.set("enum", asEnum);
        asProp.put("description", "How to store the value (scalar or json)");
        itemProps.set("as", asProp);

        ObjectNode fallbackProp = MAPPER.createObjectNode();
        fallbackProp.put("type", "string");
        fallbackProp.put("description", "Default value if JsonPath does not match");
        itemProps.set("fallback", fallbackProp);

        items.set("properties", itemProps);
        node.set("items", items);
        return node;
    }

    private static ObjectNode dataSchema()
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "array");
        node.put("description", "Data-driven loop: one iteration per entry (max 10)");

        ObjectNode items = MAPPER.createObjectNode();
        items.put("type", "object");
        items.put("description", "One data row; fields substituted into ${field} placeholders");
        node.set("items", items);
        return node;
    }

    private static ObjectNode autoInjectHeadersSchema()
    {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", "object");
        node.put("description", "Headers injected into subsequent steps (plan-scoped)");
        return node;
    }
}