package vn.edu.ptit.web_grading_system.executor_service.service.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

/**
 * Unit tests for {@link DbConnectionHelper}: dialect resolution,
 * credential passing, and the connect-retry + non-retry semantics.
 */
class DbConnectionHelperTest {

    private final DbDialectRegistry registry =
            new DbDialectRegistry(List.of(new PostgresDialect(), new MysqlDialect()));
    private final DbConnectionHelper helper = new DbConnectionHelper(registry);

    private Driver recordingDriver;
    private Properties capturedProps;

    @BeforeEach
    void registerRecordingDriver() throws SQLException {
        recordingDriver = mock(Driver.class);
        when(recordingDriver.acceptsURL(anyString())).thenReturn(true);
        DriverManager.registerDriver(recordingDriver);
    }

    @AfterEach
    void deregisterRecordingDriver() throws SQLException {
        DriverManager.deregisterDriver(recordingDriver);
    }

    @Test
    void resolve_returnsPostgresForBlankAndNull() {
        assertInstanceOf(PostgresDialect.class, helper.resolve(null));
        assertInstanceOf(PostgresDialect.class, helper.resolve(""));
        assertInstanceOf(PostgresDialect.class, helper.resolve("  "));
    }

    @Test
    void resolve_returnsMysqlDialectForMysqlAndMariaDb() {
        var mysql = helper.resolve("mysql");
        var mariadb = helper.resolve("mariadb");
        assertInstanceOf(MysqlDialect.class, mysql);
        assertInstanceOf(MysqlDialect.class, mariadb);
        // mariadb is an alias — the SAME singleton instance
        assertSame(mysql, mariadb);
    }

    @Test
    void resolve_caseAndWhitespaceAreFolded() {
        assertInstanceOf(MysqlDialect.class, helper.resolve("MySQL"));
        assertInstanceOf(MysqlDialect.class, helper.resolve(" mysql "));
        assertInstanceOf(PostgresDialect.class, helper.resolve("Postgres"));
    }

    @Test
    void resolve_unknownKey_throws() {
        assertThrows(IllegalArgumentException.class, () -> helper.resolve("oracle"));
    }

    @Test
    void credentialsReadFromConnectionBlock_notConfigRoot() throws Exception {
        // The config nests every credential inside the `connection`
        // block; verify those are the ones handed to the driver
        // (not the step-config root).
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"u","password":"p"}}"""
                .stripIndent();
        var node = new tools.jackson.databind.ObjectMapper()
                .readTree(config);

        when(recordingDriver.connect(anyString(), any(Properties.class)))
                .thenAnswer(inv -> {
                    capturedProps = inv.getArgument(1);
                    return mock(Connection.class);
                });

        helper.withConnection(node, 23457, 30_000, conn -> null);

        assertEquals("u", capturedProps.getProperty("user"));
        assertEquals("p", capturedProps.getProperty("password"));
    }

    @Test
    void actionFailure_notRetriedAndNotRewrapped() throws Exception {
        // A failure thrown by the action (lecturer SQL) must propagate
        // immediately — never retried, never wrapped with the dialect
        // hint that belongs to connection-establishment failures.
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"u","password":"p"}}"""
                .stripIndent();
        var node = new tools.jackson.databind.ObjectMapper()
                .readTree(config);

        when(recordingDriver.connect(anyString(), any(Properties.class)))
                .thenReturn(mock(Connection.class));

        var ex = assertThrows(SQLException.class, () ->
                helper.withConnection(node, 23457, 30_000, conn -> {
                    throw new SQLException("relation \"books\" does not exist");
                }));
        // Verbatim — no dialect hint, no retry (connect called once).
        assertTrue(ex.getMessage().contains("relation \"books\" does not exist"));
        assertFalse(ex.getMessage().contains(Constant.Message.Db.CONNECTION_DIALECT_PREFIX));
        verify(recordingDriver, times(1)).connect(anyString(), any());
    }

    @Test
    void withConnection_refusedPort_retriesAndThrowsDialectHint() throws Exception {
        // localhost:1 is refused immediately; the helper retries
        // CONNECT_RETRIES times before wrapping in the dialect hint.
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"root","password":"root"}}"""
                .stripIndent();
        var node = new tools.jackson.databind.ObjectMapper()
                .readTree(config);

        var ex = assertThrows(SQLException.class,
                () -> helper.withConnection(node, 1, 30_000, conn -> null));
        assertTrue(ex.getMessage().contains(
                Constant.Message.Db.CONNECTION_DIALECT_PREFIX),
                "expected dialect hint in: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("mysql"),
                "expected 'mysql' in: " + ex.getMessage());
        assertNotNull(ex.getCause());
    }

}
