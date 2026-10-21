package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LecturerQueryToolsTest {

    @Test
    void testGetTestStepCapabilitiesReturnsSupportedTypes() {
        GetTestStepCapabilitiesTool tool = new GetTestStepCapabilitiesTool();
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "lec@ptit.edu.vn", Set.of("ROLE_LECTURER"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(null, ctx);

        assertTrue(result.isSuccess());
        List<?> steps = (List<?>) result.getData();
        assertTrue(steps.contains("HTTP_REQUEST"));
        assertTrue(steps.contains("DB_QUERY"));
    }
}
