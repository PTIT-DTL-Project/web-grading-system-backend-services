package vn.edu.ptit.web_grading_system.executor_service.service.db;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Type-keyed registry for DB dialects — bean-collected, same pattern as
 * {@code StepRegistry}: a new engine is one new {@code @Component}, no registry
 * edit. Blank/null key resolves to the default engine (postgres) so existing
 * saved configs without {@code db_type} keep working; unknown keys fail fast
 * with the allowed set in the message.
 */
@Component
public class DbDialectRegistry
{
    private final Map<String, DbDialect> byKey;
    private final DbDialect defaultDialect;

    public DbDialectRegistry(List<DbDialect> dialects)
    {
        Map<String, DbDialect> map = new HashMap<>();
        for (DbDialect dialect : dialects)
        {
            for (String key : dialect.keys())
            {
                map.put(key.toLowerCase(Locale.ROOT), dialect);
            }
        }
        this.byKey = Map.copyOf(map);
        DbDialect fallback = byKey.get(Constant.DbConnection.DEFAULT_DB_TYPE);
        if (fallback == null)
        {
            // Wiring error: the default engine must always be registered or
            // every config without db_type would NPE later.
            throw new IllegalStateException("No dialect registered for default db_type '"
                    + Constant.DbConnection.DEFAULT_DB_TYPE + "'");
        }
        this.defaultDialect = fallback;
    }

    /**
     * @param key db_type value, or null/blank for the default engine
     * @throws IllegalArgumentException on unknown key
     */
    public DbDialect resolve(String key)
    {
        if (key == null || key.isBlank())
        {
            return defaultDialect;
        }
        DbDialect dialect = byKey.get(key.trim().toLowerCase(Locale.ROOT));
        if (dialect == null)
        {
            throw new IllegalArgumentException(Constant.Message.Db.UNKNOWN_DIALECT + key
                    + " (allowed: " + String.join(", ", Constant.DbConnection.ALLOWED_DB_TYPES) + ")");
        }
        return dialect;
    }
}
