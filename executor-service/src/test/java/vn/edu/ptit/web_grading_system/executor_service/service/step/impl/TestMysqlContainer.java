package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;

import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Single live MySQL 8 container shared by the DB executor integration
 * tests. The {@code books} table is seeded once and kept clean between
 * tests with {@code DELETE FROM books}. MySQL 8 defaults to
 * {@code caching_sha2_password}, which exercises the dialect's
 * {@code useSSL=false&allowPublicKeyRetrieval=true} URL flags.
 *
 * Review: 2026-09-27, Pullfrog PR #19 — migrated off the deprecated
 * {@code org.testcontainers.containers.MySQLContainer} shim to the
 * supported {@code org.testcontainers.mysql.MySQLContainer}, which is
 * non-generic (so the {@code <?>} goes too). The index is declared
 * inline because MySQL has no {@code CREATE INDEX IF NOT EXISTS}.
 */
class TestMysqlContainer {

    static final MySQLContainer MYSQL = new MySQLContainer(
            DockerImageName.parse("mysql:8"))
            .withStartupTimeout(Duration.ofSeconds(120));

    static void start() {
        if (!MYSQL.isRunning()) MYSQL.start();
        ensureSchema();
    }

    static void stop() {
        if (MYSQL.isRunning()) MYSQL.stop();
    }

    static int port() { return MYSQL.getMappedPort(3306); }
    static String database() { return MYSQL.getDatabaseName(); }
    static String username() { return MYSQL.getUsername(); }
    static String password() { return MYSQL.getPassword(); }
    static String jdbcUrl() { return "jdbc:mysql://localhost:" + port() + "/" + database() + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"; }

    static void clearBooks() {
        try (Connection c = DriverManager.getConnection(
                jdbcUrl(), username(), password());
             Statement s = c.createStatement()) {
            s.execute("DELETE FROM books");
        } catch (Exception e) {
            throw new RuntimeException("failed to clear books", e);
        }
    }

    private static void ensureSchema() {
        try (Connection c = DriverManager.getConnection(
                jdbcUrl(), username(), password());
             Statement s = c.createStatement()) {
            s.execute("""
                    CREATE TABLE IF NOT EXISTS books (
                        id CHAR(36) PRIMARY KEY,
                        title VARCHAR(100) NOT NULL,
                        author VARCHAR(100) NOT NULL,
                        year INT NOT NULL,
                        is_active BOOL NOT NULL DEFAULT 1,
                        KEY idx_books_title (title)
                    )""");
        } catch (Exception e) {
            throw new RuntimeException("failed to seed books schema", e);
        }
    }
}
