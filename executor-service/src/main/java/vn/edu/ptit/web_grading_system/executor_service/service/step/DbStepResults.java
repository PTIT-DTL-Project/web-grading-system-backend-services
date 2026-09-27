package vn.edu.ptit.web_grading_system.executor_service.service.step;

import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine.AssertionDetail;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionException;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbStepTimeoutException;

/**
 * Shared result factory for DB step executors (avoids duplicating the
 * {@link GradingStepResult} shaping across {@link DbQueryExecutor},
 * {@link DbSchemaCheckExecutor} and {@link DbMigrationExecutor}).
 *
 * <p>The labelling rule for {@link SQLException}s lives here:
 * exceptions raised by {@link
 * vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper}
 * ({@link DbConnectionException}, {@link DbStepTimeoutException}) are
 * surfaced verbatim; everything else is prefixed {@link
 * Constant.Message.Db#SQL_EXECUTION_ERROR}.
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
            /* Review: 2026-09-26, Pullfrog round 4 —
             * log serialization failures instead of swallowing
             * silently, so a broken assertion shape surfaces in
             * the job logs rather than a silent null field. */
            log.warn("Failed to serialize assertion details for step {}",
                    ctx.stepId(), e);
            assertionJson = null;
        }
        OffsetDateTime now = OffsetDateTime.now();
        return GradingStepResult.builder()
                .jobId(ctx.jobId()).planId(ctx.planId()).stepId(ctx.stepId())
                .stepOrder(ctx.stepOrder()).stepName(ctx.stepName())
                .stepType(stepType).status(status)
                .assertionResult(assertionJson)
                .errorMessage(err)
                .durationMs((int) (now.toInstant().toEpochMilli()
                        - startedMs))
                .startedAt(OffsetDateTime.ofInstant(
                        Instant.ofEpochMilli(startedMs),
                        ZoneId.systemDefault()))
                .completedAt(now)
                .build();
    }

    /**
     * Labelling rule for a {@link SQLException} thrown inside an
     * executor: {@link DbConnectionException} and {@link
     * DbStepTimeoutException} carry their final message already
     * (dialect hint or {@code SQL_TIMEOUT_ERROR}); every other
     * exception is a genuine statement error and is prefixed.
     */
    static String message(SQLException e) {
        return (e instanceof DbConnectionException
                || e instanceof DbStepTimeoutException)
                ? e.getMessage()
                : Constant.Message.Db.SQL_EXECUTION_ERROR + e.getMessage();
    }

    private DbStepResults() {}
}
