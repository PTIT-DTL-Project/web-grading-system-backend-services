package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ClassSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentSummaryDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

public class CommonToolsTest {

    @Test
    void testListMyClassesToolExecutes() {
        CourseServiceClient client = mock(CourseServiceClient.class);
        ListMyClassesTool tool = new ListMyClassesTool(client);

        ClassSummaryDto cls = ClassSummaryDto.builder()
                .id(UUID.randomUUID())
                .code("INT1434")
                .name("Kiến trúc máy tính")
                .description("Mô tả")
                .status("ACTIVE")
                .build();
        PageDto<ClassSummaryDto> page = new PageDto<>(List.of(cls), 1, 1, 20, 0);

        when(client.listStudentClasses(any(), any(), isNull(), anyInt(), anyInt(), any())).thenReturn(page);

        AgentContext ctx = new AgentContext(UUID.randomUUID(), "test@ptit.edu.vn", Set.of("ROLE_STUDENT"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(null, ctx);
        assertTrue(result.isSuccess());
        assertEquals("OK", result.getCode());
    }

    @Test
    void assignmentLookupSearchesByTitleAndReturnsPagination() {
        CourseServiceClient client = mock(CourseServiceClient.class);
        ListVisibleAssignmentsTool tool = new ListVisibleAssignmentsTool(client);
        AssignmentSummaryDto assignment = AssignmentSummaryDto.builder()
                .id(UUID.randomUUID()).title("Bài tập 4").build();
        when(client.listLecturerAssignments(anyString(), eq("ROLE_LECTURER"), isNull(),
                eq("Bài tập 4"), eq(0), eq(20), anyString()))
                .thenReturn(new PageDto<>(List.of(assignment), 1, 1, 20, 0));
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "secret");

        ToolResult result = tool.execute(new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode().put("search", "Bài tập 4"), ctx);

        assertTrue(result.isSuccess());
        assertEquals(1L, ((java.util.Map<?, ?>) result.getData()).get("totalElements"));
        assertTrue(tool.getToolDefinition().inputSchema().contains("search"));
        verify(client).listLecturerAssignments(ctx.userId().toString(), "ROLE_LECTURER", null,
                "Bài tập 4", 0, 20, "secret");
    }

    @Test
    void classLookupSearchesByNameAndReturnsPagination() {
        CourseServiceClient client = mock(CourseServiceClient.class);
        ListMyClassesTool tool = new ListMyClassesTool(client);
        ClassSummaryDto cls = ClassSummaryDto.builder().id(UUID.randomUUID()).name("Lớp 4").build();
        when(client.listLecturerClasses(anyString(), eq("ROLE_LECTURER"), eq("name:Lớp 4"),
                eq(0), eq(20), anyString()))
                .thenReturn(new PageDto<>(List.of(cls), 1, 1, 20, 0));
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "secret");

        ToolResult result = tool.execute(new com.fasterxml.jackson.databind.ObjectMapper()
                .createObjectNode().put("search", "Lớp 4"), ctx);

        assertTrue(result.isSuccess());
        assertEquals(1L, ((java.util.Map<?, ?>) result.getData()).get("totalElements"));
        assertTrue(tool.getToolDefinition().inputSchema().contains("search"));
        verify(client).listLecturerClasses(ctx.userId().toString(), "ROLE_LECTURER", "name:Lớp 4", 0, 20, "secret");
    }
}
