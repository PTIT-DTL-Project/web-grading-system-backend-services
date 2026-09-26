package vn.edu.ptit.web_grading_system.executor_service.service.step;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine.AssertionDetail;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Shared result factory for DB step executors (avoids duplicating the
 * {@link GradingStepResult} shaping across {@link DbQueryExecutor},
 * {@link DbSchemaCheckExecutor} and {@link DbMigrationExecutor}).
 */
final class DbStepResults {

    static GradingStepResult buildResult(ObjectMapper mapper,
            HttpStepExecutor.StepContext ctx, String stepType,
            StepResultStatus status, List<AssertionDetail> details,
            String err, long started) {
        String assertionJson;
        try {
            assertionJson = details.isEmpty() ? null
                    : mapper.writeValueAsString(details);
        } catch (Exception e) {
            assertionJson = null;
        }
        long duration = System.currentTimeMillis() - started;
        return GradingStepResult.builder()
                .jobId(ctx.jobId()).planId(ctx.planId()).stepId(ctx.stepId())
                .stepOrder(ctx.stepOrder()).stepName(ctx.stepName())
                .stepType(stepType).status(status)
                .assertionResult(assertionJson)
                .errorMessage(err).durationMs((int) duration)
                .startedAt(OffsetDateTime.now())
                .completedAt(OffsetDateTime.now())
                .build();
    }

    private DbStepResults() {}
}
