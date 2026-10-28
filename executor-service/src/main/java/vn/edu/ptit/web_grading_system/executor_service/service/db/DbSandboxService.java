package vn.edu.ptit.web_grading_system.executor_service.service.db;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import vn.edu.ptit.web_grading_system.executor_service.Constant;

import java.sql.SQLException;
import java.sql.Statement;

/**
 * Resets the student DB to a pristine state between plans so each plan
 * starts with no leftover data from the previous plan.
 *
 * <p>The reset is a drop+recreate of the target database, executed
 * against the system database ({@code postgres} for PG, {@code mysql}
 * for MySQL/MariaDB) via JDBC. PostgreSQL also terminates existing
 * connections to the target database before dropping it.
 *
 * <p>This runs inside the grading pod after each plan completes and
 * before the next plan begins. The compose container (app + DB) stays
 * up for the whole job; only the database contents are reset.
 */
@Component
public class DbSandboxService {

    private final DbConnectionHelper db;
    private final ObjectMapper mapper;

    public DbSandboxService(DbConnectionHelper db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    /**
     * Resets {@code connectionConfig.database} to an empty database on
     * {@code hostPort}. No-op when the connection block has no database
     * name. Any failure is wrapped in a {@link RuntimeException} so the
     * orchestrator can fail the job with a clear message.
     */
    public void resetDatabase(JsonNode connectionConfig, Integer hostPort) {
        JsonNode connBlock = connectionConfig.path(Constant.DbConnection.CONNECTION);
        String database = connBlock.path(Constant.DbConnection.DATABASE).asString("");
        if (database.isBlank()) {
            return;
        }
        String dbType = connBlock.path(Constant.DbConnection.DB_TYPE)
                .asString(Constant.DbConnection.DEFAULT_DB_TYPE);
        try {
            db.withConnection(buildSystemConfig(connectionConfig, dbType), hostPort,
                    Constant.DbSandbox.RESET_TIMEOUT_MS, conn -> {
                        try (Statement stmt = conn.createStatement()) {
                            if ("postgres".equals(dbType)) {
                                // Terminate existing connections so DROP DATABASE succeeds.
                                stmt.execute(
                                        Constant.DbSandbox.PG_TERMINATE_CONNECTIONS.formatted(database));
                            }
                            stmt.execute(getDropSql(dbType, database));
                            stmt.execute(getCreateSql(dbType, database));
                        }
                        return null;
                    });
        } catch (SQLException e) {
            throw new RuntimeException(
                    "DB sandbox reset failed for database '" + database + "': " + e.getMessage(), e);
        }
    }

    /**
     * Returns a copy of the connection block with {@code database}
     * replaced by the engine's system database, so we can connect
     * to run DROP/CREATE without being connected to the target.
     */
    private JsonNode buildSystemConfig(JsonNode connectionConfig, String dbType) {
        String systemDb = switch (dbType) {
            case "mysql", "mariadb" -> Constant.DbSandbox.MYSQL_SYSTEM_DB;
            default -> Constant.DbSandbox.PG_SYSTEM_DB;
        };
        JsonNode conn = connectionConfig.path(Constant.DbConnection.CONNECTION);
        ObjectNode copy = mapper.createObjectNode();
        conn.properties().forEach(e -> copy.set(e.getKey(), e.getValue().deepCopy()));
        copy.put(Constant.DbConnection.DATABASE, systemDb);
        ObjectNode outer = mapper.createObjectNode();
        outer.set(Constant.DbConnection.CONNECTION, copy);
        return outer;
    }

    private String getDropSql(String dbType, String database) {
        return switch (dbType) {
            case "mysql", "mariadb" -> Constant.DbSandbox.MYSQL_DROP_DB.formatted(database);
            default -> Constant.DbSandbox.PG_DROP_DB.formatted(database);
        };
    }

    private String getCreateSql(String dbType, String database) {
        return switch (dbType) {
            case "mysql", "mariadb" -> Constant.DbSandbox.MYSQL_CREATE_DB.formatted(database);
            default -> Constant.DbSandbox.PG_CREATE_DB.formatted(database);
        };
    }
}
