package vn.edu.ptit.web_grading_system.executor_service.service.scoring;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.AssertionEngine.AssertionDetail;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.VariableContext;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionEngineTest {

    private final AssertionEngine engine = new AssertionEngine();
    private final ObjectMapper mapper = new ObjectMapper();
    private VariableContext vars = new VariableContext();

    private AssertionDetail only(String configJson) {
        return engine.evaluateHttp(200, "{\"id\":\"b1\",\"title\":\"De Men\"}",
                mapper.readTree(configJson), vars).stream()
                .skip(0).reduce((a, b) -> b).orElseThrow();
    }

    @Test
    void expectedStatus_passAndFail() {
        List<AssertionDetail> r = engine.evaluateHttp(201, "{}", mapper.readTree("{\"expected_status\":201}"), vars);
        assertTrue(r.get(0).isPassed());
        r = engine.evaluateHttp(404, "{}", mapper.readTree("{\"expected_status\":201}"), vars);
        assertFalse(r.get(0).isPassed());
        assertTrue(r.get(0).getMessage().contains("Expected status 201 but got 404"));
    }

    @Test
    void assertionStatusKind() {
        String cfg = "{\"assertions\":[{\"kind\":\"status\",\"equals\":200}]}";
        assertTrue(engine.evaluateHttp(200, "{}", mapper.readTree(cfg), vars).get(0).isPassed());
        assertFalse(engine.evaluateHttp(500, "{}", mapper.readTree(cfg), vars).get(0).isPassed());
    }

    @Test
    void containsText() {
        String cfg = "{\"assertions\":[{\"kind\":\"contains\",\"text\":\"De Men\"}]}";
        assertTrue(only(cfg).isPassed());
        String cfgFail = "{\"assertions\":[{\"kind\":\"contains\",\"text\":\"Kafka\"}]}";
        assertFalse(only(cfgFail).isPassed());
    }

    @Test
    void jsonPathExistsAndNotExists() {
        assertTrue(only("{\"assertions\":[{\"kind\":\"json_path\",\"path\":\"$.id\"}]}").isPassed());
        assertFalse(only("{\"assertions\":[{\"kind\":\"json_path\",\"path\":\"$.missing\"}]}").isPassed());
        // exists=false inverted
        assertTrue(only("{\"assertions\":[{\"kind\":\"json_path\",\"path\":\"$.missing\",\"exists\":false}]}").isPassed());
    }

    @Test
    void bodyEquals_strictJson() {
        assertTrue(only("{\"assertions\":[{\"kind\":\"body_equals\",\"json\":{\"id\":\"b1\",\"title\":\"De Men\"}}]}").isPassed());
        assertFalse(only("{\"assertions\":[{\"kind\":\"body_equals\",\"json\":{\"id\":\"b1\",\"title\":\"Other\"}}]}").isPassed());
    }

    @Test
    void bodyStructure_unorderedKeys_ignoresValues() {
        // reorder + different values -> still passes
        assertTrue(only("{\"assertions\":[{\"kind\":\"body_structure\",\"json\":{\"title\":\"\",\"id\":\"\"}}]}").isPassed());
        assertFalse(only("{\"assertions\":[{\"kind\":\"body_structure\",\"json\":{\"id\":\"\",\"extra\":\"\"}}]}").isPassed());
    }

    @Test
    void fieldEquals_passAndFail() {
        assertTrue(only("{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.id\",\"equals\":\"b1\"}]}").isPassed());
        assertFalse(only("{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.id\",\"equals\":\"b2\"}]}").isPassed());
    }

    @Test
    void fieldEquals_withVariable() {
        vars.put("bookId", "b1");
        assertTrue(only("{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.id\",\"equals\":\"${bookId}\"}]}").isPassed());
        vars.put("bookId", "wrong");
        assertFalse(only("{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.id\",\"equals\":\"${bookId}\"}]}").isPassed());
        vars = new VariableContext();
    }

    @Test
    void fieldEquals_missingField() {
        assertFalse(only("{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.missing\",\"equals\":\"x\"}]}").isPassed());
    }

    @Test
    void fieldEquals_nullBody() {
        assertFalse(engine.evaluateHttp(200, null, mapper.readTree(
                "{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.id\",\"equals\":\"b1\"}]}"),
                vars).get(0).isPassed());
    }

    @Test
    void fieldEquals_numericComparison() {
        // 1.0 should equal 1 numerically via BigDecimal
        assertTrue(engine.evaluateHttp(200, "{\"score\":1.0}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.score\",\"equals\":\"1\"}]}"),
                vars).get(0).isPassed());
        // 1.5 should not equal 1
        assertFalse(engine.evaluateHttp(200, "{\"score\":1.5}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.score\",\"equals\":\"1\"}]}"),
                vars).get(0).isPassed());
    }

    @Test
    void fieldEquals_stringNumericNoMatch() {
        // String "00123" should NOT equal "123" — exact string equality, not numeric
        // Review: 2026-09-20, Pullfrog PR #16.
        assertFalse(engine.evaluateHttp(200, "{\"id\":\"00123\"}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.id\",\"equals\":\"123\"}]}"),
                vars).get(0).isPassed());
    }

    @Test
    void fieldEquals_stringNumberExactMatch() {
        // String "123" should equal "123" — exact string equality holds
        // Review: 2026-09-20, Pullfrog PR #16.
        assertTrue(engine.evaluateHttp(200, "{\"id\":\"123\"}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.id\",\"equals\":\"123\"}]}"),
                vars).get(0).isPassed());
    }

    @Test
    void fieldEquals_missingFieldExplicitNull() {
        // Response body has explicit null at $.id
        // Expected "null" should NOT pass — missing/null fields always fail
        assertFalse(engine.evaluateHttp(200, "{\"id\":null}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.id\",\"equals\":\"null\"}]}"),
                vars).get(0).isPassed());
    }

    @Test
    void fieldEquals_messageContainsMismatch() {
        AssertionDetail d = only("{\"assertions\":[{\"kind\":\"field_equals\",\"path\":\"$.title\",\"equals\":\"Other\"}]}");
        assertFalse(d.isPassed());
        assertTrue(d.getMessage().contains("Expected: Other"));
    }

    @Test
    void unknownKind_failsWithMessage() {
        AssertionDetail d = only("{\"assertions\":[{\"kind\":\"teleport\"}]}");
        assertFalse(d.isPassed());
        assertTrue(d.getMessage().contains("Unknown assertion kind: teleport"));
    }

    // ---------- Phase 1: new assertion kinds ----------

    @Test
    void arrayLength_passAndFail() {
        // body is a JSON array with 3 elements
        assertTrue(engine.evaluateHttp(200, "[1,2,3]", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"array_length\",\"path\":\"$\",\"equals\":3}]}"), vars).get(0).isPassed());
        assertFalse(engine.evaluateHttp(200, "[1,2,3]", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"array_length\",\"path\":\"$\",\"equals\":5}]}"), vars).get(0).isPassed());
        // nested array path
        assertTrue(engine.evaluateHttp(200, "{\"items\":[1,2,3,4]}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"array_length\",\"path\":\"$.items\",\"equals\":4}]}"), vars).get(0).isPassed());
        // missing path -> length 0, equals 0 passes
        assertTrue(engine.evaluateHttp(200, "{\"a\":1}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"array_length\",\"path\":\"$.missing\",\"equals\":0}]}"), vars).get(0).isPassed());
        // missing path -> length 0, equals 1 fails
        assertFalse(engine.evaluateHttp(200, "{\"a\":1}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"array_length\",\"path\":\"$.missing\",\"equals\":1}]}"), vars).get(0).isPassed());
    }

    @Test
    void containsItem_passAndFail() {
        assertTrue(engine.evaluateHttp(200, "[\"De Men\",\"Kafka\"]", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"contains_item\",\"path\":\"$\",\"equals\":\"De Men\"}]}"), vars).get(0).isPassed());
        assertFalse(engine.evaluateHttp(200, "[\"A\",\"B\"]", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"contains_item\",\"path\":\"$\",\"equals\":\"Kafka\"}]}"), vars).get(0).isPassed());
        // array of objects: check by string value of first field
        assertTrue(engine.evaluateHttp(200, "{\"items\":[{\"title\":\"A\"},{\"title\":\"B\"}]}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"contains_item\",\"path\":\"$.items[*].title\",\"equals\":\"A\"}]}"), vars).get(0).isPassed());
    }

    @Test
    void jsonPathEquals_passAndFail() {
        assertTrue(only("{\"assertions\":[{\"kind\":\"json_path_equals\",\"path\":\"$.id\",\"equals\":\"b1\"}]}").isPassed());
        assertFalse(only("{\"assertions\":[{\"kind\":\"json_path_equals\",\"path\":\"$.id\",\"equals\":\"b2\"}]}").isPassed());
        // numeric comparison via BigDecimal
        assertTrue(engine.evaluateHttp(200, "{\"price\":100}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"json_path_equals\",\"path\":\"$.price\",\"equals\":\"100\"}]}"), vars).get(0).isPassed());
        // with variable substitution
        vars.put("expectedId", "b1");
        assertTrue(only("{\"assertions\":[{\"kind\":\"json_path_equals\",\"path\":\"$.id\",\"equals\":\"${expectedId}\"}]}").isPassed());
        vars = new VariableContext();
    }

    @Test
    void greaterThanAndLessThan() {
        // greater_than
        assertTrue(engine.evaluateHttp(200, "{\"price\":150}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"greater_than\",\"path\":\"$.price\",\"value\":\"100\"}]}"), vars).get(0).isPassed());
        assertFalse(engine.evaluateHttp(200, "{\"price\":50}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"greater_than\",\"path\":\"$.price\",\"value\":\"100\"}]}"), vars).get(0).isPassed());
        // less_than
        assertTrue(engine.evaluateHttp(200, "{\"price\":50}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"less_than\",\"path\":\"$.price\",\"value\":\"100\"}]}"), vars).get(0).isPassed());
        assertFalse(engine.evaluateHttp(200, "{\"price\":150}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"less_than\",\"path\":\"$.price\",\"value\":\"100\"}]}"), vars).get(0).isPassed());
        // non-numeric value -> fail
        assertFalse(engine.evaluateHttp(200, "{\"price\":\"expensive\"}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"greater_than\",\"path\":\"$.price\",\"value\":\"100\"}]}"), vars).get(0).isPassed());
    }

    @Test
    void matchesRegex_passAndFail() {
        String pattern = "^ABC-\\\\d+$";
        assertTrue(engine.evaluateHttp(200, "{\"code\":\"ABC-123\"}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"matches_regex\",\"path\":\"$.code\",\"pattern\":\"" + pattern + "\"}]}"), vars).get(0).isPassed());
        assertFalse(engine.evaluateHttp(200, "{\"code\":\"XYZ-123\"}", mapper.readTree(
                "{\"assertions\":[{\"kind\":\"matches_regex\",\"path\":\"$.code\",\"pattern\":\"" + pattern + "\"}]}"), vars).get(0).isPassed());
    }

    @Test
    void anyOf_passWhenAnySubPasses() {
        // ISO date OR simple date format (no 'status' inside any_of — status has no HTTP context here)
        String datePattern1 = "^\\\\d{4}-\\\\d{2}-\\\\d{2}$";
        String datePattern2 = "^\\\\d{2}/\\\\d{2}/\\\\d{4}$";
        String cfg = "{\"assertions\":[{\"kind\":\"any_of\",\"assertions\":[" +
                "{\"kind\":\"matches_regex\",\"path\":\"$.date\",\"pattern\":\"" + datePattern1 + "\"}," +
                "{\"kind\":\"matches_regex\",\"path\":\"$.date\",\"pattern\":\"" + datePattern2 + "\"}]}]}";
        assertTrue(engine.evaluateHttp(200, "{\"date\":\"2026-10-28\"}", mapper.readTree(cfg), vars).get(0).isPassed());
        assertTrue(engine.evaluateHttp(200, "{\"date\":\"28/10/2026\"}", mapper.readTree(cfg), vars).get(0).isPassed());
        assertFalse(engine.evaluateHttp(200, "{\"date\":\"not-a-date\"}", mapper.readTree(cfg), vars).get(0).isPassed());
    }
}