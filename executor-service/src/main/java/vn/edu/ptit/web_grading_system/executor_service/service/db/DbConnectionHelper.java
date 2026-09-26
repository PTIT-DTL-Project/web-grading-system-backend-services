package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import vn.edu.ptit.web_grading_system.executor_service.Constant;

/**
 * Opens a JDBC connection for a DB step and runs a callback over it.
 *
 * <p>The engine comes from the step's own {@code connection.db_type} (never
 * inferred from the compose image name) and is resolved through the dialect
 * registry, so URL, driver and schema filters are dialect-owned.
 *
 * <p>Connection setup is retried because the DB container can still be
 * initialising when the first step runs (mysql:8 entrypoint takes tens of
 * seconds). The single-job gate per pod (SKILL §1) makes the
 * {@link DriverManager#setLoginTimeout(int)} call thread-safe.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DbConnectionHelper {

    private static final int CONNECT_RETRIES = 5;
    private static final int CONNECT_RETRY_MS = 1_000;
    private static final int LOGIN_TIMEOUT_SECONDS = 5;

    private final DbDialectRegistry dialectRegistry;

    /**
     * Opens a JDBC connection for {@code connection} on {@code hostPort}
     * and runs {@code action} over it, retrying {@code CONNECT_RETRIES}
     * times on connection failure. The connection is closed when the
     * action returns or throws.
     */
    public <T> T withConnection(JsonNode connection, Integer hostPort,
            ConnectionAction<T> action) throws SQLException {
        JsonNode connBlock = connection.path(Constant.DbConnection.CONNECTION);
        String dbType = connBlock.path(Constant.DbConnection.DB_TYPE)
                .asString(Constant.DbConnection.DEFAULT_DB_TYPE);
        String database = connBlock.path(Constant.DbConnection.DATABASE)
                .asString("");
        String username = connection.path(Constant.DbConnection.USERNAME)
                .asString("");
        String password = connection.path(Constant.DbConnection.PASSWORD)
                .asString("");
        DbDialect dialect = dialectRegistry.resolve(dbType);
        String url = dialect.jdbcUrl(hostPort, database);
        SQLException last = null;
        for (int attempt = 1; attempt <= CONNECT_RETRIES; attempt++) {
            int saved = DriverManager.getLoginTimeout();
            DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
            try (Connection conn = DriverManager.getConnection(url, username, password)) {
                DriverManager.setLoginTimeout(saved);
                return action.apply(conn);
            } catch (SQLException e) {
                DriverManager.setLoginTimeout(saved);
                last = e;
                if (attempt < CONNECT_RETRIES) {
                    log.warn("DB connection attempt {}/{} failed for {}: {}. Retrying...",
                            attempt, CONNECT_RETRIES, dbType, e.getMessage());
                    try {
                        Thread.sleep(CONNECT_RETRY_MS);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("DB connection interrupted", ie);
                    }
                }
            }
        }
        throw new SQLException(Constant.Message.Db.CONNECTION_DIALECT_PREFIX
                + dbType + Constant.Message.Db.CONNECTION_DIALECT_SUFFIX
                + ": " + last.getMessage(), last);
    }

    /** Returns the dialect registered for {@code key}, for callers that
     * need dialect-owned SQL (schema checks) beyond the connection URL. */
    public DbDialect resolve(String key) { return dialectRegistry.resolve(key); }

    @FunctionalInterface
    public interface ConnectionAction<T> { T apply(Connection conn) throws SQLException; }
}
