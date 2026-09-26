package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * PostgreSQL dialect. Schema scope is fixed to {@code public} — student apps
 * run with the default search_path, and PG's information_schema spans all
 * schemas (unlike MySQL, where table_schema is the database itself).
 */
@Component
public class PostgresDialect implements DbDialect
{
    @Override
    public Set<String> keys()
    {
        return Set.of("postgres");
    }

    @Override
    public int defaultPort()
    {
        return 5432;
    }

    @Override
    public String jdbcUrl(int hostPort, String database)
    {
        // Defensive: the executor reads config over an internal API.
        // Review: 2026-09-26, Pullfrog PR #17 (F4).
        DbDialect.requireSafeDatabase(database);
        return "jdbc:postgresql://localhost:" + hostPort + "/" + database;
    }

    @Override
    public String tableExistsSql()
    {
        return "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name = ?";
    }

    @Override
    public String columnExistsSql()
    {
        return "SELECT data_type FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?";
    }

    @Override
    public String primaryKeySql()
    {
        return "SELECT COUNT(*) FROM information_schema.table_constraints tc "
                + "JOIN information_schema.key_column_usage kcu "
                + "ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema "
                + "WHERE tc.table_schema = 'public' AND tc.table_name = ? "
                + "AND tc.constraint_type = 'PRIMARY KEY' AND kcu.column_name = ?";
    }

    @Override
    public String indexExistsSql()
    {
        return "SELECT COUNT(*) FROM pg_indexes "
                + "WHERE schemaname = 'public' AND tablename = ? AND indexname = ?";
    }

    @Override
    public boolean sameType(String expected, String actual)
    {
        if (expected == null || actual == null)
        {
            return false;
        }
        return normalize(expected).equals(normalize(actual));
    }

    /**
     * PG reports long-form type names; lecturers write the alias
     * (possibly with a length, e.g. varchar(50), which PG's
     * catalog never reports). Also maps the {@code character} and
     * {@code time} families that PG reports in long form.
     * Review: 2026-09-26, Pullfrog PR #17 (F3).
     */
    private static String normalize(String type)
    {
        String raw = type.trim().toLowerCase(Locale.ROOT);
        int paren = raw.indexOf('(');
        String t = paren > 0 ? raw.substring(0, paren).trim() : raw;
        return switch (t)
        {
            case "character varying" -> "varchar";
            case "timestamp with time zone" -> "timestamptz";
            case "timestamp without time zone" -> "timestamp";
            case "double precision" -> "float8";
            case "bool" -> "boolean";
            case "int" -> "integer";
            case "character" -> "char";
            case "time without time zone" -> "time";
            case "time with time zone" -> "timetz";
            default -> t;
        };
    }
}
