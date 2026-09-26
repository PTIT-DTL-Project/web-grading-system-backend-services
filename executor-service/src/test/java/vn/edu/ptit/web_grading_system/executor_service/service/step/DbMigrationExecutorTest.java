package vn.edu.ptit.web_grading_system.executor_service.service.step;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper.ConnectionAction;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialectRegistry;
import vn.edu.ptit.web_grading_system.executor_service.service.db.MysqlDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.step.DbMigrationExecutor;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.VariableContext;

/**
 * Unit tests for {@link DbMigrationExecutor} — logic only.
 */
class DbMigrationExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final DbConnectionHelper db = mock(DbConnectionHelper.class);
    private final DbMigrationExecutor exec = new DbMigrationExecutor(db, mapper);

    @Test
    void type_isDbMigration() {
        assertEquals(Constant.DbStep.TYPE_MIGRATION, exec.type());
    }

    @Test
    void allStatementsSucceed_isPassed() throws Exception {
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"u","password":"p"},
                "statements":[
                    "INSERT INTO books (id, title) VALUES ('1', 'A')",
                    "INSERT INTO books (id, title) VALUES ('2', 'B')"]}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(ps);

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, ConnectionAction.class);
            return action.apply(conn);
        });

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "m", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertNull(result.getErrorMessage());
        assertNull(result.getAssertionResult());
        // Verify commit and autoCommit restore happened.
        verify(conn).commit();
        verify(conn).setAutoCommit(true);
        verify(conn).setAutoCommit(false);
    }

    @Test
    void statementFailure_rollsBackAndReturnsError() throws Exception {
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"u","password":"p"},
                "statements":["INSERT INTO books VALUES ('1', 'A')"]}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        var ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(any())).thenReturn(ps);
        when(ps.executeUpdate()).thenThrow(new SQLException("duplicate key"));

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, ConnectionAction.class);
            return action.apply(conn);
        });

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "m", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertTrue(result.getErrorMessage()
                .contains(Constant.Message.Db.SQL_EXECUTION_ERROR));
        verify(conn).rollback();
        verify(conn).setAutoCommit(true); // restore even after rollback
    }
}
