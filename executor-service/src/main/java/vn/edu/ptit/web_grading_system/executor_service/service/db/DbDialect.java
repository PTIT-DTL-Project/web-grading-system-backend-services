package vn.edu.ptit.web_grading_system.executor_service.service.db;

import java.util.Set;

/**
 * Engine-specific SQL/JDBC behavior for DB grading steps — Axis 1 of DB
 * versatility: "how to talk to the DB", keyed by {@code connection.db_type}.
 * Orthogonal to Axis 2 (image presence/pull): any engine image maps to exactly
 * one dialect. Add an engine = one {@code @Component} implementing this
 * interface; {@link DbDialectRegistry} collects it automatically (same pattern
 * as {@code StepRegistry}).
 *
 * <p>Schema-check SQL contract: each method returns a single-value query the
 * executor runs with PreparedStatement params in the documented order.
 */
public interface DbDialect
{
    /** Config keys this dialect answers to, lowercase (e.g. mysql + mariadb alias). */
    Set<String> keys();

    /** Port the DB listens on inside its container when config omits db_port. */
    int defaultPort();

    /** JDBC URL against the allocated host port (same pod, host-network path). */
    String jdbcUrl(int hostPort, String database);

    /**
     * Params: (table_name). Returns COUNT(*) — exists when value &gt; 0.
     */
    String tableExistsSql();

    /**
     * Params: (table_name, column_name). Returns the column's data_type
     * (no row = column missing) — one query covers existence AND type, so a
     * check with {@code data_type} needs no second round-trip.
     */
    String columnExistsSql();

    /**
     * Params: (table_name, column_name). Returns COUNT(*) — PK on that column
     * when value &gt; 0.
     */
    String primaryKeySql();

    /**
     * Params: (table_name, index_name). Returns COUNT(*) — index exists when
     * value &gt; 0.
     */
    String indexExistsSql();

    /**
     * Normalized type comparison for COLUMN_EXISTS {@code data_type}: dialects
     * name types differently ({@code character varying} vs {@code varchar}),
     * so equality is post-normalization, case-insensitive.
     */
    boolean sameType(String expected, String actual);
}
