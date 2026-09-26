package vn.edu.ptit.web_grading_system.executor_service.service.step;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.PostgresDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialectRegistry;
import vn.edu.ptit.web_grading_system.executor_service.service.db.MysqlDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper.ConnectionAction;
import vn.edu.ptit.web_grading_system.executor_service.service.step.DbSchemaCheckExecutor;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.VariableContext;

/**
 * Unit tests for {@link DbSchemaCheckExecutor} — logic only.
 * {@link DbConnectionHelper} and the dialect are mocked so the test
 * is hermetic and fast.
 */
class DbSchemaCheckExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DbConnectionHelper db = mock(DbConnectionHelper.class);
    private final DbSchemaCheckExecutor exec = new DbSchemaCheckExecutor(db, mapper);

    @Test
    void type_isDbSchemaCheck() {
        assertEquals(Constant.DbStep.TYPE_SCHEMA_CHECK, exec.type());
    }

    @Test
    void allChecksPass_isPassed() throws Exception {
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "checks":[
                    {"kind":"TABLE_EXISTS","table_name":"books"},
                    {"kind":"COLUMN_EXISTS","table_name":"books",
                     "column_name":"title","data_type":"varchar"},
                    {"kind":"PRIMARY_KEY","table_name":"books","column":"id"},
                    {"kind":"INDEX_EXISTS","table_name":"books",
                     "index_name":"idx_books_title"}
                ]}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        var rs = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getInt(1)).thenReturn(1);   // count > 0
        when(rs.getString(1)).thenReturn("character varying");

        when(db.withConnection(any(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(2, ConnectionAction.class);
            return action.apply(conn);
        });
        when(db.resolve("postgres")).thenReturn(new PostgresDialect());

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "s", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("TABLE_EXISTS"));
        assertTrue(result.getAssertionResult().contains("COLUMN_EXISTS"));
        assertTrue(result.getAssertionResult().contains("PRIMARY_KEY"));
        assertTrue(result.getAssertionResult().contains("INDEX_EXISTS"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void aFailingCheck_isFailedWithDetails() throws Exception {
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "checks":[
                    {"kind":"TABLE_EXISTS","table_name":"books"},
                    {"kind":"INDEX_EXISTS","table_name":"books",
                     "index_name":"missing_idx"}
                ]}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        var rs = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        // TABLE_EXISTS passes (count=1); INDEX_EXISTS fails (count=0)
        when(rs.getInt(1))
                .thenReturn(1)   // TABLE_EXISTS
                .thenReturn(0);  // INDEX_EXISTS

        when(db.withConnection(any(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(2, ConnectionAction.class);
            return action.apply(conn);
        });
        when(db.resolve("postgres")).thenReturn(new PostgresDialect());

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "s", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.FAILED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("TABLE_EXISTS"));
        assertTrue(result.getAssertionResult().contains("INDEX_EXISTS"));
        assertTrue(result.getAssertionResult().contains("missing"));
    }

    @Test
    void columnExists_usesDialectSameType() throws Exception {
        // data_type "character varying" vs expected "varchar" should
        // match via PostgresDialect.sameType.
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "checks":[{"kind":"COLUMN_EXISTS","table_name":"books",
                "column_name":"title","data_type":"varchar"}]}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        var rs = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(ps);
        when(ps.executeQuery()).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(1)).thenReturn("character varying");

        when(db.withConnection(any(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(2, ConnectionAction.class);
            return action.apply(conn);
        });
        when(db.resolve("postgres")).thenReturn(new PostgresDialect());

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "s", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("COLUMN_EXISTS"));
    }

    @Test
    void connectionFailure_returnsError() throws Exception {
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"u","password":"p"},
                "checks":[{"kind":"TABLE_EXISTS","table_name":"books"}]}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        when(db.withConnection(any(), anyInt(), any()))
                .thenThrow(new SQLException("no such host"));

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "s", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertTrue(result.getErrorMessage()
                .contains(Constant.Message.Db.SQL_EXECUTION_ERROR));
    }
}
