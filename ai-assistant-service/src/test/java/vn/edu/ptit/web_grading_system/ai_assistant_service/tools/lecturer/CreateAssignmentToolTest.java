package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.OperationService;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

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
}
