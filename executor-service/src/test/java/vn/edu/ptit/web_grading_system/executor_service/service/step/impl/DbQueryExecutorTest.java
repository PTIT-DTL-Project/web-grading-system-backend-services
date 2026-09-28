package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import vn.edu.ptit.web_grading_system.executor_service.service.step.StepContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.DockerClientFactory;

import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.exception.DbConnectionException;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialectRegistry;
import vn.edu.ptit.web_grading_system.executor_service.service.db.impl.PostgresDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.service.step.impl.DbQueryExecutor;
import vn.edu.ptit.web_grading_system.executor_service.entity.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.VariableContext;

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

        var ctx = new StepContext(
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
        // {@code ctx.getTimeoutMs()}.
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
        var ctx = new StepContext(
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

        var ctx = new StepContext(
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

        var ctx = new StepContext(
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

        var ctx = new StepContext(
                java.util.UUID.randomUUID(), java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(), 1, "q", node, vars, 30000);
        var result = exec.execute(ctx);

        assertEquals(StepResultStatus.ERROR, result.getStatus());
        assertEquals(Constant.Message.Db.SQL_EXECUTION_ERROR
                + "relation \"books\" does not exist",
                result.getErrorMessage());
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

    private static String colsToJson(String[] cols) {
        var sb = new StringBuilder("[");
        for (int i = 0; i < cols.length; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(cols[i]).append('"');
        }
        return sb.append(']').toString();
    }
    private static JsonNode configQuery(String bookId, int expRows, String[] cols) {
        try {
            return new ObjectMapper().readTree("""
                    {"connection":{"db_type":"postgres","database":"%s",
                    "username":"%s","password":"%s"},
                    "query":"SELECT id, title, author FROM books WHERE id = '${bookId}'",
                    "expected":{"row_count":%d,"columns":%s}}"""
                    .formatted(TestPostgresContainer.database(),
                            TestPostgresContainer.username(), TestPostgresContainer.password(),
                            expRows, colsToJson(cols)));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static void seedBook(UUID id, String title, String author, int year) throws Exception {
        try (Connection c = DriverManager.getConnection(TestPostgresContainer.jdbcUrl(),
                TestPostgresContainer.username(), TestPostgresContainer.password());
             PreparedStatement ps = c.prepareStatement("INSERT INTO books (id,title,author,year) VALUES (?,?,?,?)")) {
            ps.setObject(1, id); ps.setString(2, title); ps.setString(3, author); ps.setInt(4, year);
            ps.executeUpdate();
        }
    }

    private static DbQueryExecutor realQueryExecutor() {
        return new DbQueryExecutor(new DbConnectionHelper(
                new DbDialectRegistry(List.of(new PostgresDialect()))), new ObjectMapper());
    }

    private StepContext stepContext(String name, JsonNode config, VariableContext vars) {
        return new StepContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, name, config, vars, 30000);
    }

    @Test
    void tc_rowCountMatch_passes() throws Exception {
        assumeDocker();
        TestPostgresContainer.clearBooks();
        UUID id = UUID.randomUUID();
        seedBook(id, "Dè Mèn", "Tâi", 1941);
        var config = configQuery(id.toString(), 1, new String[]{"id","title","author"});
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        vars.put("bookId", id.toString());
        var result = realQueryExecutor().execute(stepContext("q", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("row_count"));
        assertTrue(result.getAssertionResult().contains("columns"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_rowCountMismatch_fails() throws Exception {
        assumeDocker();
        TestPostgresContainer.clearBooks();
        var config = configQuery("00000000-0000-0000-0000-000000000003", 1, new String[]{"id","title","author"});
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        vars.put("bookId", "00000000-0000-0000-0000-000000000003");
        var result = realQueryExecutor().execute(stepContext("q", config, vars));
        assertEquals(StepResultStatus.FAILED, result.getStatus());
        assertFalse(result.getAssertionResult().isEmpty());
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_emptyResultSet_rowCountZero_passes() throws Exception {
        assumeDocker();
        TestPostgresContainer.clearBooks();
        var config = configQuery("00000000-0000-0000-0000-000000000003", 0, new String[]{"id","title","author"});
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        vars.put("bookId", "00000000-0000-0000-0000-000000000003");
        var result = realQueryExecutor().execute(stepContext("q", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("row_count"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_columnsCaseInsensitive_passes() throws Exception {
        assumeDocker();
        TestPostgresContainer.clearBooks();
        UUID id = UUID.randomUUID();
        seedBook(id, "Dè Mèn", "Tâi", 1941);
        var config = configQuery(id.toString(), 1, new String[]{"ID","TITLE","AUTHOR"});
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestPostgresContainer.port());
        vars.put("bookId", id.toString());
        var result = realQueryExecutor().execute(stepContext("q", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("columns"));
        assertNull(result.getErrorMessage());
    }
}
