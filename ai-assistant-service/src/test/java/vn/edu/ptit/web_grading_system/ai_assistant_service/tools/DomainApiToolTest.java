package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.http.MediaType;

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
        assertEquals("APPROVAL_REQUIRED", mapper.readTree(createQuestion.call("{}"))
                .path("code").asText());
        assertEquals("INVALID_ARGUMENTS", createQuestion.validateArguments(mapper.readTree("{}"))
                .getCode());
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
}
