package vn.edu.ptit.web_grading_system.course_service.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import vn.edu.ptit.web_grading_system.course_service.entity.StepType;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    // ---------- connection block (multi-DBMS) ----------

    @ParameterizedTest
    @ValueSource(strings = {"postgres", "mysql", "mariadb", "MYSQL", "Postgres"})
    void connection_knownDbType_passes(String dbType) {
        ObjectNode n = node("""
            {"connection":{"db_type":"%s","db_service":"db","database":"appdb",
                           "username":"root","password":"root"},
             "query":"SELECT 1"}""".formatted(dbType));
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, n));
    }

    @Test
    void connection_absent_passes_defaultsToPostgres() {
        ObjectNode n = node("{\"query\":\"SELECT 1\"}");
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, n));
    }

    @Test
    void connection_unknownDbType_failsNamingAllowedValues() {
        ObjectNode n = node("""
            {"connection":{"db_type":"oracle"},
             "query":"SELECT 1"}""");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, n));
        assertTrue(e.getMessage().contains("db_type"));
        // Deterministic — sorted engine set.
        assertTrue(e.getMessage().contains("mariadb, mysql, postgres"),
                "message was: " + e.getMessage());
    }

    @Test
    void connection_notAnObject_fails() {
        ObjectNode n = node("{\"connection\":[],\"query\":\"SELECT 1\"}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, n));
        assertTrue(e.getMessage().contains("connection must be an object"));
    }

    @Test
    void connection_unknownDbType_failsOnSchemaCheckAndMigrationToo() {
        ObjectNode schema = node("""
            {"connection":{"db_type":"oracle"},
             "checks":[{"kind":"TABLE_EXISTS","table_name":"books"}]}""");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_SCHEMA_CHECK, schema));
        assertTrue(e.getMessage().contains("db_type"));
        ObjectNode migration = node("""
            {"connection":{"db_type":"oracle"},
             "statements":["INSERT INTO books VALUES (1)"]}""");
        var e2 = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_MIGRATION, migration));
        assertTrue(e2.getMessage().contains("db_type"));
    }

    @Test
    void connection_databaseInjection_fails() {
        ObjectNode n = node("""
            {"connection":{"db_type":"mysql","database":"appdb?allowLoadLocalInfile=true",
                           "username":"root","password":"root"},
             "query":"SELECT 1"}""");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, n));
        assertTrue(e.getMessage().contains("database"));
        assertTrue(e.getMessage().contains("[A-Za-z0-9_$]+"));
    }

    @Test
    void connection_dbPortRange_fails() {
        ObjectNode zero = node("""
            {"connection":{"db_type":"mysql","db_port":0,"database":"appdb",
                           "username":"root","password":"root"},
             "query":"SELECT 1"}""");
        var e1 = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, zero));
        assertTrue(e1.getMessage().contains("db_port"));
        ObjectNode over = node("""
            {"connection":{"db_type":"mysql","db_port":70000,"database":"appdb",
                           "username":"root","password":"root"},
             "query":"SELECT 1"}""");
        var e2 = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, over));
        assertTrue(e2.getMessage().contains("db_port"));
        // Valid port still passes.
        ObjectNode ok = node("""
            {"connection":{"db_type":"mysql","db_port":3306,"database":"appdb",
                           "username":"root","password":"root"},
             "query":"SELECT 1"}""");
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.DB_QUERY, ok));
    }

    // ---------- EXTRACT / DELAY (rejected standalone since phase A3) ----------

    @Test
    void extract_standalone_rejected() {
        ObjectNode n = node("""
            {"variables":[
               {"name":"pageSize","value":"10"},
               {"name":"firstBookId","from":"step_1","expression":"$.id"}
            ]}""");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.EXTRACT, n));
        assertTrue(e.getMessage().contains("extract[]"));
    }

    @Test
    void delay_standalone_rejected() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.DELAY,
                        node("{\"duration_ms\":5000}")));
        assertTrue(e.getMessage().contains("not supported"));
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

    // ---------- Phase 1: new assertion kinds ----------

    @Test
    void http_newAssertionKinds_pass() {
        String regex = "^ABC-\\\\d+$";
        String json = "{\"method\":\"GET\",\"path\":\"/x\"," +
                "\"assertions\":[" +
                "{\"kind\":\"array_length\",\"path\":\"$\",\"equals\":3}," +
                "{\"kind\":\"contains_item\",\"path\":\"$[*].title\",\"equals\":\"De Men\"}," +
                "{\"kind\":\"json_path_equals\",\"path\":\"$.id\",\"equals\":\"b1\"}," +
                "{\"kind\":\"greater_than\",\"path\":\"$.price\",\"value\":\"100\"}," +
                "{\"kind\":\"less_than\",\"path\":\"$.price\",\"value\":\"200\"}," +
                "{\"kind\":\"matches_regex\",\"path\":\"$.code\",\"pattern\":\"" + regex + "\"}," +
                "{\"kind\":\"any_of\",\"assertions\":[" +
                "{\"kind\":\"status\",\"equals\":200}," +
                "{\"kind\":\"json_path\",\"path\":\"$.id\"}" +
                "]}" +
                "]}";
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, node(json)));
    }

    @Test
    void http_arrayLengthMissingFields_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"assertions\":[{\"kind\":\"array_length\"}]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("'path'") || e.getMessage().contains("'equals'"));

        ObjectNode n2 = node("{\"method\":\"GET\",\"path\":\"/x\",\"assertions\":[{\"kind\":\"array_length\",\"path\":\"$\"}]}");
        var e2 = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n2));
        assertTrue(e2.getMessage().contains("'equals'"));
    }

    @Test
    void http_greaterThanMissingValue_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"assertions\":[{\"kind\":\"greater_than\",\"path\":\"$.price\"}]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("'value'"));
    }

    @Test
    void http_matchesRegexMissingPattern_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"assertions\":[{\"kind\":\"matches_regex\",\"path\":\"$.code\"}]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("'pattern'"));
    }

    @Test
    void http_anyOfMissingSubAssertions_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"assertions\":[{\"kind\":\"any_of\"}]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("'assertions'"));

        ObjectNode n2 = node("{\"method\":\"GET\",\"path\":\"/x\",\"assertions\":[{\"kind\":\"any_of\",\"assertions\":[]}]}");
        var e2 = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n2));
        assertTrue(e2.getMessage().contains("non-empty"));
    }

    @Test
    void http_extractWithFallback_passes() {
        String json = """
            {"method":"GET","path":"/x",
             "extract":[{"name":"bookId","from":"response_body","expression":"$.missing","fallback":"NOT_FOUND"}]}""";
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, node(json)));
    }

    @Test
    void http_extractWithAsJson_passes() {
        String json = """
            {"method":"GET","path":"/x",
             "extract":[{"name":"whole","from":"response_body","expression":"$","as":"json"}]}""";
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, node(json)));
    }

    // ---------- Phase 3: data[] loop + auto_inject_headers ----------

    @Test
    void http_dataLoop_validUpToTen_passes() {
        StringBuilder sb = new StringBuilder(
                "{\"method\":\"POST\",\"path\":\"/api/v1/books\",\"data\":[");
        for (int i = 0; i < 10; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"title\":\"Sach \",\"year\":").append(1940 + i).append("}");
        }
        sb.append("],\"body\":{\"title\":\"${title}\",\"year\":\"${year}\"}}");
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, node(sb.toString())));
    }

    @Test
    void http_dataLoop_validOne_passes() {
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, node(
                "{\"method\":\"POST\",\"path\":\"/x\",\"data\":[{\"title\":\"A\"}],\"body\":\"{\\\"title\\\":\\\"${title}\\\"}\"}")));
    }

    @Test
    void http_dataLoop_elevenEntries_fails() {
        StringBuilder sb = new StringBuilder(
                "{\"method\":\"POST\",\"path\":\"/x\",\"data\":[");
        for (int i = 0; i < 11; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"title\":\"Sach \",\"year\":").append(1940 + i).append("}");
        }
        sb.append("]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, node(sb.toString())));
        assertTrue(e.getMessage().contains("at most 10 entries"), "message was: " + e.getMessage());
    }

    @Test
    void http_dataLoop_empty_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"data\":[]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("non-empty array"), "message was: " + e.getMessage());
    }

    @Test
    void http_dataLoop_notArray_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"data\":{\"title\":\"A\"}}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("non-empty array"), "message was: " + e.getMessage());
    }

    @Test
    void http_dataLoop_entryNotObject_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"data\":[\"not-an-object\"]}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("data entries must be objects"), "message was: " + e.getMessage());
    }

    @Test
    void http_autoInjectHeaders_valid_passes() {
        assertDoesNotThrow(() -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, node(
                "{\"method\":\"GET\",\"path\":\"/x\",\"auto_inject_headers\":{\"Authorization\":\"Bearer ${accessToken}\"}}")));
    }

    @Test
    void http_autoInjectHeaders_notObject_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"auto_inject_headers\":\"Bearer ${token}\"}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("object of strings"), "message was: " + e.getMessage());
    }

    @Test
    void http_autoInjectHeaders_valueNotString_fails() {
        ObjectNode n = node("{\"method\":\"GET\",\"path\":\"/x\",\"auto_inject_headers\":{\"Authorization\":123}}");
        var e = assertThrows(IllegalArgumentException.class,
                () -> StepConfigValidator.validateAndSerialize(StepType.HTTP_REQUEST, n));
        assertTrue(e.getMessage().contains("must be a string"), "message was: " + e.getMessage());
    }

    // ---------- Phase 7: JSON Schema generation ----------

    @Test
    void generateHttpSchema_hasRequiredFields() {
        JsonNode schema = StepConfigValidator.generateHttpSchema();
        assertEquals("http://json-schema.org/draft-07/schema#", schema.path("$schema").asText());
        assertEquals("HTTP_REQUEST step config", schema.path("title").asText());
        assertEquals("object", schema.path("type").asText());

        JsonNode required = schema.path("required");
        assertTrue(required.isArray());
        boolean hasMethod = false, hasPath = false;
        for (JsonNode r : required) {
            if ("method".equals(r.asText())) hasMethod = true;
            if ("path".equals(r.asText())) hasPath = true;
        }
        assertTrue(hasMethod, "required should contain 'method'");
        assertTrue(hasPath, "required should contain 'path'");
    }

    @Test
    void generateHttpSchema_methodHasEnum() {
        JsonNode schema = StepConfigValidator.generateHttpSchema();
        JsonNode methodProp = schema.path("properties").path("method");
        assertEquals("string", methodProp.path("type").asText());
        JsonNode enumNode = methodProp.path("enum");
        assertTrue(enumNode.isArray());
        boolean foundGet = false;
        for (JsonNode e : enumNode) {
            if ("GET".equals(e.asText())) foundGet = true;
        }
        assertTrue(foundGet, "GET should be in method enum");
    }

    @Test
    void generateHttp_schemaHasExpectedStatusRange() {
        JsonNode schema = StepConfigValidator.generateHttpSchema();
        JsonNode esProp = schema.path("properties").path("expected_status");
        assertEquals("integer", esProp.path("type").asText());
        assertEquals(100, esProp.path("minimum").asInt());
        assertEquals(599, esProp.path("maximum").asInt());
    }

    @Test
    void generateHttpSchema_hasAssertionsWithKindEnum() {
        JsonNode schema = StepConfigValidator.generateHttpSchema();
        JsonNode assertions = schema.path("properties").path("assertions");
        assertEquals("array", assertions.path("type").asText());
        JsonNode items = assertions.path("items");
        assertEquals("object", items.path("type").asText());
        JsonNode kindProp = items.path("properties").path("kind");
        assertEquals("string", kindProp.path("type").asText());
        JsonNode kindEnum = kindProp.path("enum");
        assertTrue(kindEnum.isArray());
        boolean hasAnyOf = false;
        for (JsonNode e : kindEnum) {
            if ("any_of".equals(e.asText())) hasAnyOf = true;
        }
        assertTrue(hasAnyOf, "any_of should be in assertion kind enum");
    }

    @Test
    void generateHttpSchema_hasDataAndAutoInjectHeaders() {
        JsonNode schema = StepConfigValidator.generateHttpSchema();
        JsonNode props = schema.path("properties");
        assertNotNull(props.path("data"), "data property should exist");
        assertNotNull(props.path("auto_inject_headers"), "auto_inject_headers property should exist");
        assertEquals("array", props.path("data").path("type").asText());
        assertEquals("object", props.path("auto_inject_headers").path("type").asText());
    }

    @Test
    void generateHttpSchema_extractHasRequiredFields() {
        JsonNode schema = StepConfigValidator.generateHttpSchema();
        JsonNode extract = schema.path("properties").path("extract");
        assertEquals("array", extract.path("type").asText());
        JsonNode items = extract.path("items");
        JsonNode itemRequired = items.path("required");
        boolean hasName = false, hasFrom = false, hasExpression = false;
        for (JsonNode r : itemRequired) {
            switch (r.asText()) {
                case "name" -> hasName = true;
                case "from" -> hasFrom = true;
                case "expression" -> hasExpression = true;
            }
        }
        assertTrue(hasName);
        assertTrue(hasFrom);
        assertTrue(hasExpression);
    }
}
