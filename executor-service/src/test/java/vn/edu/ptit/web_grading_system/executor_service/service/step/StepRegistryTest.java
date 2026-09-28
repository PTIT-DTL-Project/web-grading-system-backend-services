package vn.edu.ptit.web_grading_system.executor_service.service.step;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import vn.edu.ptit.web_grading_system.executor_service.entity.GradingStepResult;

class StepRegistryTest {

    private static class DupStep implements StepExecutor {
        private final String type;

        DupStep(String type) { this.type = type; }

        @Override public String type() { return type; }
        @Override public GradingStepResult execute(StepContext ctx) { return null; }
    }

    @Test
    void duplicateType_throwsWithClasses() {
        var e = assertThrows(IllegalStateException.class,
                () -> new StepRegistry(List.of(new DupStep("DB_QUERY"), new DupStep("DB_QUERY"))));
        assertTrue(e.getMessage().contains("DB_QUERY"));
        assertTrue(e.getMessage().contains("DupStep"));
    }
}
