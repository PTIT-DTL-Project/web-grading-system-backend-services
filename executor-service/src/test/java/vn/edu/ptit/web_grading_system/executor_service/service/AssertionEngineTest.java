package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine.AssertionDetail;
import vn.edu.ptit.web_grading_system.executor_service.service.VariableContext;

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
}