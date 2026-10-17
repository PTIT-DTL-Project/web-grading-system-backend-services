package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpStepExecutorTelemetryTest {

    @Test
    void extractSqlQueries_filtersOnlyValidSqlStatements() {
        String logContent = """
            2026-10-15 00:01:00 UTC LOG: statement: SELECT * FROM books WHERE id = 1;
            2026-10-15 00:01:01 UTC LOG: statement: SELECT * FROM authors WHERE book_id = 1;
            2026-10-15 00:01:02 UTC LOG: checkpoint starting: time
        """;
        List<String> queries = HttpStepExecutor.extractSqlQueries(logContent);
        assertThat(queries).hasSize(2);
        assertThat(queries.get(0)).contains("SELECT * FROM books");
    }

    @Test
    void extractStackTrace_capturesJavaStackTraceOnServerError() {
        String errorBody = """
            {"timestamp":"2026-10-15T00:00:00Z","status":500,"error":"Internal Server Error","trace":"java.lang.NullPointerException: Cannot invoke method\\n\\tat com.example.demo.service.UserService.getUser(UserService.java:42)"}
        """;
        String trace = HttpStepExecutor.extractStackTrace(errorBody);
        assertThat(trace).contains("UserService.java:42");
    }
}
