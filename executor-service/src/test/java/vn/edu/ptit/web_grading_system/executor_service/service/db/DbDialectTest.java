package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DbDialectTest {

    private final PostgresDialect pg = new PostgresDialect();
    private final MysqlDialect my = new MysqlDialect();

    // ---- keys ----

    @Test
    void keys_coverRegisteredEngines() {
        assertTrue(pg.keys().contains("postgres"));
        assertEquals(1, pg.keys().size());
        assertTrue(my.keys().contains("mysql"));
        assertTrue(my.keys().contains("mariadb"));
    }

    // ---- JDBC URLs ----

    @Test
    void postgresJdbcUrl_interpolatesHostPortAndDatabase() {
        assertEquals("jdbc:postgresql://localhost:33060/appdb", pg.jdbcUrl(33060, "appdb"));
    }

    @Test
    void mysqlJdbcUrl_interpolatesPortAndAuthParams() {
        String url = my.jdbcUrl(33061, "bookstore");
        assertTrue(url.startsWith("jdbc:mysql://localhost:33061/bookstore?"),
                "url was: " + url);
        // mysql:8 default auth is caching_sha2; with SSL off the client must
        // retrieve the server public key or every connection fails auth
        assertTrue(url.contains("allowPublicKeyRetrieval=true"), "url was: " + url);
        assertTrue(url.contains("useSSL=false"), "url was: " + url);
    }

    // ---- schema-check SQL: engine-specific catalogs ----

    @Test
    void schemaSql_scopedToEngineCatalog() {
        // PG information_schema spans schemas → must pin 'public';
        // MySQL table_schema IS the database → DATABASE()
        assertTrue(pg.tableExistsSql().contains("table_schema = 'public'"));
        assertTrue(my.tableExistsSql().contains("table_schema = DATABASE()"));
        assertTrue(pg.indexExistsSql().contains("pg_indexes"));
        assertTrue(my.indexExistsSql().contains("information_schema.statistics"));
        assertTrue(my.indexExistsSql().contains("DATABASE()"));
        // MySQL projects column_type (not data_type) so tinyint(1) stays
        // distinguishable for boolean; PG reports data_type (no length).
        assertTrue(pg.columnExistsSql().contains("data_type"));
        assertTrue(my.columnExistsSql().contains("column_type"));
    }

    @Test
    void schemaSql_placeholderCounts_matchDocumentedParamOrder() {
        // table: (table_name) / column: (table_name, column_name) /
        // pk: (table_name, column_name) / index: (table_name, index_name)
        for (DbDialect d : new DbDialect[]{pg, my}) {
            assertEquals(1, placeholders(d.tableExistsSql()), d.getClass().getSimpleName());
            assertEquals(2, placeholders(d.columnExistsSql()), d.getClass().getSimpleName());
            assertEquals(2, placeholders(d.primaryKeySql()), d.getClass().getSimpleName());
            assertEquals(2, placeholders(d.indexExistsSql()), d.getClass().getSimpleName());
        }
    }

    // ---- sameType: normalization per engine ----

    @Test
    void postgresSameType_normalizesAliasAndLongForm() {
        assertTrue(pg.sameType("varchar", "character varying"));
        assertTrue(pg.sameType("varchar(50)", "character varying"));
        assertTrue(pg.sameType("timestamptz", "timestamp with time zone"));
        assertTrue(pg.sameType("int", "integer"));
        assertTrue(pg.sameType("INTEGER", "integer"));
        // PG reports the 'character' and 'time' families in long form
        assertTrue(pg.sameType("char", "character"));
        assertTrue(pg.sameType("char(1)", "character"));
        assertTrue(pg.sameType("time", "time without time zone"));
        assertTrue(pg.sameType("timetz", "time with time zone"));
    }

    @Test
    void postgresSameType_differentTypes_notEqual() {
        assertFalse(pg.sameType("varchar", "integer"));
        assertFalse(pg.sameType(null, "varchar"));
        assertFalse(pg.sameType("varchar", null));
    }

    @Test
    void mysqlSameType_stripsWidthAndMapsAliases() {
        assertTrue(my.sameType("integer", "int"));
        assertTrue(my.sameType("int(11)", "integer"));
        assertTrue(my.sameType("boolean", "tinyint(1)"));
        assertTrue(my.sameType("bool", "tinyint(1)"));
        assertTrue(my.sameType("varchar(255)", "varchar"));
        // tinyint(4) (a count column) is NOT boolean — the prefix check
        // is exact so the sameType is falsy here.
        assertFalse(my.sameType("boolean", "tinyint(4)"));
        // BIT(1) is MySQL's rarer boolean spelling; folded only
        // when the lecturer asked for boolean, so `bit` vs `bit(1)`
        // (same column) still matches.
        assertTrue(my.sameType("boolean", "bit(1)"));
        assertTrue(my.sameType("bit", "bit(1)"));
        assertFalse(my.sameType("boolean", "bit(8)"));
    }

    @Test
    void mysqlSameType_differentTypes_notEqual() {
        assertFalse(my.sameType("varchar", "integer"));
        assertFalse(my.sameType("text", "varchar"));
        assertFalse(my.sameType(null, "int"));
    }

    // ---- identifier guard (F4) ----

    @Test
    void requireSafeDatabase_allowsBareIdentifiers() {
        assertDoesNotThrow(() -> DbDialect.requireSafeDatabase("appdb"));
        assertDoesNotThrow(() -> DbDialect.requireSafeDatabase("book_store"));
        assertDoesNotThrow(() -> DbDialect.requireSafeDatabase("db$1"));
    }

    @Test
    void requireSafeDatabase_rejectsInjectionChars() {
        assertThrows(IllegalArgumentException.class,
                () -> DbDialect.requireSafeDatabase("appdb?allowLoadLocalInfile=true"));
        assertThrows(IllegalArgumentException.class,
                () -> DbDialect.requireSafeDatabase("a&b"));
        assertThrows(IllegalArgumentException.class,
                () -> DbDialect.requireSafeDatabase("a/b"));
        assertThrows(IllegalArgumentException.class,
                () -> DbDialect.requireSafeDatabase("a:b"));
        assertThrows(IllegalArgumentException.class,
                () -> DbDialect.requireSafeDatabase("a#r"));
    }

    private static int placeholders(String sql) {
        int n = 0;
        for (char c : sql.toCharArray()) {
            if (c == '?') {
                n++;
            }
        }
        return n;
    }
}
