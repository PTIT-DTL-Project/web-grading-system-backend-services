package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import java.util.List;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.exception.DbConnectionException;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialectRegistry;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.impl.PostgresDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.step.impl.DbSchemaCheckExecutor;
import vn.edu.ptit.web_grading_system.executor_service.entity.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.VariableContext;

/**
 * Unit tests for {@link DbSchemaCheckExecutor} — logic only.
 * Each check kind uses its own mocked {@link PreparedStatement}
 * and {@link ResultSet} so the four switch arms are independently
 * verifiable, and {@code verify(conn).prepareStatement(...)} pins
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
    void connectionFailure_returnsDialectHintVerbatim() throws Exception {
        // DbConnectionException is surfaced verbatim — no prefix,
        // no doubling.
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"u","password":"p"},
                "checks":[{"kind":"TABLE_EXISTS","table_name":"books"}]}"""
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
                java.util.UUID.randomUUID(), 1, "s", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertEquals("dialect hint: set connection.db_type",
                result.getErrorMessage());
    }

    @Test
    void statementFailure_returnsErrorWithPrefixOnce() throws Exception {
        // Every non-connection, non-timeout SQLException gets
        // SQL_EXECUTION_ERROR exactly once.
        var node = mapper.readTree("""
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "checks":[{"kind":"TABLE_EXISTS","table_name":"books"}]}""");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        when(db.withConnection(any(), anyInt(), anyInt(), any()))
                .thenThrow(new SQLException("no such host"));

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "s", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertEquals(Constant.Message.Db.SQL_EXECUTION_ERROR + "no such host",
                result.getErrorMessage());
    }
    @Test
    void configTimeoutMs_isHonouredOverCtxTimeoutMs() throws Exception {
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},"timeoutMs":2000,
                "checks":[{"kind":"TABLE_EXISTS","table_name":"books"}]}"""
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

        when(db.withConnection(any(), anyInt(), anyInt(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            var action = inv.getArgument(3, DbConnectionHelper.ConnectionAction.class);
            return action.apply(conn);
        });
        when(db.resolve("postgres")).thenReturn(new PostgresDialect());

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "s", node, vars, null);
        exec.execute(ctx);

        verify(ps).setQueryTimeout(2);
    }

    @Test
    void budgetExhausted_throwsTimeout() throws Exception {
        // A spent budget is checked before any statement runs, so
        // no PreparedStatement is created and the error carries
        // the timeout label without a dialect hint.
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"},
                "checks":[{"kind":"TABLE_EXISTS","table_name":"books"}],
                "timeoutMs":0}"""
                .stripIndent();
        var node = mapper.readTree(config);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, 23457);

        var conn = mock(Connection.class);
        when(db.withConnection(any(), anyInt(), anyInt(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    var action = inv.getArgument(3, DbConnectionHelper.ConnectionAction.class);
                    return action.apply(conn);
                });
        when(db.resolve("postgres")).thenReturn(new PostgresDialect());

        var ctx = new HttpStepExecutor.StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "s", node, vars, null);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertTrue(result.getErrorMessage()
                .contains(Constant.Message.Db.SQL_TIMEOUT_ERROR));
        assertFalse(result.getErrorMessage()
                .contains(Constant.Message.Db.CONNECTION_DIALECT_PREFIX));
        verify(conn, never()).prepareStatement(any());
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

    private static JsonNode configSchemaCheck(String kind, String table, String col, String dtype) {
        try {
            return new ObjectMapper().readTree("""
                    {"connection":{"db_type":"postgres","database":"%s",
                    "username":"%s","password":"%s"},
                    "checks":[{"kind":"%s","table_name":"%s"%s}],
                    "timeoutMs":30000}"""
                    .formatted(TestPostgresContainer.database(),
                            TestPostgresContainer.username(), TestPostgresContainer.password(),
                            kind, table,
                            (col == null) ? "" : ",\"column_name\":\"%s\",\"data_type\":\"%s\"".formatted(col, dtype)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static JsonNode configSchemaCheck(String kind, String table) {
        try {
            return new ObjectMapper().readTree("""
                    {"connection":{"db_type":"postgres","database":"%s",
                    "username":"%s","password":"%s"},
                    "checks":[{"kind":"%s","table_name":"%s"}],
                    "timeoutMs":30000}"""
                    .formatted(TestPostgresContainer.database(),
                            TestPostgresContainer.username(), TestPostgresContainer.password(),
                            kind, table));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static DbSchemaCheckExecutor realSchemaExecutor() {
        return new DbSchemaCheckExecutor(new DbConnectionHelper(
                new DbDialectRegistry(List.of(new PostgresDialect()))), new ObjectMapper());
    }

    private HttpStepExecutor.StepContext stepContext(String name, JsonNode config, VariableContext vars) {
        return new HttpStepExecutor.StepContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, name, config, vars, 30000);
    }

    @Test
    void tc_tableExists_true() throws Exception {
        assumeDocker();
        var config = configSchemaCheck("TABLE_EXISTS", "books");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realSchemaExecutor().execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("TABLE_EXISTS"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_tableExists_false() throws Exception {
        assumeDocker();
        var config = configSchemaCheck("TABLE_EXISTS", "no_such_table");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realSchemaExecutor().execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.FAILED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("TABLE_EXISTS"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_columnExists_normalizesVarchar() throws Exception {
        assumeDocker();
        var config = configSchemaCheck("COLUMN_EXISTS", "books", "title", "VARCHAR");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realSchemaExecutor().execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("COLUMN_EXISTS"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_primaryKey() throws Exception {
        assumeDocker();
        var config = new ObjectMapper().readTree("""
                {"connection":{"db_type":"postgres","database":"%s",
                "username":"%s","password":"%s"},
                "checks":[{"kind":"PRIMARY_KEY","table_name":"books","column":"id"}],
                "timeoutMs":30000}""".formatted(TestPostgresContainer.database(),
                        TestPostgresContainer.username(), TestPostgresContainer.password()));
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realSchemaExecutor().execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("PRIMARY_KEY"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_indexExists() throws Exception {
        assumeDocker();
        var config = new ObjectMapper().readTree("""
                {"connection":{"db_type":"postgres","database":"%s",
                "username":"%s","password":"%s"},
                "checks":[{"kind":"INDEX_EXISTS","table_name":"books","index_name":"idx_books_title"}],
                "timeoutMs":30000}""".formatted(TestPostgresContainer.database(),
                        TestPostgresContainer.username(), TestPostgresContainer.password()));
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realSchemaExecutor().execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("INDEX_EXISTS"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_mixedChecks_firstFail_marksFailed() throws Exception {
        assumeDocker();
        // first check (type mismatch) fails without throwing; second passes.
        var config = new ObjectMapper().readTree("""
                {"connection":{"db_type":"postgres","database":"%s",
                "username":"%s","password":"%s"},
                "checks":[{"kind":"COLUMN_EXISTS","table_name":"books",
                           "column_name":"title","data_type":"INT"},
                          {"kind":"TABLE_EXISTS","table_name":"books"}],
                "timeoutMs":30000}""".formatted(TestPostgresContainer.database(),
                        TestPostgresContainer.username(), TestPostgresContainer.password()));
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        var result = realSchemaExecutor().execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.FAILED, result.getStatus());
        // Review: 2026-09-27, Pullfrog PR #19 — prove the executor evaluated
        // BOTH checks (aggregation), not just the first failing one: exactly
        // two details, the first (type-mismatch) fails and the second
        // (TABLE_EXISTS) passes, so short-circuiting cannot satisfy this test.
        var details = new ObjectMapper().readTree(result.getAssertionResult());
        assertEquals(2, details.size());
        assertFalse(details.get(0).path("passed").asBoolean());
        assertTrue(details.get(1).path("passed").asBoolean());
        assertNull(result.getErrorMessage());
    }
}
