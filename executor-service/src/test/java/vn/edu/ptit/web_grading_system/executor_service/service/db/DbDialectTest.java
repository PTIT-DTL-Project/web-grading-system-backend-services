package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    }

    @Test
    void mysqlSameType_differentTypes_notEqual() {
        assertFalse(my.sameType("varchar", "integer"));
        assertFalse(my.sameType("text", "varchar"));
        assertFalse(my.sameType(null, "int"));
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
