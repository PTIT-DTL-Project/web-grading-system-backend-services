package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DomainApiToolTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final DomainApiTool createQuestion = new DomainApiTool(
            "createTestQuestion", "Tạo câu kiểm tra", "ROLE_LECTURER", "POST",
            "/api/v1/assignments/{assignmentId}/plans/with-steps",
            new String[]{"assignmentId"}, new String[]{}, true, null,
            RestClient.create("http://localhost:1"), mapper);

    private final DomainApiTool createStep = new DomainApiTool(
            "createTestStep", "Tạo bước chấm", "ROLE_LECTURER", "POST",
            "/api/v1/assignments/{assignmentId}/plans/{planId}/steps",
            new String[]{"assignmentId", "planId"}, new String[]{}, true, null,
            RestClient.create("http://localhost:1"), mapper);

    private ToolResult validateStepThrough(DomainApiTool tool, String stepJson) throws Exception {
        String ids = "\"assignmentId\":\"" + UUID.randomUUID() + "\"";
        String json = tool == createQuestion
                ? "{" + ids + ",\"payload\":{\"plan\":{\"name\":\"Q1\",\"sequenceOrder\":1},\"steps\":[" + stepJson + "]}}"
                : "{" + ids + ",\"planId\":\"" + UUID.randomUUID() + "\",\"payload\":" + stepJson + "}";
        return tool.validateArguments(mapper.readTree(json));
    }

    @Test
    void stepSchemaDescribesHttpAndDbFeaturesForBothCreationRoutes() throws Exception {
        for (DomainApiTool tool : java.util.List.of(createQuestion, createStep)) {
            var schema = mapper.readTree(tool.getToolDefinition().inputSchema());
            var payload = schema.path("properties").path("payload");
            var step = tool == createQuestion ? payload.path("properties").path("steps").path("items") : payload;
            var config = step.path("properties").path("config");
            for (String key : java.util.List.of("method", "path", "assertions", "extract", "data",
                    "auto_inject_headers", "connection", "checks", "query", "statements")) {
                assertTrue(config.path("properties").has(key), key);
            }
            assertTrue(config.toString().contains("any_of"));
        }
    }

    @Test
    void bothCreationRoutesRejectInvalidHttpAndDbConfig() throws Exception {
        String[] invalid = {
                "{\"stepOrder\":1,\"name\":\"bad\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"TELEPORT\",\"path\":\"/books\"}}",
                "{\"stepOrder\":1,\"name\":\"bad\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"books\"}}",
                "{\"stepOrder\":1,\"name\":\"bad\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"/books\",\"expected_status\":600}}",
                "{\"stepOrder\":1,\"name\":\"bad\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"/books\",\"assertions\":[{\"kind\":\"any_of\",\"assertions\":[]}]}}",
                "{\"stepOrder\":1,\"name\":\"bad\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"POST\",\"path\":\"/books\",\"data\":[" + "{} ,".repeat(10) + "{}]}}",
                "{\"stepOrder\":1,\"name\":\"bad\",\"stepType\":\"DB_SCHEMA_CHECK\",\"config\":{\"checks\":[{\"kind\":\"PRIMARY_KEY\",\"column\":\"id\"}]}}",
                "{\"stepOrder\":1,\"name\":\"bad\",\"stepType\":\"DB_QUERY\",\"config\":{\"query\":\"SELECT COUNT(*) FROM books\",\"expected\":{\"row_count\":0}}}",
                "{\"stepOrder\":1,\"name\":\"bad\",\"stepType\":\"DB_MIGRATION\",\"config\":{\"statements\":[]}}"
        };
        for (DomainApiTool tool : java.util.List.of(createQuestion, createStep)) {
            for (String step : invalid) {
                ToolResult result = validateStepThrough(tool, step);
                assertNotNull(result, tool.getName() + " accepted " + step);
                assertEquals("INVALID_ARGUMENTS", result.getCode());
            }
        }
    }

    @Test
    void bothCreationRoutesAcceptAdvancedHttpAndValidDbStep() throws Exception {
        String http = "{\"stepOrder\":1,\"name\":\"create\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"POST\",\"path\":\"/books\",\"data\":[{\"title\":\"A\"}],\"auto_inject_headers\":{\"Authorization\":\"Bearer ${token}\"},\"extract\":[{\"name\":\"id\",\"from\":\"response_body\",\"expression\":\"$.id\"}],\"assertions\":[{\"kind\":\"any_of\",\"assertions\":[{\"kind\":\"status\",\"equals\":201}]}]}}";
        String db = "{\"stepOrder\":2,\"name\":\"schema\",\"stepType\":\"DB_SCHEMA_CHECK\",\"config\":{\"connection\":{\"db_type\":\"postgres\",\"db_service\":\"db\",\"db_port\":5432},\"checks\":[{\"kind\":\"PRIMARY_KEY\",\"table_name\":\"books\",\"column\":\"id\"}]}}";
        for (DomainApiTool tool : java.util.List.of(createQuestion, createStep)) {
            assertNull(validateStepThrough(tool, http), tool.getName());
            assertNull(validateStepThrough(tool, db), tool.getName());
        }
    }

    @Test
    void questionRequiresPlanAndNonEmptyStepsBeforeRequest() throws Exception {
        String id = UUID.randomUUID().toString();
        for (String payload : java.util.List.of(
                "{\"steps\":[{\"stepOrder\":1,\"name\":\"health\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"/health\"}}]}",
                "{\"plan\":{\"name\":\"Q1\",\"sequenceOrder\":1},\"steps\":[]}")) {
            ToolResult result = createQuestion.validateArguments(mapper.readTree(
                    "{\"assignmentId\":\"" + id + "\",\"payload\":" + payload + "}"));
            assertNotNull(result);
            assertEquals("INVALID_ARGUMENTS", result.getCode());
        }
    }

    @Test
    void bothCreationRoutesRejectMissingStepIdentityOrBodyExpectation() throws Exception {
        String[] invalid = {
                "{\"name\":\"health\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"/health\"}}",
                "{\"stepOrder\":1,\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"/health\"}}",
                "{\"stepOrder\":1,\"name\":\"body\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"/books\",\"assertions\":[{\"kind\":\"body_equals\"}]}}",
                "{\"stepOrder\":1,\"name\":\"shape\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"/books\",\"assertions\":[{\"kind\":\"body_structure\"}]}}"
        };
        for (DomainApiTool tool : java.util.List.of(createQuestion, createStep)) {
            for (String step : invalid) {
                ToolResult result = validateStepThrough(tool, step);
                assertNotNull(result, tool.getName() + " accepted " + step);
                assertEquals("INVALID_ARGUMENTS", result.getCode());
            }
        }
    }

    @Test
    void studentCannotInvokeLecturerWriteOrSeeItInRoleFilteredRegistry() throws Exception {
        var student = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "secret");
        var args = mapper.readTree("{\"assignmentId\":\"" + UUID.randomUUID() + "\",\"payload\":{}}");
        assertEquals("FORBIDDEN", createQuestion.execute(args, student).getCode());
        assertTrue(new ToolRegistry(java.util.List.of(createQuestion), mapper)
                .getToolsForRoles(student.roles()).isEmpty());
    }

    @Test
    void questionSchemaRequiresPlanAndStepsAndDirectCallbackCannotWrite() throws Exception {
        var schema = mapper.readTree(createQuestion.getToolDefinition().inputSchema());
        assertTrue(schema.path("properties").path("payload").path("required").toString().contains("steps"));
        String configDescription = schema.path("properties").path("payload").path("properties")
                .path("steps").path("items").path("properties").path("config")
                .path("description").asText();
        assertTrue(configDescription.contains("path"));
        assertTrue(configDescription.contains("query"));
        assertEquals("APPROVAL_REQUIRED", mapper.readTree(createQuestion.call("{}"))
                .path("code").asText());
        assertEquals("INVALID_ARGUMENTS", createQuestion.validateArguments(mapper.readTree("{}"))
                .getCode());
    }

    @Test
    void invalidStepConfigIsRejectedBeforeAskingForConsent() throws Exception {
        var args = mapper.readTree("{\"assignmentId\":\"" + UUID.randomUUID()
                + "\",\"payload\":{\"plan\":{\"name\":\"Q1\",\"sequenceOrder\":1},"
                + "\"steps\":[{\"name\":\"HTTP\",\"stepType\":\"HTTP_REQUEST\",\"stepOrder\":1,"
                + "\"config\":{\"method\":\"POST\",\"url\":\"/api/books\"}}]}}");

        ToolResult result = createQuestion.validateArguments(args);

        assertNotNull(result);
        assertEquals("INVALID_ARGUMENTS", result.getCode());
        assertTrue(result.getMessage().contains("path"));
    }

    @Test
    void studentPlanReadUsesScopedPublicRouteAndCallerHeaders() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://course.local");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID studentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        server.expect(requestTo("http://course.local/api/v1/student/assignments/" + assignmentId + "/plans"))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andExpect(header("X-User-Id", studentId.toString()))
                .andExpect(header("X-User-Roles", "ROLE_STUDENT"))
                .andRespond(withSuccess("[{\"id\":\"" + UUID.randomUUID() + "\",\"name\":\"Q1\"}]", MediaType.APPLICATION_JSON));
        DomainApiTool tool = new DomainApiTool("listMyTestPlans", "", "ROLE_STUDENT", "GET",
                "/api/v1/student/assignments/{assignmentId}/plans", new String[]{"assignmentId"},
                new String[]{}, false, null, builder.build(), mapper);
        var context = new AgentContext(studentId, "student@ptit.edu.vn", Set.of("ROLE_STUDENT"),
                UUID.randomUUID(), UUID.randomUUID(), "secret");

        ToolResult result = tool.execute(mapper.readTree("{\"assignmentId\":\"" + assignmentId + "\"}"), context);

        assertTrue(result.isSuccess(), result.getMessage());
        server.verify();
    }

    @Test
    void writeRejectionPreservesSafeValidationMessageFromCourseService() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://course.local");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID assignmentId = UUID.randomUUID();
        // Validate endpoint chấp nhận, sau đó endpoint tạo thật trả 400.
        server.expect(requestTo("http://course.local/api/v1/internal/test-plans/validate"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andRespond(withSuccess("{\"valid\":true,\"errors\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://course.local/api/v1/assignments/" + assignmentId + "/plans/with-steps"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
                        .withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"status\":400,\"message\":\"HTTP_REQUEST: missing or empty 'path'\"}"));
        DomainApiTool tool = new DomainApiTool("createTestQuestion", "Tạo câu kiểm tra", "ROLE_LECTURER",
                "POST", "/api/v1/assignments/{assignmentId}/plans/with-steps",
                new String[]{"assignmentId"}, new String[]{}, true, null, builder.build(), mapper);
        AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "secret");

        ToolResult result = tool.execute(mapper.readTree("{\"assignmentId\":\"" + assignmentId
                + "\",\"payload\":{\"plan\":{\"name\":\"Q1\",\"sequenceOrder\":1},\"steps\":[{\"stepOrder\":1,\"name\":\"health\",\"stepType\":\"HTTP_REQUEST\",\"config\":{\"method\":\"GET\",\"path\":\"/health\"}}]}}"), context);

        assertEquals("DOMAIN_REJECTED", result.getCode());
        assertTrue(result.getMessage().contains("HTTP_REQUEST: missing or empty 'path'"));
        server.verify();
    }
}
