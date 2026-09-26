package vn.edu.ptit.web_grading_system.executor_service.service.step;

import tools.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine.AssertionDetail;

/**
 * Shared result factory for DB step executors (avoids duplicating the
 * {@link GradingStepResult} shaping across {@link DbQueryExecutor},
 * {@link DbSchemaCheckExecutor} and {@link DbMigrationExecutor}).
 */
@Slf4j
final class DbStepResults {

    static GradingStepResult buildResult(ObjectMapper mapper,
            HttpStepExecutor.StepContext ctx, String stepType,
            StepResultStatus status, List<AssertionDetail> details,
            String err, long startedMs) {
        String assertionJson;
        try {
            assertionJson = details.isEmpty() ? null
                    : mapper.writeValueAsString(details);
        } catch (Exception e) {
            /* Review: 2026-09-26, Pullfrog PR #17 (round 3) —
             * log serialization failures instead of swallowing
             * silently, so a broken assertion shape surfaces in
             * the job logs rather than a silent null field. */
            log.warn("Failed to serialize assertion details for step {}",
                    ctx.stepId(), e);
            assertionJson = null;
        }
        long duration = System.currentTimeMillis() - startedMs;
        return GradingStepResult.builder()
                .jobId(ctx.jobId()).planId(ctx.planId()).stepId(ctx.stepId())
                .stepOrder(ctx.stepOrder()).stepName(ctx.stepName())
                .stepType(stepType).status(status)
                .assertionResult(assertionJson)
                .errorMessage(err).durationMs((int) duration)
                .startedAt(OffsetDateTime.now()
                        .minusNanos(
                            (System.currentTimeMillis() - startedMs)
                                * 1_000_000L))
                .completedAt(OffsetDateTime.now())
                .build();
    }

    private DbStepResults() {}
}
