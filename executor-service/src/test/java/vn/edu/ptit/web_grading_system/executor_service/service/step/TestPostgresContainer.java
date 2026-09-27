package vn.edu.ptit.web_grading_system.executor_service.service.step;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Single live PostgreSQL container shared by the DB executor
 * integration tests ({@link DbQueryExecutorTest},
 * {@link DbSchemaCheckExecutorTest}, {@link DbMigrationExecutorTest}).
 * The {@code books} schema is seeded once and kept clean between
 * tests with {@code DELETE FROM books}.
 */
class TestPostgresContainer {

    static final PostgreSQLContainer<?> PG =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    static void start() {
        if (!PG.isRunning()) PG.start();
        ensureSchema();
    }

    static void stop() { PG.stop(); }

    static int port() { return PG.getMappedPort(5432); }
    static String database() { return PG.getDatabaseName(); }
    static String username() { return PG.getUsername(); }
    static String password() { return PG.getPassword(); }
    static String jdbcUrl() { return PG.getJdbcUrl(); }

    static void clearBooks() {
        try (Connection c = DriverManager.getConnection(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM books");
        } catch (Exception e) {
            throw new RuntimeException("failed to clear books", e);
        }
    }

    private static void ensureSchema() {
        try (Connection c = DriverManager.getConnection(
                PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
             Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE IF NOT EXISTS books (
                        id UUID PRIMARY KEY,
                        title VARCHAR NOT NULL,
                        author VARCHAR NOT NULL,
                        year INTEGER NOT NULL
                    )""");
            s.execute("CREATE INDEX IF NOT EXISTS idx_books_title ON books(title)");
        } catch (Exception e) {
            throw new RuntimeException("failed to seed books schema", e);
        }
    }
}
