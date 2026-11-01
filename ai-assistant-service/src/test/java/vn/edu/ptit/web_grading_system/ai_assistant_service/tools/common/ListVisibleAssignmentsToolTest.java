package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.JacksonConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ListVisibleAssignmentsToolTest {
    @Test
    void blankOptionalClassIdListsAssignmentsWithoutClassFilter() throws Exception {
        var mapper = new JacksonConfig().objectMapper();
        var client = mock(CourseServiceClient.class);
        var tool = new ListVisibleAssignmentsTool(client);
        var context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_LECTURER"),
                UUID.randomUUID(), UUID.randomUUID(), "secret");

        var schema = mapper.readTree(tool.getToolDefinition().inputSchema());
        assertFalse(schema.path("properties").path("classId").has("format"));
        assertFalse(schema.path("required").toString().contains("classId"));
        assertTrue(schema.path("properties").path("page").path("description").asText().contains("0"));
        assertTrue(tool.execute(mapper.readTree("{\"classId\":\"\",\"search\":\"Bài tập 4\"}"),
                context).isSuccess());
        verify(client).listLecturerAssignments(eq(context.userId().toString()), eq("ROLE_LECTURER"),
                isNull(), eq("Bài tập 4"), eq(0), eq(20), eq("secret"));
    }
}
