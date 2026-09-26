package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/**
 * MySQL dialect — answers to both {@code mysql} and {@code mariadb} keys
 * (mariadb is wire-compatible; one driver, one set of SQL). Schema scope uses
 * {@code DATABASE()} because MySQL's information_schema.table_schema is the
 * database name itself.
 */
@Component
public class MysqlDialect implements DbDialect
{
    @Override
    public Set<String> keys()
    {
        return Set.of("mysql", "mariadb");
    }

    @Override
    public int defaultPort()
    {
        return 3306;
    }

    @Override
    public String jdbcUrl(int hostPort, String database)
    {
        // Defensive: the executor reads config over an internal API.
        // Review: 2026-09-26, Pullfrog PR #17 (F4).
        DbDialect.requireSafeDatabase(database);
        // Docker mysql:8 defaults to caching_sha2_password; with SSL off the
        // client must be allowed to retrieve the server public key, otherwise
        // every connection fails auth.
        return "jdbc:mysql://localhost:" + hostPort + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    @Override
    public String tableExistsSql()
    {
        return "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = DATABASE() AND table_name = ?";
    }

    @Override
    public String columnExistsSql()
    {
        // column_type retains tinyint(1)/varchar(255) while data_type
        // is the bare name, so boolean (tinyint(1)) stays distinguishable
        // (MySQL 8 + MariaDB). Review: 2026-09-26, Pullfrog PR #17 (F2).
        return "SELECT column_type FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?";
    }

    @Override
    public String primaryKeySql()
    {
        return "SELECT COUNT(*) FROM information_schema.table_constraints tc "
                + "JOIN information_schema.key_column_usage kcu "
                + "ON tc.constraint_schema = kcu.constraint_schema AND tc.constraint_name = kcu.constraint_name "
                + "WHERE tc.table_schema = DATABASE() AND tc.table_name = ? "
                + "AND tc.constraint_type = 'PRIMARY KEY' AND kcu.column_name = ?";
    }

    @Override
    public String indexExistsSql()
    {
        return "SELECT COUNT(*) FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?";
    }

    @Override
    public boolean sameType(String expected, String actual)
    {
        if (expected == null || actual == null)
        {
            return false;
        }
        // BIT(1) is MySQL's rarer boolean spelling: fold it to
        // boolean only when the lecturer asked for boolean, so
        // `bit` vs `bit(1)` (same column, different spellings)
        // still matches. normalize alone can't do this symmetrically
        // because it width-strips bit(1)→bit, which is correct for
        // the bit-vs-bit case but would break boolean-vs-bit(1).
        // Review: 2026-09-26, Pullfrog PR #17 (round 2).
        if ("boolean".equals(normalize(expected))) {
            String raw = actual.trim().toLowerCase(Locale.ROOT);
            return "boolean".equals(normalize(actual)) || raw.equals("bit(1)");
        }
        return normalize(expected).equals(normalize(actual));
    }

    /**
     * MySQL naming: BOOL/BOOLEAN are aliases stored as tinyint(1);
     * the rarer BIT(1) spelling is also boolean. Display widths
     * ({@code varchar(255)}, {@code int(11)}) are not part of the type.
     */
    private static String normalize(String type)
    {
        String raw = type.trim().toLowerCase(Locale.ROOT);
        if (raw.startsWith("tinyint(1)"))
        {
            return "boolean";
        }
        int paren = raw.indexOf('(');
        String t = paren > 0 ? raw.substring(0, paren).trim() : raw;
        return switch (t)
        {
            case "bool", "boolean" -> "boolean";
            case "int", "integer" -> "integer";
            default -> t;
        };
    }
}
