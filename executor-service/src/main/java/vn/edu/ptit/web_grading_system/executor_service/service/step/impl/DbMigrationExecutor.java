package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.exception.DbStepTimeoutException;
import vn.edu.ptit.web_grading_system.executor_service.entity.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entity.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepExecutor;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepContext;

/**
 * Executes a {@code DB_MIGRATION} step: runs each lecturer
 * statement in a transaction and commits only when every
 * statement succeeds.
 *
 * <p><b>Atomicity is engine-dependent.</b> PostgreSQL honours
 * the guarantee for both DDL and DML. MySQL and MariaDB force
 * an implicit commit on DDL statements
 * ({@code CREATE TABLE}, {@code ALTER TABLE}, {@code DROP TABLE},
 * {@code CREATE/DROP INDEX}, {@code TRUNCATE} — see MySQL §15.3.3
 * and MariaDB "SQL statements Causing an Implicit Commit"), so a
 * migration containing DDL followed by a failing statement is
 * <em>best-effort</em>: the DDL is already durably applied,
 * {@code rollback()} has nothing to undo, and the step reports
 * {@code ERROR} without indicating the schema is partially
 * migrated.
 *
 * <p>Connection failure returns {@link StepResultStatus#ERROR}
 * with a dialect hint; a statement failure rolls back and
 * returns {@code ERROR}. A migration that completes without
 * exception is {@code PASSED} with no assertions and no extracted
 * variables (by design).
 *
 * <p>The step's {@code timeoutMs} budget is enforced as a single
 * deadline across all statements: each statement is given the
 * remaining time (clamped to at least 1 second), and the migration
 * is aborted with {@link
 * vn.edu.ptit.web_grading_system.executor_service.exception.DbStepTimeoutException}
 * once the budget is spent. {@code commit} and {@code rollback}
 * remain unbounded; a lock wait during {@code commit} is bounded
 * server-side by {@code innodb_lock_wait_timeout} on MySQL.
 */
@Component
@RequiredArgsConstructor
public class DbMigrationExecutor implements StepExecutor {

    private final DbConnectionHelper db;
    private final ObjectMapper mapper;

    @Override
    public String type() { return Constant.DbStep.TYPE_MIGRATION; }

    @Override
    public GradingStepResult execute(StepContext ctx) {
        JsonNode config = ctx.getConfig();
        Integer hostPort = (Integer) ctx.getVariableContext()
                .get(Constant.VariableContext.DB_PORT);
        int timeoutMs = ctx.getConfig().path(Constant.DbStep.TIMEOUT_MS)
                .asInt(ctx.getTimeoutMs() != null ? ctx.getTimeoutMs() : 30_000);
        long deadline = System.currentTimeMillis() + timeoutMs;
        long started = System.currentTimeMillis();
        List<AssertionEngine.AssertionDetail> details = new ArrayList<>();
        try {
            db.withConnection(config, hostPort, timeoutMs, conn -> {
                conn.setAutoCommit(false);
                try {
                    for (JsonNode stmt : config.get(Constant.DbStep.STATEMENTS)) {
                        int remaining = (int) (deadline - System.currentTimeMillis());
                        if (remaining <= 0) {
                            throw new DbStepTimeoutException(
                                    Constant.Message.Db.SQL_TIMEOUT_ERROR
                                            + timeoutMs + "ms");
                        }
                        try (PreparedStatement ps = conn.prepareStatement(
                                ctx.getVariableContext().substitute(
                                        stmt.asText()))) {
                            ps.setQueryTimeout(
                                    Math.max(1, (int) Math.ceil(remaining / 1000.0)));
                            ps.executeUpdate();
                        }
                    }
                    conn.commit();
                } catch (SQLException e) {
                    conn.rollback();
                    throw e;
                } finally {
                    conn.setAutoCommit(true);
                }
                return null;
            });
        } catch (SQLException e) {
            return DbStepResults.buildResult(mapper, ctx, type(),
                    StepResultStatus.ERROR, details,
                    DbStepResults.message(e), started);
        }
        return DbStepResults.buildResult(mapper, ctx, type(),
                StepResultStatus.PASSED, details, null, started);
    }
}
