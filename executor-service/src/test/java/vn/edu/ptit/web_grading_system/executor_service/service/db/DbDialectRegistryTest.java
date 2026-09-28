package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import vn.edu.ptit.web_grading_system.executor_service.service.db.impl.MysqlDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.impl.PostgresDialect;

class DbDialectRegistryTest {

    private final DbDialectRegistry registry = new DbDialectRegistry(
            List.of(new PostgresDialect(), new MysqlDialect()));

    @Test
    void nullKey_resolvesDefaultEngine() {
        assertInstanceOf(PostgresDialect.class, registry.resolve(null));
    }

    @Test
    void blankKey_resolvesDefaultEngine() {
        assertInstanceOf(PostgresDialect.class, registry.resolve("  "));
    }

    @Test
    void postgresKey_resolvesPostgresDialect() {
        assertInstanceOf(PostgresDialect.class, registry.resolve("postgres"));
    }

    @Test
    void mysqlKey_resolvesMysqlDialect() {
        assertInstanceOf(MysqlDialect.class, registry.resolve("mysql"));
    }

    @Test
    void mariadbKey_isAliasOfMysqlDialect() {
        // mariadb is wire-compatible → same instance, one driver, one SQL set
        assertSame(registry.resolve("mysql"), registry.resolve("mariadb"));
    }

    @Test
    void keyMatchingIsCaseInsensitive() {
        assertInstanceOf(MysqlDialect.class, registry.resolve("MYSQL"));
        assertInstanceOf(PostgresDialect.class, registry.resolve("Postgres"));
    }

    @Test
    void unknownKey_throwsListingAllowedValuesInOrder() {
        var e = assertThrows(IllegalArgumentException.class,
                () -> registry.resolve("oracle"));
        assertTrue(e.getMessage().contains("oracle"));
        // deterministic — built from sorted byKey.keySet()
        assertTrue(e.getMessage().contains("mariadb, mysql, postgres"),
                "message was: " + e.getMessage());
    }

    @Test
    void defaultPort_followsEngine() {
        assertEquals(5432, registry.resolve(null).defaultPort());
        assertEquals(5432, registry.resolve("postgres").defaultPort());
        assertEquals(3306, registry.resolve("mysql").defaultPort());
        assertEquals(3306, registry.resolve("mariadb").defaultPort());
    }

    @Test
    void duplicateKey_throwsFast() {
        // wiring guard: two @Component dialects must not claim the same key.
        // PostgresDialect has a single-element key set (Set.of("postgres")), so the
        // reported key is structurally deterministic. Two MysqlDialect would flake:
        // Set.of("mysql","mariadb") iteration order is fixed for the life of the JVM
        // (ImmutableCollections.SALT), so a launch that puts "mariadb" first makes
        // contains("mysql") false — the run fails outright rather than flaking per call.
        // Review: 2026-09-28, Pullfrog PR #23
        var e = assertThrows(IllegalStateException.class,
                () -> new DbDialectRegistry(List.of(new PostgresDialect(), new PostgresDialect())));
        assertTrue(e.getMessage().contains("postgres"));
        assertTrue(e.getMessage().contains("PostgresDialect"));
    }

    @Test
    void constructor_withoutDefaultEngine_failsFast() {
        // wiring error guard: configs without db_type always resolve to
        // postgres, so the default engine must be registered
        assertThrows(IllegalStateException.class,
                () -> new DbDialectRegistry(List.of(new MysqlDialect())));
    }
}
