package vn.edu.ptit.web_grading_system.executor_service.service.step;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine.AssertionDetail;

/**
 * Executes a {@code DB_QUERY} step: runs the lecturer's SQL and
 * compares the result against {@code expected.row_count} /
 * {@code expected.columns}.
 *
 * <p>Connection failure returns {@link StepResultStatus#ERROR} with a
 * dialect hint (see {@link Constant.Message.Db}); a successful connection
 * that fails to execute is also {@code ERROR} with a {@link
 * Constant.Message.Db#SQL_EXECUTION_ERROR} prefix.
 *
 * <p>Variable substitution uses values from {@link
 * VariableContext}, which are fed by extract[] reading the student's own
 * app responses. The graded DB is per-job and disposable, so the blast
 * radius is limited to that student's own grade; nevertheless,
 * lecturers are recommended to interpolate only system variables
 * (e.g. {@code ${submission_id}}), not student-controlled values.
 */
@Component
@RequiredArgsConstructor
public class DbQueryExecutor implements StepExecutor {

    private final DbConnectionHelper db;
    private final ObjectMapper mapper;

    @Override
    public String type() { return Constant.DbStep.TYPE_QUERY; }

    @Override
    public GradingStepResult execute(HttpStepExecutor.StepContext ctx) {
        JsonNode config = ctx.config();
        String query = ctx.variableContext().substitute(
                config.path(Constant.DbStep.QUERY).asText(""));
        Integer hostPort = (Integer) ctx.variableContext()
                .get(Constant.VariableContext.DB_PORT);
        int timeoutSeconds = ctx.timeoutMs() != null
                ? (int) Math.ceil(ctx.timeoutMs() / 1000.0)
                : 30;
        long started = System.currentTimeMillis();
        List<AssertionDetail> details = new ArrayList<>();
        try {
            int[] rowCount = {0};
            List<String> columns = new ArrayList<>();
            db.withConnection(config, hostPort, ctx.timeoutMs() != null
                    ? ctx.timeoutMs() : 30_000, conn -> {
                try (Statement stmt = conn.createStatement()) {
                    stmt.setQueryTimeout(timeoutSeconds);
                    try (ResultSet rs = stmt.executeQuery(query)) {
                        ResultSetMetaData meta = rs.getMetaData();
                        for (int i = 1; i <= meta.getColumnCount(); i++) {
                            columns.add(meta.getColumnLabel(i));
                        }
                        while (rs.next()) { rowCount[0]++; }
                    }
                }
                return null;
            });
            JsonNode expected = config.path(Constant.DbStep.EXPECTED);
            if (expected.isObject()) {
                if (expected.has(Constant.DbStep.ROW_COUNT)) {
                    int exp = expected.get(Constant.DbStep.ROW_COUNT).asInt();
                    details.add(assertion(Constant.DbStep.ASSERT_ROW_COUNT, exp,
                            rowCount[0], rowCount[0] == exp,
                            "row_count expected %d, actual %d"
                                    .formatted(exp, rowCount[0])));
                }
                if (expected.has(Constant.DbStep.COLUMNS)) {
                    List<String> expCols = new ArrayList<>();
                    expected.get(Constant.DbStep.COLUMNS).forEach(c ->
                            expCols.add(c.asText().toLowerCase()));
                    List<String> actCols = new ArrayList<>();
                    for (String c : columns) actCols.add(c.toLowerCase());
                    boolean ok = actCols.size() == expCols.size();
                    if (ok) {
                        for (int i = 0; i < expCols.size(); i++) {
                            if (!actCols.get(i).equals(expCols.get(i))) { ok = false; break; }
                        }
                    }
                    details.add(assertion(Constant.DbStep.ASSERT_COLUMNS, expCols,
                            columns, ok,
                            ok ? "columns matched"
                                    : "columns mismatch: expected %s, actual %s"
                                        .formatted(expCols, columns)));
                }
            }
        } catch (SQLException e) {
            return DbStepResults.buildResult(mapper, ctx, type(),
                    StepResultStatus.ERROR, details,
                    connectionMessage(e) + e.getMessage(), started);
        }
        boolean passed = details.stream().allMatch(AssertionDetail::isPassed);
        return DbStepResults.buildResult(mapper, ctx, type(),
                passed ? StepResultStatus.PASSED : StepResultStatus.FAILED,
                details, null, started);
    }

    /** Connection failures are wrapped by {@link
     * DbConnectionHelper} with the dialect hint as the cause;
     * lecturer-SQL failures are not. */
    private static String connectionMessage(SQLException e) {
        return (e.getCause() != null) ? ""
                : Constant.Message.Db.SQL_EXECUTION_ERROR;
    }

    private static AssertionDetail assertion(String kind, Object expected,
            Object actual, boolean passed, String message) {
        return AssertionDetail.builder().kind(kind).expected(expected)
                .actual(actual).passed(passed).message(message).build();
    }
}
