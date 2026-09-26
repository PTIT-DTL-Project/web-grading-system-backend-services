package vn.edu.ptit.web_grading_system.executor_service.service.step;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.PostgresDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.step.DbSchemaCheckExecutor;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.VariableContext;

/**
 * Unit tests for {@link DbSchemaCheckExecutor} — logic only.
 * Each check kind uses its own mocked {@link PreparedStatement}
 * and {@link ResultSet} so the four switch arms are independently
 * verifiable, and {@link Mockito#verify(Object, Object...)} pins
 * the parameter order that {@link DbDialect} javadoc specifies.
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
        var tablePs = mock(PreparedStatement.class);
        var tableRs = mock(ResultSet.class);
        var colPs = mock(PreparedStatement.class);
        var colRs = mock(ResultSet.class);
        var pkPs = mock(PreparedStatement.class);
        var pkRs = mock(ResultSet.class);
        var idxPs = mock(PreparedStatement.class);
        var idxRs = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(tablePs, colPs, pkPs, idxPs);
        when(tablePs.executeQuery()).thenReturn(tableRs);
        when(colPs.executeQuery()).thenReturn(colRs);
        when(pkPs.executeQuery()).thenReturn(pkRs);
        when(idxPs.executeQuery()).thenReturn(idxRs);
        when(tableRs.next()).thenReturn(true);
        when(tableRs.getInt(1)).thenReturn(1);
        when(colRs.next()).thenReturn(true);
        when(colRs.getString(1)).thenReturn("character varying");
        when(pkRs.next()).thenReturn(true);
        when(pkRs.getInt(1)).thenReturn(1);
        when(idxRs.next()).thenReturn(true);
        when(idxRs.getInt(1)).thenReturn(1);

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, DbConnectionHelper.ConnectionAction.class);
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
        // Parameter order per DbDialect javadoc:
        verify(tablePs).setString(1, "books");
        verify(colPs).setString(1, "books");
        verify(colPs).setString(2, "title");
        verify(pkPs).setString(1, "books");
        verify(pkPs).setString(2, "id");
        verify(idxPs).setString(1, "books");
        verify(idxPs).setString(2, "idx_books_title");
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
        var tablePs = mock(PreparedStatement.class);
        var tableRs = mock(ResultSet.class);
        var idxPs = mock(PreparedStatement.class);
        var idxRs = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(tablePs, idxPs);
        when(tablePs.executeQuery()).thenReturn(tableRs);
        when(idxPs.executeQuery()).thenReturn(idxRs);
        when(tableRs.next()).thenReturn(true);
        when(tableRs.getInt(1)).thenReturn(1);   // TABLE_EXISTS passes
        when(idxRs.next()).thenReturn(true);
        when(idxRs.getInt(1)).thenReturn(0);     // INDEX_EXISTS fails

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, DbConnectionHelper.ConnectionAction.class);
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
        var colPs = mock(PreparedStatement.class);
        var colRs = mock(ResultSet.class);
        when(conn.prepareStatement(any())).thenReturn(colPs);
        when(colPs.executeQuery()).thenReturn(colRs);
        when(colRs.next()).thenReturn(true);
        when(colRs.getString(1)).thenReturn("character varying");

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, DbConnectionHelper.ConnectionAction.class);
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

        when(db.withConnection(any(), anyInt(), anyInt(), any()))
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
