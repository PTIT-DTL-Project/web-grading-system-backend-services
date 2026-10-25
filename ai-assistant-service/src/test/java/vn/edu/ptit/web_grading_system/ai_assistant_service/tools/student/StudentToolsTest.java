package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.student;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.SubmissionServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

public class StudentToolsTest {

    @Test
    void testGetMySubmissionRejectsLecturerRole() {
        SubmissionServiceClient client = mock(SubmissionServiceClient.class);
        GetMySubmissionTool tool = new GetMySubmissionTool(client);

        AgentContext lecturerCtx = new AgentContext(UUID.randomUUID(), "lecturer@ptit.edu.vn", Set.of("ROLE_LECTURER"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(null, lecturerCtx);
        assertFalse(result.isSuccess(), "Student tool should reject non-student caller");
    }
}
