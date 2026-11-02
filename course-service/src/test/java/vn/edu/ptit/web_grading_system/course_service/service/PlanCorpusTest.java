package vn.edu.ptit.web_grading_system.course_service.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import vn.edu.ptit.web_grading_system.course_service.entity.StepType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Golden corpus cho AI agent dựng test-plan config (phiên bản 2).
 * Mỗi file trong {@code plan-corpus/} là cặp (prompt tiếng Việt, danh sách batch).
 * Test chạy offline: parse payload, rồi chạy mỗi step qua
 * {@link StepConfigValidator#validateAndSerialize} để đảm bảo config chuẩn
 * trước khi agent được phép đưa ra pending action.
 * <p>
 * Linter kiểm tra các quy tắc ràng buộc giữa các bước (define-before-use,
 * required/timeoutMs vị trí, DB_SCHEMA_CHECK runtime fields, ...).
 */
@DisplayName("Golden corpus v2: AI agent test-plan config")
class PlanCorpusTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([^}]+)}");
    private static final Set<String> DYNAMIC_VARS = Set.of(
            "$UUID", "$TIMESTAMP", "$FAKER_NAME", "$FAKER_BOOK_TITLE", "$FAKER_WORD", "$FAKER_EMAIL");

    private static final List<String> FILES = List.of(
            "01-crud-books-baseline.json",
            "02-hospital-fk-lifecycle.json",
            "03-auth-jwt-auto-headers.json",
            "04-assertion-matrix.json",
            "05-db-schema-query-migration.json",
            "06-negative-matrix.json",
            "07-data-loop-books.json",
            "08-order-state-machine.json");

    @Test
    @DisplayName("Tất cả case trong golden corpus v2 phải pass validation và linter")
    void allCorpusCasesPassValidationAndLint() throws Exception {
        Set<String> allKinds = new LinkedHashSet<>();
        Set<String> allMethods = new LinkedHashSet<>();
        Set<String> allTypes = new LinkedHashSet<>();
        Set<String> allDbKinds = new LinkedHashSet<>();
        Set<Integer> allStatuses = new LinkedHashSet<>();
        int totalSteps = 0;

        for (String file : FILES) {
            try (InputStream is = PlanCorpusTest.class.getResourceAsStream("/plan-corpus/" + file)) {
                assertNotNull(is, "Không tìm thấy corpus file: " + file);
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                JsonNode corpus = MAPPER.readTree(content);

                String prompt = corpus.path("prompt").asText("");
                JsonNode expected = corpus.path("expected");
                ArrayNode batches = (ArrayNode) corpus.path("batches");

                assertTrue(batches != null && batches.size() > 0, file + ": batches phải là mảng không rỗng");
                int expectedBatchCount = expected.path("batchCount").asInt(Integer.MAX_VALUE);
                assertEquals(expectedBatchCount, batches.size(),
                        file + ": batchCount kỳ vọng " + expectedBatchCount + " nhưng có " + batches.size());

                int expectedMaxSteps = expected.path("maxSteps").asInt(Integer.MAX_VALUE);
                int expectedTotalSteps = expected.path("totalSteps").asInt(Integer.MAX_VALUE);

                // Gộp các step từ toàn bộ batch theo thứ tự (dùng cho define-before-use & stepOrder).
                List<JsonNode> mergedSteps = new ArrayList<>();
                Set<Integer> sequenceOrders = new LinkedHashSet<>();
                int prevStepOrder = -1;

                for (int b = 0; b < batches.size(); b++) {
                    JsonNode batch = batches.get(b);
                    String tool = batch.path("tool").asText("");
                    JsonNode payload = batch.path("payload");
                    assertTrue(payload.isObject(), file + " batch[" + b + "]: payload phải là JSON object");

                    List<JsonNode> batchSteps;
                    if ("createTestQuestion".equals(tool)) {
                        JsonNode plan = payload.path("plan");
                        assertTrue(plan.isObject(), file + " batch[" + b + "]: plan phải là JSON object");
                        Integer seq = plan.path("sequenceOrder").asInt();
                        assertNotNull(seq, file + " batch[" + b + "]: plan.sequenceOrder bắt buộc");
                        assertTrue(sequenceOrders.add(seq),
                                file + " batch[" + b + "]: sequenceOrder " + seq + " bị trùng");
                        batchSteps = asList(payload.path("steps"));
                    } else if ("createTestStep".equals(tool)) {
                        batchSteps = List.of(payload);
                    } else {
                        fail(file + " batch[" + b + "]: tool không hợp lệ '" + tool + "'");
                        continue;
                    }

                    assertTrue(batchSteps.size() <= expectedMaxSteps,
                            file + " batch[" + b + "]: quá " + expectedMaxSteps + " bước (hiện " + batchSteps.size() + ")");

                    for (JsonNode step : batchSteps) {
                        mergedSteps.add(step);
                        int so = step.path("stepOrder").asInt(-1);
                        assertTrue(so >= 0, file + " step thiếu stepOrder");
                        assertTrue(so > prevStepOrder,
                                file + " stepOrder không tăng dần: " + prevStepOrder + " -> " + so);
                        prevStepOrder = so;
                    }
                }

                assertEquals(expectedTotalSteps, mergedSteps.size(),
                        file + ": totalSteps kỳ vọng " + expectedTotalSteps + " nhưng có " + mergedSteps.size());
                totalSteps += mergedSteps.size();

                // Linter + validator trên mỗi step (theo thứ tự merged).
                Set<String> definedVars = new LinkedHashSet<>();
                for (int i = 0; i < mergedSteps.size(); i++) {
                    JsonNode step = mergedSteps.get(i);
                    String stepType = step.path("stepType").asText();
                    JsonNode config = step.path("config");
                    assertTrue(config.isObject(), file + " step[" + i + "]: config phải là JSON object");

                    allTypes.add(stepType);
                    StepType type;
                    try {
                        type = StepType.valueOf(stepType);
                    } catch (IllegalArgumentException e) {
                        fail(file + " step[" + i + "]: stepType không hợp lệ '" + stepType + "'");
                        continue;
                    }

                    // Validator thẩm quyền.
                    assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(type, config),
                            file + " step[" + i + "] (" + stepType + ") không pass StepConfigValidator: config=" + config);

                    // required không được nằm trong config (là field cấp step).
                    assertFalse(config.hasNonNull("required"),
                            file + " step[" + i + "]: 'required' không được đặt trong config, hãy đặt ngoài config");

                    // Không được có expectedResult (rác).
                    assertFalse(step.hasNonNull("expectedResult"),
                            file + " step[" + i + "]: không được có expectedResult");
                    assertNoExpectedResultAtStepLevel(step, file, i);

                    // DB steps must have connection with required credentials
                    assertDbConnectionRequired(step, file, i);

                    if ("HTTP_REQUEST".equals(stepType)) {
                        allMethods.add(config.path("method").asText(""));
                        // expected_status không được null/rỗng (dùng cho HTTP).
                        JsonNode es = config.path("expected_status");
                        assertFalse(es.isMissingNode() || es.isNull() || es.asText().isBlank(),
                                file + " step[" + i + "]: expected_status không được null/rỗng");
                        if (config.hasNonNull("expected_status") && !config.path("expected_status").isNull()) {
                            allStatuses.add(config.path("expected_status").asInt());
                        }
                        collectAssertionKinds(config.path("assertions"), allKinds);

                        // definedVars trước bước này phải bao phủ mọi ${var} dùng trong config (trừ dynamic/index/data-key).
                        Set<String> usedVars = collectVars(config);
                        // auto_inject_headers harvested for subsequent steps; allow vars extracted by same step
                        Set<String> extractVarsThisStep = new LinkedHashSet<>();
                        collectExtractVars(config.path("extract"), extractVarsThisStep);
                        if (config.hasNonNull("auto_inject_headers")) {
                            // remove auto_inject vars for this-step check if they're extracted by this step
                            Set<String> aiVars = collectVars(config.path("auto_inject_headers"));
                            for (String av : aiVars) {
                                if (extractVarsThisStep.contains(av)) {
                                    usedVars.remove(av);
                                }
                            }
                        }
                        Set<String> dataKeys = collectDataKeys(config.path("data"));
                        for (String v : usedVars) {
                            if (DYNAMIC_VARS.contains(v) || "index".equals(v) || dataKeys.contains(v)) {
                                continue;
                            }
                            assertTrue(definedVars.contains(v) || extractVarsThisStep.contains(v),
                                    file + " step[" + i + "]: dùng biến '${" + v + "}' trước khi được extract");
                        }

                        // any_of không được chứa status.
                        assertNoStatusInAnyOf(config.path("assertions"), file, i);

                        // contains / contains_item / matches_regex không được chứa ${extractVar}.
                        assertNoVarInLiteralAssertions(config.path("assertions"), definedVars, file, i);

                        // Thu thập biến extract sau khi validate (bước này định nghĩa cho bước sau).
                        collectExtractVars(config.path("extract"), definedVars);
                    }

                    if ("DB_SCHEMA_CHECK".equals(stepType)) {
                        collectDbCheckKinds(config.path("checks"), allDbKinds);
                        JsonNode checks = config.path("checks");
                        if (checks.isArray()) {
                            for (JsonNode chk : checks) {
                                String kind = chk.path("kind").asText("");
                                if ("PRIMARY_KEY".equals(kind) || "INDEX_EXISTS".equals(kind)) {
                                    assertTrue(chk.hasNonNull("table_name"),
                                            file + " step[" + i + "]: " + kind + " phải có table_name");
                                }
                                if ("COLUMN_EXISTS".equals(kind)) {
                                    assertTrue(chk.hasNonNull("data_type"),
                                            file + " step[" + i + "]: COLUMN_EXISTS phải có data_type");
                                }
                            }
                        }
                    }

                    if ("DB_QUERY".equals(stepType)) {
                        JsonNode expectedNode = config.path("expected");
                        assertTrue(expectedNode.hasNonNull("row_count") || expectedNode.hasNonNull("columns"),
                                file + " step[" + i + "]: DB_QUERY phải có expected.row_count hoặc expected.columns");
                    }

                    // required=false steps.
                    if (step.hasNonNull("required") && Boolean.FALSE.equals(step.path("required").asBoolean(true))) {
                        allStatuses.add(-1); // marker cho required=false
                    }
                }
            }
        }

        // Meta-coverage: corpus phải phủ đủ mọi chủ thể quan trọng.
        assertTrue(allKinds.containsAll(Set.of(
                "status", "contains", "json_path", "body_equals", "body_structure", "field_equals",
                "array_length", "contains_item", "json_path_equals", "greater_than", "less_than", "matches_regex", "any_of")),
                "Thiếu assertion kind: " + allKinds);
        assertTrue(allMethods.containsAll(Set.of("POST", "GET", "PUT", "PATCH", "DELETE")),
                "Thiếu HTTP method: " + allMethods);
        assertEquals(Set.of("HTTP_REQUEST", "DB_QUERY", "DB_SCHEMA_CHECK", "DB_MIGRATION"), allTypes,
                "Step type không khớp: " + allTypes);
        assertTrue(allDbKinds.containsAll(Set.of("TABLE_EXISTS", "COLUMN_EXISTS", "PRIMARY_KEY", "INDEX_EXISTS")),
                "Thiếu DB check kind: " + allDbKinds);
        assertTrue(allStatuses.containsAll(Set.of(200, 201, 204, 400, 401, 403, 404, 409)),
                "Thiếu HTTP status: " + allStatuses);

        System.out.println("PASS corpus v2 | files=" + FILES.size()
                + " | totalSteps=" + totalSteps
                + " | kinds=" + allKinds
                + " | methods=" + allMethods
                + " | types=" + allTypes
                + " | dbKinds=" + allDbKinds
                + " | statuses=" + allStatuses);
    }

    /* ---------- helpers ---------- */

    private List<JsonNode> asList(JsonNode node) {
        if (node.isArray()) {
            List<JsonNode> list = new ArrayList<>();
            for (JsonNode n : node) list.add(n);
            return list;
        }
        return List.of();
    }

    private void collectAssertionKinds(JsonNode assertions, Set<String> sink) {
        if (assertions == null || !assertions.isArray()) return;
        for (JsonNode a : assertions) {
            String kind = a.path("kind").asText("");
            if (!kind.isEmpty()) sink.add(kind);
            if ("any_of".equals(kind)) collectAssertionKinds(a.path("assertions"), sink);
        }
    }

    private void collectDbCheckKinds(JsonNode checks, Set<String> sink) {
        if (checks == null || !checks.isArray()) return;
        for (JsonNode c : checks) {
            String kind = c.path("kind").asText("");
            if (!kind.isEmpty()) sink.add(kind);
        }
    }

    private void collectExtractVars(JsonNode extract, Set<String> sink) {
        if (extract == null || !extract.isArray()) return;
        for (JsonNode ex : extract) {
            String name = ex.path("name").asText("");
            if (!name.isEmpty()) sink.add(name);
        }
    }

    /** Thu thập tất cả tên biến ${...} xuất hiện trong một node (bao gồm nested). */
    private Set<String> collectVars(JsonNode node) {
        Set<String> vars = new LinkedHashSet<>();
        if (node == null) return vars;
        if (node.isTextual()) {
            Matcher m = VAR_PATTERN.matcher(node.asText());
            while (m.find()) vars.add(m.group(1));
        } else if (node.isObject()) {
            for (var e : node.properties()) {
                vars.addAll(collectVars(e.getValue()));
            }
        } else if (node.isArray()) {
            for (JsonNode e : node) vars.addAll(collectVars(e));
        }
        return vars;
    }

    private Set<String> collectDataKeys(JsonNode data) {
        Set<String> keys = new LinkedHashSet<>();
        if (data == null || !data.isArray()) return keys;
        for (JsonNode entry : data) {
            if (entry.isObject()) {
                for (var e : entry.properties()) keys.add(e.getKey());
            }
        }
        return keys;
    }

    private void assertNoStatusInAnyOf(JsonNode assertions, String file, int idx) {
        if (assertions == null || !assertions.isArray()) return;
        for (JsonNode a : assertions) {
            if ("any_of".equals(a.path("kind").asText(""))) {
                JsonNode subs = a.path("assertions");
                if (subs.isArray()) {
                    for (JsonNode s : subs) {
                        assertNotEquals("status", s.path("kind").asText(""),
                                file + " step[" + idx + "]: any_of không được lồng assertion kind 'status'");
                    }
                }
            }
            assertNoStatusInAnyOf(a.path("assertions"), file, idx);
        }
    }

    private void assertNoVarInLiteralAssertions(JsonNode assertions, Set<String> definedVars,
                                                String file, int idx) {
        if (assertions == null || !assertions.isArray()) return;
        for (JsonNode a : assertions) {
            String kind = a.path("kind").asText("");
            if ("contains".equals(kind)) {
                String text = a.path("text").asText("");
                assertNoVar(text, definedVars, file, idx, "contains.text");
            } else if ("contains_item".equals(kind)) {
                String eq = a.path("equals").asText("");
                assertNoVar(eq, definedVars, file, idx, "contains_item.equals");
            } else if ("matches_regex".equals(kind)) {
                String pat = a.path("pattern").asText("");
                assertNoVar(pat, definedVars, file, idx, "matches_regex.pattern");
            }
            assertNoVarInLiteralAssertions(a.path("assertions"), definedVars, file, idx);
        }
    }

    private void assertNoVar(String literal, Set<String> definedVars, String file, int idx, String field) {
        if (literal.isEmpty()) return;
        Matcher m = VAR_PATTERN.matcher(literal);
        while (m.find()) {
            String v = m.group(1);
            assertFalse(definedVars.contains(v),
                    file + " step[" + idx + "]: " + field + " không được dùng biến extract '${" + v + "}' (gia trị literal)");
        }
    }

    private void assertDbConnectionRequired(JsonNode step, String file, int idx) {
        String type = step.path("stepType").asText("");
        if (!type.startsWith("DB_")) return;
        JsonNode cfg = step.path("config");
        assertTrue(cfg.isObject(), file + " step[" + idx + "]: config phải là object");
        JsonNode conn = cfg.path("connection");
        assertTrue(conn.isObject(), file + " step[" + idx + "]: DB step bắt buộc phải có config.connection");
        for (String f : List.of("database", "username", "password")) {
            String v = conn.path(f).asText("");
            assertFalse(v.isBlank(), file + " step[" + idx + "]: connection." + f + " bắt buộc");
        }
    }

    private void assertNoExpectedResultAtStepLevel(JsonNode step, String file, int idx) {
        assertFalse(step.has("expectedResult") && !step.path("expectedResult").isNull(),
                file + " step[" + idx + "]: không dùng expectedResult cấp step, dùng config.expected");
    }

    private void assertAutoInjectPlacement(List<JsonNode> steps, String file) {
        for (int i = 0; i < steps.size(); i++) {
            JsonNode cfg = steps.get(i).path("config");
            if (cfg.hasNonNull("auto_inject_headers") && cfg.path("auto_inject_headers").isObject()) {
                // declared at step i - effective for i+1 onward; allowed to be declared anywhere but better at earlier step
                // no hard fail, just ensure it's object - runtime enforces placement
            }
        }
        // basic check: negative auth tests should shield
        for (int i = 0; i < steps.size(); i++) {
            String name = steps.get(i).path("name").asText("").toLowerCase();
            if ((name.contains("401") || name.contains("403")) && steps.get(i).path("stepType").asText("").equals("HTTP_REQUEST")) {
                JsonNode cfg = steps.get(i).path("config");
                if (name.contains("401") && cfg.path("auto_inject_headers").isObject()) {
                    // 401 without token should typically not rely on auto-inject; allow but corpus should be explicit
                }
            }
        }
    }
}
