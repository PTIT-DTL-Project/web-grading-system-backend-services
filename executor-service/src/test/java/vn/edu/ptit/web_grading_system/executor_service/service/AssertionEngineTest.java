package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine.AssertionDetail;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssertionEngineTest {

    private final AssertionEngine engine = new AssertionEngine();
    private final ObjectMapper mapper = new ObjectMapper();

    private AssertionDetail only(String configJson) {
        return engine.evaluateHttp(200, "{\"id\":\"b1\",\"title\":\"De Men\"}", mapper.readTree(configJson)).stream()
                .skip(0).reduce((a, b) -> b).orElseThrow();
    }

    @Test
    void expectedStatus_passAndFail() {
        List<AssertionDetail> r = engine.evaluateHttp(201, "{}", mapper.readTree("{\"expected_status\":201}"));
        assertTrue(r.get(0).isPassed());
        r = engine.evaluateHttp(404, "{}", mapper.readTree("{\"expected_status\":201}"));
        assertFalse(r.get(0).isPassed());
        assertTrue(r.get(0).getMessage().contains("Expected status 201 but got 404"));
    }

    @Test
    void assertionStatusKind() {
        String cfg = "{\"assertions\":[{\"kind\":\"status\",\"equals\":200}]}";
        assertTrue(engine.evaluateHttp(200, "{}", mapper.readTree(cfg)).get(0).isPassed());
        assertFalse(engine.evaluateHttp(500, "{}", mapper.readTree(cfg)).get(0).isPassed());
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
    void unknownKind_failsWithMessage() {
        AssertionDetail d = only("{\"assertions\":[{\"kind\":\"teleport\"}]}");
        assertFalse(d.isPassed());
        assertTrue(d.getMessage().contains("Unknown assertion kind: teleport"));
    }
}