package vn.edu.ptit.web_grading_system.executor_service.service.scoring;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Central evidence redactor for everything grading evidence sends toward
 * students (and later the chatbot): response/request bodies, verdict text,
 * extracted vars. One function, applied at every output path, fail-closed.
 *
 * <p>Rules: JSON-aware recursive key masking when parseable (byte-identical
 * passthrough when clean); descent continues into string values that are
 * themselves JSON (escaped verdict bodies), because the regex sweep cannot see
 * through escaping; regex sweep for token shapes on any text; any internal
 * failure returns {@code "***"} — loud, never leaking, never silent.
 * No depth cap: nesting follows the finite input, and pathological depth
 * fail-closes via the catch-all.
 *
 * <p>Review: 2026-10-10, phase A4 sanitize; Pullfrog nested-string descent.
 */
public final class EvidenceRedactor {

    private EvidenceRedactor() {
    }

    // Exact key match (lowercased, camelCase flattened by lowercasing):
    // substring matching over-masks legit fields ("tokens"). Free-text shapes
    // (Bearer, key=value, JDBC) are covered by the sweep below instead.
    private static final java.util.Set<String> SECRET_KEYS = java.util.Set.of(
            "password", "passwd", "pwd", "authorization", "secret", "token",
            "api_key", "apikey", "api-key", "access_token", "accesstoken",
            "access-token", "client_secret", "clientsecret",
            "auth_token", "authtoken", "auth-token", "refresh_token", "refreshtoken",
            "id_token", "idtoken", "private_key", "privatekey");

    private static final Pattern BEARER_BASIC =
            Pattern.compile("\\b(Bearer|Basic)\\s+[A-Za-z0-9\\-._~+/=]+");
    private static final Pattern KEY_VALUE =
            // [^\s&]+ (not \S+): stop at & so one secret query param does not
            // eat the innocent params after it (?token=abc&x=1 keeps x=1).
            Pattern.compile("(?i)(password|passwd|secret|token)\\s*[:=]\\s*[^\\s&]+");
    // User kept, password masked: matches up to the LAST @ before a slash, so
    // passwords containing @ are still fully covered (best-effort; a password
    // containing / is out of reach and documented in the phase doc).
    private static final Pattern JDBC_CREDENTIALS =
            Pattern.compile("(://[^:/\\s]+:)[^/\\s]*(@)");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String redact(String input) {
        // Absent stays absent and blank stays blank: only real content is
        // worth parsing, and the fail-closed catch below must never turn
        // an empty field into a mask (Pullfrog verdict-side redaction calls
        // this on every extracted side, present or not).
        if (input == null || input.isBlank()) {
            return input;
        }
        try {
            return redactJsonAware(input);
        } catch (Throwable t) {
            return "***";
        }
    }

    private static String redactJsonAware(String input) {
        JsonNode tree;
        try {
            tree = MAPPER.readTree(input);
        } catch (Exception e) {
            return sweep(input);
        }
        boolean[] dirty = new boolean[1];
        maskNode(tree, dirty);
        String out = dirty[0] ? MAPPER.writeValueAsString(tree) : input;
        return sweep(out);
    }

    private static void maskNode(JsonNode node, boolean[] dirty) {
        if (node instanceof ObjectNode obj) {
            // Jackson 3: snapshot first — mutating via set() while iterating
            // the live properties() view throws.
            var entries = new ArrayList<>(obj.properties());
            for (var entry : entries) {
                String lower = entry.getKey().toLowerCase(Locale.ROOT);
                if (SECRET_KEYS.contains(lower)) {
                    obj.set(entry.getKey(), MAPPER.getNodeFactory().textNode("***"));
                    dirty[0] = true;
                } else {
                    String masked = maskNestedString(entry.getValue());
                    if (masked != null) {
                        obj.set(entry.getKey(), MAPPER.getNodeFactory().textNode(masked));
                        dirty[0] = true;
                    } else {
                        maskNode(entry.getValue(), dirty);
                    }
                }
            }
        } else if (node instanceof ArrayNode arr) {
            for (int i = 0; i < arr.size(); i++) {
                JsonNode child = arr.get(i);
                if (child.isObject() || child.isArray()) {
                    maskNode(child, dirty);
                } else {
                    String masked = maskNestedString(child);
                    if (masked != null) {
                        arr.set(i, MAPPER.getNodeFactory().textNode(masked));
                        dirty[0] = true;
                    }
                }
            }
        }
    }

    /**
     * Redacts a string value that is itself JSON (e.g. an escaped verdict body
     * inside an assertion array — the regex sweep cannot see through escaping).
     * Returns the redacted JSON string when masking changed anything, else null
     * (caller keeps the original bytes verbatim).
     */
    private static String maskNestedString(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asString();
        String trimmed = value.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            return null;
        }
        JsonNode inner;
        try {
            inner = MAPPER.readTree(value);
        } catch (Exception e) {
            return null;
        }
        if (!inner.isObject() && !inner.isArray()) {
            return null;
        }
        boolean[] dirty = new boolean[1];
        maskNode(inner, dirty);
        if (!dirty[0]) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(inner);
        } catch (Exception e) {
            return null;
        }
    }

    private static String sweep(String text) {
        String out = BEARER_BASIC.matcher(text).replaceAll("$1 ***");
        out = KEY_VALUE.matcher(out).replaceAll("$1=***");
        out = JDBC_CREDENTIALS.matcher(out).replaceAll("$1***$2");
        return out;
    }
}
