package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
 * <p>Connection setup is retried (up to {@code CONNECT_RETRIES} or the step
 * budget, whichever expires first) because the DB container can still be
 * initialising when the first step runs (mysql:8 entrypoint takes tens of
 * seconds). The single-job gate per pod (SKILL §1) makes the
 * {@link DriverManager#setLoginTimeout(int)} call thread-safe.
 *
 * <p>Only connection-establishment failures are retried and wrapped with the
 * dialect hint. Failures thrown by {@link ConnectionAction#apply(Connection)}
 * (lecturer SQL, migrations, schema checks) propagate immediately without
 * retry and are labelled by the caller.
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
     * Opens a JDBC connection for {@code config} on {@code hostPort} within
     * {@code timeoutMs}, then runs {@code action} over it. Connection
     * failures are retried and wrapped with a {@link Constant.Message.Db}
     * dialect hint; action failures propagate immediately (not retried).
     */
    public <T> T withConnection(JsonNode config, Integer hostPort,
            int timeoutMs, ConnectionAction<T> action) throws SQLException {
        JsonNode connBlock = config.path(Constant.DbConnection.CONNECTION);
        String dbType = connBlock.path(Constant.DbConnection.DB_TYPE)
                .asString(Constant.DbConnection.DEFAULT_DB_TYPE);
        String database = connBlock.path(Constant.DbConnection.DATABASE)
                .asString("");
        String username = connBlock.path(Constant.DbConnection.USERNAME)
                .asString("");
        String password = connBlock.path(Constant.DbConnection.PASSWORD)
                .asString("");
        DbDialect dialect = dialectRegistry.resolve(dbType);
        String url = dialect.jdbcUrl(hostPort, database);
        SQLException last = null;
        long start = System.currentTimeMillis();
        int saved = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        Connection conn = null;
        try {
            for (int attempt = 1; attempt <= CONNECT_RETRIES; attempt++) {
                /* Review: 2026-09-26, Pullfrog PR #17 (round 3) —
                 * stop retrying once the step budget is spent, so a
                 * slow DB container does not hold the grading thread
                 * past EXECUTOR_MAX_EXECUTION_MS. */
                if (System.currentTimeMillis() - start > timeoutMs) {
                    last = new SQLException("Connection timed out after "
                            + timeoutMs + "ms");
                    break;
                }
                try {
                    conn = DriverManager.getConnection(url, username, password);
                    break; // connected — exit retry loop
                } catch (SQLException e) {
                    last = e;
                    if (attempt < CONNECT_RETRIES
                            && System.currentTimeMillis() - start
                                    + CONNECT_RETRY_MS <= timeoutMs) {
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
        } finally {
            DriverManager.setLoginTimeout(saved);
        }
        if (conn == null) {
            throw new SQLException(Constant.Message.Db.CONNECTION_DIALECT_PREFIX
                    + dbType + Constant.Message.Db.CONNECTION_DIALECT_SUFFIX
                    + ": " + last.getMessage(), last);
        }
        try {
            return action.apply(conn);
        } finally {
            conn.close();
        }
    }

    /** Returns the dialect registered for {@code key}, for callers that
     * need dialect-owned SQL (schema checks) beyond the connection URL. */
    public DbDialect resolve(String key) { return dialectRegistry.resolve(key); }

    @FunctionalInterface
    public interface ConnectionAction<T> { T apply(Connection conn) throws SQLException; }
}
