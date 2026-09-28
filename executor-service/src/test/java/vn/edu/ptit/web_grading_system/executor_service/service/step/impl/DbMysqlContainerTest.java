package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import vn.edu.ptit.web_grading_system.executor_service.service.step.StepContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbConnectionHelper;
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialectRegistry;
import vn.edu.ptit.web_grading_system.executor_service.service.db.impl.MysqlDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.impl.PostgresDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.step.impl.DbMigrationExecutor;
import vn.edu.ptit.web_grading_system.executor_service.entity.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.service.scoring.VariableContext;

/**
 * MySQL container tests for the DB executors — validates
 * {@code MysqlDialect} end-to-end against a live MySQL 8 server,
 * including the {@code caching_sha2_password} auth negotiated by
 * the dialect's {@code useSSL=false&allowPublicKeyRetrieval=true}
 * URL flags.
 *
 * Review: 2026-09-27, Pullfrog PR #19 — nitpicks: dead imports removed,
 * clearBooks scoped to the one test that mutates a row, one call per line.
 */
class DbMysqlContainerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    // Registry MUST contain the default engine (postgres) or its
    // constructor rejects the registry; the tests below resolve
    // mysql explicitly, so MysqlDialect is exercised end-to-end.
    private final DbConnectionHelper db = new DbConnectionHelper(
            new DbDialectRegistry(List.of(new PostgresDialect(), new MysqlDialect())));

    @BeforeAll
    static void startContainer() {
        if (DockerClientFactory.instance().isDockerAvailable()) TestMysqlContainer.start();
    }

    @AfterAll
    static void stopContainer() {
        TestMysqlContainer.stop();
    }

    private static void assumeDocker() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable());
    }

    private static void clearBooks() {
        TestMysqlContainer.clearBooks();
    }

    private static JsonNode configQuery() {
        try {
            return new ObjectMapper().readTree("""
                    {"connection":{"db_type":"mysql","database":"%s",
                    "username":"%s","password":"%s"},
                    "query":"SELECT 1 AS n",
                    "expected":{"row_count":1,"columns":["n"]}}"""
                    .formatted(TestMysqlContainer.database(),
                            TestMysqlContainer.username(), TestMysqlContainer.password()));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static JsonNode configSchemaCheck(String kind, String table, String col, String dtype) {
        try {
            var extra = (col == null) ? "" : ",\"column_name\":\"%s\",\"data_type\":\"%s\"".formatted(col, dtype);
            return new ObjectMapper().readTree("""
                    {"connection":{"db_type":"mysql","database":"%s",
                    "username":"%s","password":"%s"},
                    "checks":[{"kind":"%s","table_name":"%s"%s}],
                    "timeoutMs":30000}"""
                    .formatted(TestMysqlContainer.database(),
                            TestMysqlContainer.username(), TestMysqlContainer.password(),
                            kind, table, extra));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static JsonNode configMigration(String... statements) {
        try {
            var sb = new StringBuilder();
            for (int i = 0; i < statements.length; i++) {
                if (i > 0) sb.append(',');
                sb.append('"').append(statements[i].replace("\"", "\\\"")).append('"');
            }
            return new ObjectMapper().readTree("""
                    {"connection":{"db_type":"mysql","database":"%s",
                    "username":"%s","password":"%s"},
                    "statements":[%s],"timeoutMs":30000}"""
                    .formatted(TestMysqlContainer.database(),
                            TestMysqlContainer.username(), TestMysqlContainer.password(),
                            sb));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private DbMigrationExecutor realMigrationExecutor() {
        return new DbMigrationExecutor(db, mapper);
    }

    private static StepContext stepContext(String name, JsonNode config, VariableContext vars) {
        return new StepContext(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                1, name, config, vars, 30000);
    }

    @Test
    void tc_queryAuth_works() throws Exception {
        assumeDocker();
        var config = configQuery();
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestMysqlContainer.port());
        var result = new DbQueryExecutor(db, mapper).execute(stepContext("q", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("row_count"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_tableExists_true() throws Exception {
        assumeDocker();
        var config = configSchemaCheck("TABLE_EXISTS", "books", null, null);
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestMysqlContainer.port());
        var result = new DbSchemaCheckExecutor(db, mapper).execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("TABLE_EXISTS"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_columnExists_varcharNormalizes() throws Exception {
        assumeDocker();
        var config = configSchemaCheck("COLUMN_EXISTS", "books", "title", "VARCHAR");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestMysqlContainer.port());
        var result = new DbSchemaCheckExecutor(db, mapper).execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("COLUMN_EXISTS"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_columnExists_booleanNeedsColumnType() throws Exception {
        assumeDocker();
        // The fixture declares is_active BOOL, which MySQL stores as
        // tinyint(1). columnExistsSql() projects column_type, so the
        // check only passes via column_type (data_type would give bare
        // tinyint, which normalize() does not map to boolean). Pinning
        // actual to "tinyint(1)" proves the projection observationally.
        var config = configSchemaCheck("COLUMN_EXISTS", "books", "is_active", "BOOLEAN");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestMysqlContainer.port());
        var result = new DbSchemaCheckExecutor(db, mapper).execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        var details = mapper.readTree(result.getAssertionResult());
        assertEquals(1, details.size());
        assertEquals("tinyint(1)", details.get(0).path("actual").asString());
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_primaryKey() throws Exception {
        assumeDocker();
        var config = new ObjectMapper().readTree("""
                {"connection":{"db_type":"mysql","database":"%s",
                "username":"%s","password":"%s"},
                "checks":[{"kind":"PRIMARY_KEY","table_name":"books","column":"id"}],
                "timeoutMs":30000}""".formatted(TestMysqlContainer.database(),
                        TestMysqlContainer.username(), TestMysqlContainer.password()));
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestMysqlContainer.port());
        var result = new DbSchemaCheckExecutor(db, mapper).execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("PRIMARY_KEY"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_indexExists() throws Exception {
        assumeDocker();
        var config = new ObjectMapper().readTree("""
                {"connection":{"db_type":"mysql","database":"%s",
                "username":"%s","password":"%s"},
                "checks":[{"kind":"INDEX_EXISTS","table_name":"books","index_name":"idx_books_title"}],
                "timeoutMs":30000}""".formatted(TestMysqlContainer.database(),
                        TestMysqlContainer.username(), TestMysqlContainer.password()));
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestMysqlContainer.port());
        var result = new DbSchemaCheckExecutor(db, mapper).execute(stepContext("s", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertTrue(result.getAssertionResult().contains("INDEX_EXISTS"));
        assertNull(result.getErrorMessage());
    }

    @Test
    void tc_migrationCommit() throws Exception {
        assumeDocker();
        clearBooks();
        var config = configMigration(
                "INSERT INTO books (id,title,author,year) VALUES ('00000000-0000-0000-0000-000000000004','A','B',2000)");
        var vars = new VariableContext();
        vars.put(Constant.VariableContext.DB_PORT, TestMysqlContainer.port());
        var result = realMigrationExecutor().execute(stepContext("m", config, vars));
        assertEquals(StepResultStatus.PASSED, result.getStatus());
        assertNull(result.getErrorMessage());
        try (Connection c = DriverManager.getConnection(TestMysqlContainer.jdbcUrl(),
                TestMysqlContainer.username(), TestMysqlContainer.password());
             var rs = c.prepareStatement("SELECT COUNT(*) FROM books WHERE id='00000000-0000-0000-0000-000000000004'").executeQuery()) {
            rs.next();
            assertEquals(1, rs.getInt(1));
        }
    }
}
