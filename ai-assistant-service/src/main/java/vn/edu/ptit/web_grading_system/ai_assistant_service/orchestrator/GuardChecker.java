package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class GuardChecker {

    private final AgentProperties properties;

    public boolean canContinue(Run run, int currentSteps) {
        if (run == null) {
            return false;
        }
        if (run.getStatus() == RunStatus.CANCELLED) {
            return false;
        }
        if (currentSteps >= properties.getLimits().getMaxModelSteps()) {
            return false;
        }

        if (run.getStartedAt() != null) {
            long elapsed = Duration.between(run.getStartedAt(), Instant.now()).getSeconds();
            if (elapsed >= properties.getLimits().getActiveTimeoutSeconds()) {
                return false;
            }
        }
        return true;
    }
}
