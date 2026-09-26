package vn.edu.ptit.web_grading_system.executor_service.service.step;

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
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;

/**
 * Executes a {@code DB_MIGRATION} step: runs each lecturer
 * statement in a single transaction and commits only when every
 * statement succeeds.
 *
 * <p>Connection failure returns {@link StepResultStatus#ERROR}
 * with a dialect hint; a statement failure rolls back and
 * returns {@code ERROR}. A migration that completes without
 * exception is {@code PASSED} with no assertions and no extracted
 * variables (by design).
 */
@Component
@RequiredArgsConstructor
public class DbMigrationExecutor implements StepExecutor {

    private final DbConnectionHelper db;
    private final ObjectMapper mapper;

    @Override
    public String type() { return Constant.DbStep.TYPE_MIGRATION; }

    @Override
    public GradingStepResult execute(HttpStepExecutor.StepContext ctx) {
        JsonNode config = ctx.config();
        Integer hostPort = (Integer) ctx.variableContext()
                .get(Constant.VariableContext.DB_PORT);
        int timeoutSeconds = ctx.timeoutMs() != null
                ? (int) Math.ceil(ctx.timeoutMs() / 1000.0)
                : 30;
        long started = System.currentTimeMillis();
        List<AssertionEngine.AssertionDetail> details = new ArrayList<>();
        try {
            db.withConnection(config, hostPort, ctx.timeoutMs() != null
                    ? ctx.timeoutMs() : 30_000, conn -> {
                conn.setAutoCommit(false);
                try {
                    for (JsonNode stmt : config.get(Constant.DbStep.STATEMENTS)) {
                        try (PreparedStatement ps = conn.prepareStatement(
                                ctx.variableContext().substitute(
                                        stmt.asText()))) {
                            ps.setQueryTimeout(timeoutSeconds);
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
                    connectionMessage(e) + e.getMessage(), started);
        }
        return DbStepResults.buildResult(mapper, ctx, type(),
                StepResultStatus.PASSED, details, null, started);
    }

    /** Connection failures are wrapped by {@link
     * DbConnectionHelper} with the dialect hint as the cause;
     * statement failures are not. */
    private static String connectionMessage(SQLException e) {
        return (e.getCause() != null) ? ""
                : Constant.Message.Db.SQL_EXECUTION_ERROR;
    }
}
