package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.CreateAssignmentCommand;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.OperationService;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.Set;
import java.util.UUID;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class CreateAssignmentToolTest {

    @Test
    void testValidateFailsWhenRequiredFieldsMissing() throws Exception {
        CourseServiceClient client = mock(CourseServiceClient.class);
        OperationService opService = mock(OperationService.class);
        CreateAssignmentTool tool = new CreateAssignmentTool(client, opService, new ObjectMapper());

        AgentContext ctx = new AgentContext(UUID.randomUUID(), "lec@ptit.edu.vn", Set.of("ROLE_LECTURER"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(new ObjectMapper().readTree("{}"), ctx);

        assertFalse(result.isSuccess(), "Should fail when title and classId missing");
    }

    @Test
    void defaultsToStudentDockerComposeWhenStrategyMissing() throws Exception {
        CourseServiceClient client = mock(CourseServiceClient.class);
        OperationService operations = mock(OperationService.class);
        ObjectMapper mapper = new JacksonConfig().objectMapper();
        CreateAssignmentTool tool = new CreateAssignmentTool(client, operations, mapper);
        UUID classId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"), null, null, "secret");
        when(operations.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(client.createAssignment(anyString(), eq("ROLE_LECTURER"), any(), eq("secret")))
                .thenReturn(ApiEnvelope.<AssignmentSummaryDto>builder()
                        .status(201).data(AssignmentSummaryDto.builder().id(assignmentId).title("Bài 1").build()).build());

        // Review: 2026-10-27 — gradingStrategy is optional now. The tool must fill in the
        // lecturer-created default and still create the assignment instead of rejecting the call.
        ToolResult result = tool.execute(mapper.readTree("{\"title\":\"Bài 1\",\"classId\":\"" + classId + "\"}"), ctx);

        assertTrue(result.isSuccess());
        ArgumentCaptor<CreateAssignmentCommand> body = ArgumentCaptor.forClass(CreateAssignmentCommand.class);
        verify(client).createAssignment(eq(ctx.userId().toString()), eq("ROLE_LECTURER"), body.capture(), eq("secret"));
        assertEquals("STUDENT_DOCKER_COMPOSE", body.getValue().gradingStrategy());
        assertEquals(classId, body.getValue().classId());
    }

    @Test
    void rejectsLecturerComposeWithoutTemplateBeforeCallingCourseService() throws Exception {
        CourseServiceClient client = mock(CourseServiceClient.class);
        CreateAssignmentTool tool = new CreateAssignmentTool(client, mock(OperationService.class), new JacksonConfig().objectMapper());
        UUID classId = UUID.randomUUID();
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"), null, null, "secret");

        ToolResult result = tool.execute(new ObjectMapper().readTree(
                "{\"title\":\"Bài 1\",\"classId\":\"" + classId + "\",\"gradingStrategy\":\"LECTURER_DOCKER_COMPOSE\"}"), ctx);

        assertFalse(result.isSuccess());
        assertEquals("INVALID_ARGUMENTS", result.getCode());
        verifyNoInteractions(client);
    }

    @Test
    void schemaDoesNotRequireGradingStrategy() throws Exception {
        CreateAssignmentTool tool = new CreateAssignmentTool(
                mock(CourseServiceClient.class), mock(OperationService.class), new JacksonConfig().objectMapper());

        JsonNode required = new ObjectMapper()
                .readTree(tool.getToolDefinition().inputSchema())
                .path("required");

        // A required gradingStrategy made the model ask the lecturer to pick one; the schema
        // must keep it optional so the STUDENT_DOCKER_COMPOSE default applies.
        assertTrue(required.isArray());
        assertFalse(required.toString().contains("gradingStrategy"));
        assertTrue(required.toString().contains("title"));
        assertTrue(required.toString().contains("classId"));
    }

    @Test
    void sendsCourseRequestShapeAndOnlyReportsSuccessWithCreatedId() throws Exception {
        CourseServiceClient client = mock(CourseServiceClient.class);
        OperationService operations = mock(OperationService.class);
        ObjectMapper mapper = new JacksonConfig().objectMapper();
        CreateAssignmentTool tool = new CreateAssignmentTool(client, operations, mapper);
        UUID classId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"), null, null, "secret");
        when(operations.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(client.createAssignment(anyString(), eq("ROLE_LECTURER"), any(), eq("secret")))
                .thenReturn(ApiEnvelope.<AssignmentSummaryDto>builder()
                        .status(201).data(AssignmentSummaryDto.builder().id(assignmentId).title("Bài 1").build()).build());

        ToolResult result = tool.execute(mapper.readTree("{\"title\":\"Bài 1\",\"classId\":\"" + classId + "\",\"gradingStrategy\":\"STUDENT_DOCKER_COMPOSE\"}"), ctx);

        assertTrue(result.isSuccess());
        ArgumentCaptor<CreateAssignmentCommand> body = ArgumentCaptor.forClass(CreateAssignmentCommand.class);
        verify(client).createAssignment(eq(ctx.userId().toString()), eq("ROLE_LECTURER"), body.capture(), eq("secret"));
        assertEquals(classId, body.getValue().classId());
        assertEquals("STUDENT_DOCKER_COMPOSE", body.getValue().gradingStrategy());
        assertEquals("Bài 1", body.getValue().title());
        assertFalse(mapper.writeValueAsString(body.getValue()).contains("idempotencyKey"));
        verify(operations).markSuccess(anyString(), anyString());
    }

    @Test
    void emptyCreatedPayloadCannotBeReportedAsSuccess() throws Exception {
        CourseServiceClient client = mock(CourseServiceClient.class);
        OperationService operations = mock(OperationService.class);
        ObjectMapper mapper = new JacksonConfig().objectMapper();
        CreateAssignmentTool tool = new CreateAssignmentTool(client, operations, mapper);
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"), null, null, "secret");
        when(operations.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(client.createAssignment(anyString(), anyString(), any(), anyString()))
                .thenReturn(ApiEnvelope.<AssignmentSummaryDto>builder().status(201).build());

        ToolResult result = tool.execute(mapper.readTree("{\"title\":\"Bài 1\",\"classId\":\"" + UUID.randomUUID() + "\",\"gradingStrategy\":\"STUDENT_DOCKER_COMPOSE\"}"), ctx);

        assertFalse(result.isSuccess());
        verify(operations, never()).markSuccess(anyString(), anyString());
        verify(operations).markFailure(anyString(), anyString());
    }

    @Test
    void courseValidationErrorIsNotRetriedOrReportedAsSuccess() throws Exception {
        CourseServiceClient client = mock(CourseServiceClient.class);
        OperationService operations = mock(OperationService.class);
        ObjectMapper mapper = new JacksonConfig().objectMapper();
        CreateAssignmentTool tool = new CreateAssignmentTool(client, operations, mapper);
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"), null, null, "secret");
        FeignException.BadRequest badRequest = mock(FeignException.BadRequest.class);
        when(badRequest.contentUTF8()).thenReturn("{\"error\":\"gradingStrategy: must not be null\"}");
        when(badRequest.getMessage()).thenReturn("400 Bad Request");
        when(operations.findByIdempotencyKey(anyString())).thenReturn(Optional.empty());
        when(client.createAssignment(anyString(), anyString(), any(), anyString())).thenThrow(badRequest);

        ToolResult result = tool.execute(mapper.readTree("{\"title\":\"Bài 1\",\"classId\":\"" + UUID.randomUUID() + "\",\"gradingStrategy\":\"STUDENT_DOCKER_COMPOSE\"}"), ctx);

        assertFalse(result.isSuccess());
        assertEquals("VALIDATION_ERROR", result.getCode());
        assertFalse(result.isRetryable());
        verify(client, times(1)).createAssignment(anyString(), anyString(), any(), anyString());
        verify(operations, never()).markSuccess(anyString(), anyString());
    }
}
