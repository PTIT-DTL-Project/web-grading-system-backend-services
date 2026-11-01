package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ToolTimelineStatusTest {
    @Test
    void failedToolResultIsNotShownAsSuccessfulCall() {
        assertTrue(AgentRuntime.isToolFailure("{\"success\":false,\"code\":\"INVALID_ARGUMENTS\",\"message\":\"bad input\"}"));
        assertFalse(AgentRuntime.isToolFailure("{\"items\":[],\"success\":false}"));
        assertFalse(AgentRuntime.isToolFailure("plain result"));
    }
}
