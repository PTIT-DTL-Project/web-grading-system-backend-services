package vn.edu.ptit.web_grading_system.ai_assistant_service.migration;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class V3MigrationSqlValidationTest {

    @Test
    void testV3MigrationFileContainsAllAgentTables() throws Exception {
        ClassPathResource resource = new ClassPathResource("db/migration/V3__2026-10-22__init_ai_agent_tables.sql");
        assertTrue(resource.exists(), "V3 migration file must exist in classpath");

        try (InputStream is = resource.getInputStream()) {
            String sql = new String(is.readAllBytes(), StandardCharsets.UTF_8).toLowerCase();
            String[] requiredTables = {
                    "ai_conversation",
                    "ai_message",
                    "ai_run",
                    "ai_task",
                    "ai_conversation_summary",
                    "ai_operation",
                    "ai_tool_execution",
                    "ai_pending_action",
                    "ai_run_event"
            };
            for (String table : requiredTables) {
                assertTrue(sql.contains("create table if not exists " + table),
                        "Migration must define table: " + table);
            }

            assertTrue(sql.contains("idx_conversation_owner"), "Must define idx_conversation_owner");
            assertTrue(sql.contains("idx_message_conversation"), "Must define idx_message_conversation");
            assertTrue(sql.contains("uq_message_client_id"), "Must define unique clientMessageId index");
            assertTrue(sql.contains("idx_run_conversation"), "Must define idx_run_conversation");
            assertTrue(sql.contains("idx_run_status"), "Must define idx_run_status");
            assertTrue(sql.contains("idx_task_conversation"), "Must define idx_task_conversation");
            assertTrue(sql.contains("idx_operation_task"), "Must define idx_operation_task");
        }
    }
}
