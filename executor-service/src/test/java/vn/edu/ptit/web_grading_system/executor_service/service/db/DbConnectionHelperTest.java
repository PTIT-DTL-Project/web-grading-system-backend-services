package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

/**
 * Unit tests for {@link DbConnectionHelper}: dialect resolution and
 * the connect-retry + dialect-hint behaviour.
 */
class DbConnectionHelperTest {

    private final DbDialectRegistry registry =
            new DbDialectRegistry(List.of(new PostgresDialect(), new MysqlDialect()));
    private final DbConnectionHelper helper = new DbConnectionHelper(registry);

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
    void withConnection_refusedPort_retriesAndThrowsDialectHint() {
        // localhost:1 is refused immediately; the helper retries
        // CONNECT_RETRIES times before wrapping in the dialect hint.
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"root","password":"root"}}"""
                .stripIndent();
        var node = new tools.jackson.databind.ObjectMapper()
                .readTree(config);

        var ex = assertThrows(SQLException.class,
                () -> helper.withConnection(node, 1, conn -> null));
        // The final exception carries the dialect hint, not the
        // raw refused-port message alone.
        assertTrue(ex.getMessage().contains(
                Constant.Message.Db.CONNECTION_DIALECT_PREFIX),
                "expected dialect hint in: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("mysql"),
                "expected 'mysql' in: " + ex.getMessage());
        // The root cause is the refused connection.
        assertNotNull(ex.getCause());
    }
}
