package vn.edu.ptit.web_grading_system.executor_service.service.db;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.Constant;

class DbSandboxServiceTest {

    private final DbConnectionHelper db = mock(DbConnectionHelper.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final DbSandboxService service = new DbSandboxService(db, mapper);

    @Test
    void resetDatabase_postgres_dropCreateAndTerminate() throws Exception {
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"postgres","password":"postgres"}}""";
        var node = mapper.readTree(config);

        List<String> executed = new ArrayList<>();
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute(anyString())).thenAnswer(inv -> {
            executed.add(inv.getArgument(0));
            return true;
        });

        when(db.withConnection(any(), any(), anyInt(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    DbConnectionHelper.ConnectionAction<Void> action =
                            (DbConnectionHelper.ConnectionAction<Void>) inv.getArgument(3);
                    return action.apply(conn);
                });

        service.resetDatabase(node, 23457);

        // Connect to PG system DB (postgres), not appdb
        verify(db).withConnection(argThat(cfg ->
                        cfg.path(Constant.DbConnection.CONNECTION)
                                .path(Constant.DbConnection.DATABASE)
                                .asString()
                                .equals("postgres")),
                any(), anyInt(), any());

        // PG: terminate connections first, then DROP, then CREATE
        assertEquals(3, executed.size());
        assertTrue(executed.get(0).contains("pg_terminate_backend"),
                "expected terminate: " + executed.get(0));
        assertEquals("DROP DATABASE IF EXISTS appdb", executed.get(1));
        assertEquals("CREATE DATABASE appdb", executed.get(2));
    }

    @Test
    void resetDatabase_mysql_dropCreate() throws Exception {
        var config = """
                {"connection":{"db_type":"mysql","database":"appdb",
                "username":"root","password":"root"}}""";
        var node = mapper.readTree(config);

        List<String> executed = new ArrayList<>();
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute(anyString())).thenAnswer(inv -> {
            executed.add(inv.getArgument(0));
            return true;
        });

        when(db.withConnection(any(), any(), anyInt(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    DbConnectionHelper.ConnectionAction<Void> action =
                            (DbConnectionHelper.ConnectionAction<Void>) inv.getArgument(3);
                    return action.apply(conn);
                });

        service.resetDatabase(node, 23457);

        // MySQL: connect to system DB (mysql), DROP + CREATE only
        verify(db).withConnection(argThat(cfg ->
                        cfg.path(Constant.DbConnection.CONNECTION)
                                .path(Constant.DbConnection.DATABASE)
                                .asString()
                                .equals("mysql")),
                any(), anyInt(), any());

        assertEquals(2, executed.size());
        assertEquals("DROP DATABASE IF EXISTS appdb", executed.get(0));
        assertEquals("CREATE DATABASE appdb", executed.get(1));
    }

    @Test
    void resetDatabase_mariaDb_usesMysqlSql() throws Exception {
        var config = """
                {"connection":{"db_type":"mariadb","database":"appdb",
                "username":"root","password":"root"}}""";
        var node = mapper.readTree(config);

        List<String> executed = new ArrayList<>();
        Connection conn = mock(Connection.class);
        Statement stmt = mock(Statement.class);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.execute(anyString())).thenAnswer(inv -> {
            executed.add(inv.getArgument(0));
            return true;
        });

        when(db.withConnection(any(), any(), anyInt(), any()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    DbConnectionHelper.ConnectionAction<Void> action =
                            (DbConnectionHelper.ConnectionAction<Void>) inv.getArgument(3);
                    return action.apply(conn);
                });

        service.resetDatabase(node, 23457);

        assertEquals(2, executed.size());
        assertEquals("DROP DATABASE IF EXISTS appdb", executed.get(0));
        assertEquals("CREATE DATABASE appdb", executed.get(1));
    }

    @Test
    void resetDatabase_noDatabase_noOp() throws Exception {
        var config = "{\"connection\":{\"db_type\":\"postgres\"}}";
        var node = mapper.readTree(config);

        service.resetDatabase(node, 23457);

        // No database name → no connection attempted
        verify(db, never()).withConnection(any(), any(), anyInt(), any());
    }

    @Test
    void resetDatabase_connectionFailure_wrapsInRuntimeException() throws Exception {
        var config = """
                {"connection":{"db_type":"postgres","database":"appdb",
                "username":"postgres","password":"postgres"}}""";
        var node = mapper.readTree(config);
        when(db.withConnection(any(), any(), anyInt(), any()))
                .thenThrow(new SQLException("connection refused"));

        var ex = assertThrows(RuntimeException.class,
                () -> service.resetDatabase(node, 23457));
        assertTrue(ex.getMessage().contains("DB sandbox reset failed"));
        assertTrue(ex.getMessage().contains("appdb"));
        assertTrue(ex.getCause() instanceof SQLException);
    }
}
