package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.Map;
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
        Map<?, ?> steps = (Map<?, ?>) result.getData();
        assertTrue(steps.containsKey("HTTP_REQUEST"));
        assertTrue(steps.containsKey("DB_QUERY"));
    }
}
