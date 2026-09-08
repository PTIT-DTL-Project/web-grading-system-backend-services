package vn.edu.ptit.web_grading_system.course_service.service;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.ptit.web_grading_system.course_service.entities.StepType;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StepConfigValidatorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private ObjectNode node(String json) {
        try {
            return (ObjectNode) mapper.readTree(json);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ---------- HTTP_REQUEST ----------

    @Test
    void http_validFullConfig_passes() {
        String json = """
            {
              "method": "POST",
              "path": "/api/v1/books",
              "headers": {"Content-Type": "application/json", "Authorization": "Bearer ${token}"},
              "body": {"title": "De Men"},
              "query_params": {"year": 1941},
              "expected_status": 201,
              "assertions": [
                {"kind": "status", "equals": "201"},
                {"kind": "json_path", "path": "$.id", "exists": true},
                {"kind": "contains", "text": "De Men"}
              ],
              "extract": [{"name": "bookId", "from": "response_body", "expression": "$.id"}]
            }""";
        String out = StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, node(json));
        assertTrue(out.contains("\"method\":\"POST\""));
    }

    @ParameterizedTest
    @ValueSource(strings = {"FETCH", "get all", ""})
    void http_unknownOrBlankMethod_fails(String method) {
        ObjectNode n = node("{\"method\":\"" + method + "\",\"path\":\"/x\"}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("method"));
    }

    @Test
    void http_pathWithoutLeadingSlash_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"api/v1/books\"}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("must start with '/'"));
    }

    @Test
    void http_headersAsArray_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"headers\":[]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("headers must be an object"));
    }

    @Test
    void http_expectedStatusOutOfRange_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"expected_status\":99}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("100–599"));
    }

    @Test
    void http_assertionUnknownKind_fails() {
        ObjectNode n = node("""
            {"method":"GET","path":"/x","assertions":[{"kind":"magic","equals":"1"}]}""");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("unknown kind 'magic'"));
    }

    @Test
    void http_extractMissingExpression_fails() {
        ObjectNode n = node("""
            {"method":"GET","path":"/x","extract":[{"name":"bookId","from":"response_body"}]}""");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("'expression'"));
    }

    // ---------- DB_QUERY ----------

    @Test
    void dbQuery_validWithExpected_passes() {
        ObjectNode n = node("""
            {"query":"SELECT id, title FROM books WHERE id = ${bookId}",
             "expected":{"row_count":1,"columns":["id","title"],"sample":{"title":"De Men"}}}""");
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, n));
    }

    @Test
    void dbQuery_emptyQuery_fails() {
        ObjectNode n = node("{\"query\":\"\"}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, n));
        assertTrue(e.getMessage().contains("'query'"));
    }

    @Test
    void dbQuery_expectedAsArray_fails() {
        ObjectNode n = node("{\"query\":\"SELECT 1\",\"expected\":[]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, n));
        assertTrue(e.getMessage().contains("expected must be an object"));
    }

    // ---------- DB_SCHEMA_CHECK ----------

    @Test
    void schemaCheck_validKinds_pass() {
        ObjectNode n = node("""
            {"checks":[
              {"kind":"TABLE_EXISTS","table_name":"books"},
              {"kind":"COLUMN_EXISTS","table_name":"books","column_name":"title","data_type":"VARCHAR"},
              {"kind":"INDEX_EXISTS","index_name":"idx_books_title"},
              {"kind":"PRIMARY_KEY","column":"id"}
            ]}""");
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.DB_SCHEMA_CHECK, n));
    }

    @Test
    void schemaCheck_columnExistsWithoutColumnName_fails() {
        ObjectNode n = node("{\"checks\":[{\"kind\":\"COLUMN_EXISTS\",\"table_name\":\"books\"}]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_SCHEMA_CHECK, n));
        assertTrue(e.getMessage().contains("'column_name'"));
    }

    @Test
    void schemaCheck_emptyChecks_fails() {
        ObjectNode n = node("{\"checks\":[]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_SCHEMA_CHECK, n));
        assertTrue(e.getMessage().contains("non-empty"));
    }

    // ---------- DB_MIGRATION ----------

    @Test
    void migration_validStatements_pass() {
        ObjectNode n = node("""
            {"statements":["INSERT INTO books VALUES ('1','A',2000)","UPDATE books SET year=2001"]}""");
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.DB_MIGRATION, n));
    }

    @Test
    void migration_blankStatement_fails() {
        ObjectNode n = node("{\"statements\":[\"INSERT INTO books VALUES ('1','A')\",\"   \"]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_MIGRATION, n));
        assertTrue(e.getMessage().contains("non-empty strings"));
    }

    // ---------- EXTRACT ----------

    @Test
    void extract_valueForm_and_fromForm_pass() {
        ObjectNode n = node("""
            {"variables":[
               {"name":"pageSize","value":"10"},
               {"name":"firstBookId","from":"step_1","expression":"$.id"}
            ]}""");
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.EXTRACT, n));
    }

    @Test
    void extract_variableWithoutValueOrFrom_fails() {
        ObjectNode n = node("{\"variables\":[{\"name\":\"orphan\"}]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.EXTRACT, n));
        assertTrue(e.getMessage().contains("'value' or"));
    }

    // ---------- DELAY ----------

    @Test
    void delay_positive_passes() {
        assertDoesNotThrow(() ->
                StepConfigValidator.validateAndSerialize(StepType.DELAY, node("{\"duration_ms\":5000}")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-5"})
    void delay_nonPositive_fails(String v) {
        ObjectNode n = node("{\"duration_ms\":" + v + "}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DELAY, n));
        assertTrue(e.getMessage().contains("positive integer"));
    }

    // ---------- generic ----------

    @Test
    void config_notAnObject_fails_forEveryType() throws Exception {
        var arr = mapper.readTree("[]");
        Stream.of(StepType.values()).forEach(type -> {
            var e = assertThrows(IllegalArgumentException.class,
                    () -> StepConfigValidator.validateAndSerialize(type, arr));
            assertTrue(e.getMessage().contains("JSON object"), type.name());
        });
    }
}
