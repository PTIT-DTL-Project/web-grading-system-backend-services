package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import vn.edu.ptit.web_grading_system.executor_service.service.step.StepContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import tools.jackson.databind.JsonNode;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.util.UUID;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;

import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialectRegistry;
import vn.edu.ptit.web_grading_system.executor_service.service.db.impl.PostgresDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper.ConnectionAction;
import vn.edu.ptit.web_grading_system.executor_service.service.step.impl.DbMigrationExecutor;
import vn.edu.ptit.web_grading_system.executor_service.entity.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.VariableContext;

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

        var ctx = new StepContext(
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

        var ctx = new StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "m", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertEquals(Constant.Message.Db.SQL_EXECUTION_ERROR + "duplicate key",
                result.getErrorMessage());
        verify(conn).rollback();
        verify(conn).setAutoCommit(true); // restore even after rollback
    }

    @Test
    void configTimeoutMs_isHonouredOverCtxTimeoutMs() throws Exception {
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"u","password":"p"},
                "statements":["INSERT INTO books VALUES ('1','A')"],
                "timeoutMs":2000}"""
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

        // ctx timeoutMs is null → the config key decides.
        var ctx = new StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "m", node,
                vars, null);
        exec.execute(ctx);

        verify(ps).setQueryTimeout(2);
    }

    @Test
    void budgetExhausted_throwsTimeoutAndRollsBack() throws Exception {
        // A spent budget inside the statement list must raise a
        // timeout (not a dialect hint) and still roll back the
        // in-progress transaction.
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"u","password":"p"},
                "statements":["INSERT INTO books VALUES ('1','A')"],
                "timeoutMs":0}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        when(conn.prepareStatement(any())).thenReturn(mock(PreparedStatement.class));

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, ConnectionAction.class);
            return action.apply(conn);
        });

        var ctx = new StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "m", node,
                vars, null);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertTrue(result.getErrorMessage()
                .contains(Constant.Message.Db.SQL_TIMEOUT_ERROR));
        assertFalse(result.getErrorMessage()
                .contains(Constant.Message.Db.CONNECTION_DIALECT_PREFIX));
        assertEquals(Constant.Message.Db.SQL_TIMEOUT_ERROR + "0ms",
                result.getErrorMessage());
        verify(conn).rollback();
        verify(conn).setAutoCommit(true);
    }


    // Review: 2026-09-27, Pullfrog PR #19 — scope the Docker assumption
    // to the container-backed tests only. A class-level @BeforeAll
    // assumeTrue aborts the whole class container, so the pre-existing
    // Mockito tests in this class vanish (Tests run: 0, Skipped: 0) on a
    // Docker-less runner.
    private static void assumeDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    // ---- container-backed (live PostgreSQL) tests ----
    @BeforeAll
    static void startContainer() {
        if (DockerClientFactory.instance().isDockerAvailable()) TestPostgresContainer.start();
    }

    @AfterAll
    static void stopContainer() {
        TestPostgresContainer.stop();
    }

    private static JsonNode configMigration(String... statements) {
        try {
            var sb = new StringBuilder();
            for (int i = 0; i < statements.length; i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(statements[i].replace("\"", "\\\"")).append('"');
            }
            return new ObjectMapper().readTree("""
                    {"connection":{"db_type":"postgres","database":"%s",
                    "username":"%s","password":"%s"},
                    "statements":[%s],"timeoutMs":30000}"""
                    .formatted(TestPostgresContainer.database(),
                            TestPostgresContainer.username(), TestPostgresContainer.password(),
                            sb));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static DbMigrationExecutor realMigrationExecutor() {
        return new DbMigrationExecutor(new DbConnectionHelper(
                new DbDialectRegistry(List.of(new PostgresDialect()))), new ObjectMapper());
    }

    private StepContext stepContext(String name, JsonNode config, VariableContext vars) {
        return new StepContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, name, config, vars, 30000);
    }

    @Test
    void tc_allStatementsSucceed_commits() throws Exception {
        assumeDocker();
        TestPostgresContainer.clearBooks();
        var config = configMigration("INSERT INTO books (id,title,author,year) VALUES ('00000000-0000-0000-0000-000000000001','A','B',2000)");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realMigrationExecutor().execute(stepContext("m", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertNull(result.getErrorMessage());
        try (Connection c = DriverManager.getConnection(TestPostgresContainer.jdbcUrl(),
                TestPostgresContainer.username(), TestPostgresContainer.password());
             var rs = c.prepareStatement("SELECT COUNT(*) FROM books WHERE id='00000000-0000-0000-0000-000000000001'").executeQuery()) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }

    @Test
    void tc_statementFails_rollsBack() throws Exception {
        assumeDocker();
        TestPostgresContainer.clearBooks();
        var config = configMigration("INSERT INTO books (id,title,author,year) VALUES ('00000000-0000-0000-0000-000000000002','A','B',2000)",
                "INSERT INTO books (id,title,author,year) VALUES ('00000000-0000-0000-0000-000000000002','C','D',2001)");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realMigrationExecutor().execute(stepContext("m", config, vars));
        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertTrue(result.getErrorMessage().startsWith(Constant.Message.Db.SQL_EXECUTION_ERROR));
        try (Connection c = DriverManager.getConnection(TestPostgresContainer.jdbcUrl(),
                TestPostgresContainer.username(), TestPostgresContainer.password());
             var rs = c.prepareStatement("SELECT COUNT(*) FROM books WHERE id='00000000-0000-0000-0000-000000000002'").executeQuery()) {
            rs.next();
            assertEquals(0, rs.getInt(1));
        }
    }

    @Test
    void tc_ddlRollbackOnPg_atomic() throws Exception {
        assumeDocker();
        TestPostgresContainer.clearBooks();
        var config = configMigration("CREATE TABLE ddl_tmp (x int)", "INSERT INTO ddl_tmp (x) VALUES ('not_an_int')");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realMigrationExecutor().execute(stepContext("m", config, vars));
        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertTrue(result.getErrorMessage().startsWith(Constant.Message.Db.SQL_EXECUTION_ERROR));
        // DDL must be rolled back too: the table must not exist.
        // Review: 2026-09-27, Pullfrog PR #19 — narrow the assertion to the
        // missing-relation error so a connection failure (which also throws
        // SQLException) cannot satisfy this check.
        var ex = assertThrows(java.sql.SQLException.class, () -> {
            try (Connection c = DriverManager.getConnection(TestPostgresContainer.jdbcUrl(),
                    TestPostgresContainer.username(), TestPostgresContainer.password());
                 var stmt = c.prepareStatement("SELECT COUNT(*) FROM ddl_tmp")) {
                stmt.executeQuery();
            }
        });
        assertTrue(ex.getMessage().contains("ddl_tmp"));
        assertTrue(ex.getMessage().contains("does not exist"));
    }
}
