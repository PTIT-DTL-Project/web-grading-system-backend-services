package vn.edu.ptit.web_grading_system.executor_service.service.step;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionException;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.service.step.DbQueryExecutor;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.VariableContext;

/**
 * Unit tests for {@link DbQueryExecutor} — logic only (the JDBC
 * connection is provided by a mocked {@link DbConnectionHelper}).
 */
class DbQueryExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DbConnectionHelper db = mock(DbConnectionHelper.class);
    private final DbQueryExecutor exec = new DbQueryExecutor(db, mapper);

    @Test
    void type_isDbQuery() {
        assertEquals(Constant.DbStep.TYPE_QUERY, exec.type());
    }

    @Test
    void successfulQueryWithMatchingRowCountAndColumns_isPassed() throws Exception {
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "query":"SELECT id, title FROM books WHERE id = ${bookId}",
                "expected":{"row_count":1,"columns":["id","title"]}}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.APP_PORT, 8080);
        vars.put(Constant.VariableContext.DB_PORT, 23457);
        vars.put("bookId", "7");

        var conn = mock(Connection.class);
        var stmt = mock(Statement.class);
        var rs = mock(ResultSet.class);
        var meta = mock(ResultSetMetaData.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any())).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(2);
        when(meta.getColumnLabel(1)).thenReturn("id");
        when(meta.getColumnLabel(2)).thenReturn("title");
        when(rs.next()).thenReturn(true).thenReturn(false); // one row

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, DbConnectionHelper.ConnectionAction.class);
            return action.apply(conn);
        });

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "q", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("row_count"));
        assertTrue(result.getAssertionResult().contains("columns"));
        assertNull(result.getErrorMessage());
        // The substituted SQL reached the driver, not the template.
        verify(stmt).executeQuery("SELECT id, title FROM books WHERE id = 7");
    }

    @Test
    void configTimeoutMs_isHonouredOverCtxTimeoutMs() throws Exception {
        // A {@code timeoutMs} key in the step config overrides
        // {@code ctx.timeoutMs()}.
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "query":"SELECT 1","timeoutMs":2000}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        var stmt = mock(Statement.class);
        var rs = mock(ResultSet.class);
        var meta = mock(ResultSetMetaData.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any())).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(1);
        when(rs.next()).thenReturn(false);

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, DbConnectionHelper.ConnectionAction.class);
            return action.apply(conn);
        });

        // ctx timeoutMs is null → the config key decides.
        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "q", node,
                vars, null);
        exec.execute(ctx);

        verify(stmt).setQueryTimeout(2);
    }

    @Test
    void rowCountMismatch_isFailedWithAssertion() throws Exception {
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "query":"SELECT * FROM books",
                "expected":{"row_count":3}}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        var stmt = mock(Statement.class);
        var rs = mock(ResultSet.class);
        var meta = mock(ResultSetMetaData.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any())).thenReturn(rs);
        when(rs.getMetaData()).thenReturn(meta);
        when(meta.getColumnCount()).thenReturn(1);
        when(rs.next()).thenReturn(true).thenReturn(true).thenReturn(false); // 2 rows

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, DbConnectionHelper.ConnectionAction.class);
            return action.apply(conn);
        });

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "q", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.FAILED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("row_count"));
        assertFalse(result.getAssertionResult().contains("columns"));
    }

    @Test
    void connectionFailure_returnsDialectHintVerbatim() throws Exception {
        // DbConnectionException is surfaced verbatim — no
        // SQL_EXECUTION_ERROR prefix and no doubling.
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"u","password":"p"},
                "query":"SELECT 1"}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        when(db.withConnection(any(), anyInt(), anyInt(), any()))
                .thenThrow(new DbConnectionException(
                        "dialect hint: set connection.db_type",
                        new SQLException("no driver")));

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "q", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertEquals("dialect hint: set connection.db_type",
                result.getErrorMessage());
        assertNull(result.getAssertionResult());
    }

    @Test
    void statementFailure_returnsErrorWithPrefixOnce() throws Exception {
        // Every non-connection, non-timeout SQLException gets
        // SQL_EXECUTION_ERROR exactly once.
        var node = mapper.readTree("""
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "query":"SELECT 1"}""");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        when(db.withConnection(any(), anyInt(), anyInt(), any()))
                .thenThrow(new SQLException(
                        "relation \"books\" does not exist"));

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "q", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertEquals(Constant.Message.Db.SQL_EXECUTION_ERROR
                + "relation \"books\" does not exist",
                result.getErrorMessage());
    }
}
