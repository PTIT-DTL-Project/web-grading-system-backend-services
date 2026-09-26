package vn.edu.ptit.web_grading_system.course_service.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;
import vn.edu.ptit.web_grading_system.course_service.entities.StepType;

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
            Set.of("status", "body_structure", "body_equals", "json_path", "contains", "field_equals");
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
            case EXTRACT -> validateExtract(config);
            case DELAY -> validateDelay(config);
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
            String database = conn.get("database").asText();
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

    private static void validateExtract(JsonNode c) {
        JsonNode variables = c.get("variables");
        if (variables == null || !variables.isArray() || variables.isEmpty()) {
            throw new IllegalArgumentException("EXTRACT: 'variables' must be a non-empty array");
        }
        for (JsonNode v : variables) {
            requireText(v, "name", "EXTRACT.variables");
            boolean hasValue = v.hasNonNull("value");
            boolean hasFrom = v.hasNonNull("from") && StringUtils.hasText(v.get("from").asString());
            boolean hasExpr = v.hasNonNull("expression") && StringUtils.hasText(v.get("expression").asString());
            if (!hasValue && !(hasFrom && hasExpr)) {
                throw new IllegalArgumentException(
                        "EXTRACT.variables['" + v.get("name").asString() + "']: needs 'value' or ('from' + 'expression')");
            }
        }
    }

    private static void validateDelay(JsonNode c) {
        JsonNode d = c.get("duration_ms");
        if (d == null || !d.canConvertToInt() || d.asInt() <= 0) {
            throw new IllegalArgumentException("DELAY: duration_ms must be a positive integer");
        }
    }
}