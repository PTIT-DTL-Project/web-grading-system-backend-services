package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GuardCheckerTest {

    @Test
    void testGuardCheckerLimits() {
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(5);
        props.getLimits().setActiveTimeoutSeconds(60);

        GuardChecker checker = new GuardChecker(props);

        Run run = Run.builder()
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now().minusSeconds(10))
                .build();

        assertTrue(checker.canContinue(run, 3), "Should continue when within limits");
        assertFalse(checker.canContinue(run, 5), "Should halt when max steps reached");

        run.setStatus(RunStatus.CANCELLED);
        assertFalse(checker.canContinue(run, 1), "Should halt when run cancelled");
    }
}
