package vn.edu.ptit.web_grading_system.executor_service.service.step;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialect;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.AssertionEngine.AssertionDetail;

/**
 * Executes a {@code DB_SCHEMA_CHECK} step: runs each check against
 * the dialect's information-schema query and reports one
 * {@link AssertionDetail} per check.
 *
 * <p>Connection failure returns {@link StepResultStatus#ERROR} with
 * a dialect hint; a failing check returns {@code FAILED} with the
 * per-check details.
 */
@Component
@RequiredArgsConstructor
public class DbSchemaCheckExecutor implements StepExecutor {

    private final DbConnectionHelper db;
    private final ObjectMapper mapper;

    @Override
    public String type() { return Constant.DbStep.TYPE_SCHEMA_CHECK; }

    @Override
    public GradingStepResult execute(HttpStepExecutor.StepContext ctx) {
        JsonNode config = ctx.config();
        JsonNode connection = config.path(Constant.DbConnection.CONNECTION);
        String dbType = connection.path(Constant.DbConnection.DB_TYPE)
                .asString(Constant.DbConnection.DEFAULT_DB_TYPE);
        Integer hostPort = (Integer) ctx.variableContext()
                .get(Constant.VariableContext.DB_PORT);
        int timeoutSeconds = ctx.timeoutMs() != null
                ? (int) Math.ceil(ctx.timeoutMs() / 1000.0)
                : 30;
        long started = System.currentTimeMillis();
        List<AssertionDetail> details = new ArrayList<>();
        try {
            db.withConnection(config, hostPort, ctx.timeoutMs() != null
                    ? ctx.timeoutMs() : 30_000, conn -> {
                DbDialect dialect = db.resolve(dbType);
                for (JsonNode check : config.get(Constant.DbStep.CHECKS)) {
                    details.add(runCheck(conn, dialect, check, timeoutSeconds));
                }
                return null;
            });
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

    private AssertionDetail runCheck(Connection conn, DbDialect dialect,
            JsonNode check, int timeoutSeconds) throws SQLException {
        String kind = check.path("kind").asText();
        return switch (kind) {
            case Constant.DbStep.KIND_TABLE_EXISTS -> {
                String table = check.path(Constant.DbStep.TABLE_NAME).asText();
                String sql = dialect.tableExistsSql();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, table);
                    ps.setQueryTimeout(timeoutSeconds);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        boolean ok = rs.getInt(1) > 0;
                        yield assertion(kind, true, ok, ok,
                                ok ? "table '" + table + "' exists"
                                        : "table '" + table + "' missing");
                    }
                }
            }
            case Constant.DbStep.KIND_COLUMN_EXISTS -> {
                String table = check.path(Constant.DbStep.TABLE_NAME).asText();
                String column = check.path(Constant.DbStep.COLUMN_NAME).asText();
                String expectedType = check.path(Constant.DbStep.DATA_TYPE)
                        .asText("");
                String sql = dialect.columnExistsSql();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, table);
                    ps.setString(2, column);
                    ps.setQueryTimeout(timeoutSeconds);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        String actualType = rs.getString(1);
                        boolean ok = dialect.sameType(expectedType, actualType);
                        yield assertion(kind, expectedType, actualType, ok,
                                ok ? "column '" + column + "' is " + expectedType
                                        : "column '" + column + "' is " + actualType
                                        + ", expected " + expectedType);
                    }
                }
            }
            case Constant.DbStep.KIND_PRIMARY_KEY -> {
                String table = check.path(Constant.DbStep.TABLE_NAME).asText();
                String column = check.path(Constant.DbStep.COLUMN).asText();
                String sql = dialect.primaryKeySql();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, table);
                    ps.setString(2, column);
                    ps.setQueryTimeout(timeoutSeconds);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        boolean ok = rs.getInt(1) > 0;
                        yield assertion(kind, true, ok, ok,
                                ok ? "PRIMARY KEY (" + column + ") on '"
                                        + table + "'"
                                        : "PRIMARY KEY (" + column
                                        + ") missing on '" + table + "'");
                    }
                }
            }
            case Constant.DbStep.KIND_INDEX_EXISTS -> {
                String table = check.path(Constant.DbStep.TABLE_NAME).asText();
                String index = check.path(Constant.DbStep.INDEX_NAME).asText();
                String sql = dialect.indexExistsSql();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, table);
                    ps.setString(2, index);
                    ps.setQueryTimeout(timeoutSeconds);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        boolean ok = rs.getInt(1) > 0;
                        yield assertion(kind, true, ok, ok,
                                ok ? "index '" + index + "' on '" + table + "'"
                                        : "index '" + index + "' missing on '"
                                        + table + "'");
                    }
                }
            }
            default -> throw new SQLException(
                    Constant.Message.Db.UNKNOWN_CHECK_KIND + kind);
        };
    }

    /** Connection failures are wrapped by {@link
     * DbConnectionHelper} with the dialect hint as the cause;
     * check/statement failures are not. */
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
