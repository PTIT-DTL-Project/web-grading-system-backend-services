package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentTraceTest {

    @Test
    void missingToolDescriptionDoesNotInterruptModelCall() {
        ToolCallback callback = mock(ToolCallback.class);
        ToolDefinition definition = mock(ToolDefinition.class);
        when(callback.getToolDefinition()).thenReturn(definition);
        when(definition.name()).thenReturn("readTool");
        when(definition.inputSchema()).thenReturn("{}");
        AgentContext context = new AgentContext(UUID.randomUUID(), "", java.util.Set.of(),
                UUID.randomUUID(), UUID.randomUUID(), "");

        assertDoesNotThrow(() -> AgentTrace.modelRequest(context, 1, 1, "system",
                List.of(new UserMessage("hello")), List.of(callback), null));
    }

    @Test
    void currentRunScopeCorrelatesNestedModelEventsAndClearsAfterUse() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("AI_AGENT_TRACE");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            UUID runId = UUID.randomUUID();
            AgentContext context = new AgentContext(UUID.randomUUID(), "", java.util.Set.of(),
                    UUID.randomUUID(), runId, "gateway-secret");
            try (var scope = AgentTrace.bind(context)) {
                AgentTrace.recordCurrent("model.selection.response", Map.of("content", "gateway-secret"));
            }
            AgentTrace.recordCurrent("model.selection.response", Map.of("content", "after scope"));

            assertEquals(1, captured.list.size());
            var line = new ObjectMapper().readTree(captured.list.getFirst().getFormattedMessage());
            assertEquals(runId.toString(), line.path("runId").asText());
            assertEquals("[REDACTED]", line.path("content").asText());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void writesCompleteStructuredEventWhileMaskingAuthenticationValues() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("AI_AGENT_TRACE");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            UUID runId = UUID.randomUUID();
            AgentTrace.record(runId, "gateway-secret", "tool.result", Map.of(
                    "tool", "getAssignment",
                    "input", "{\"assignmentId\":\"a4\",\"token\":\"abc\"}",
                    "output", List.of(Map.of("name", "Bài tập 4", "gatewaySecret", "gateway-secret")),
                    "prompt", "Use gateway-secret or sk-testApiKey123 to call the service"));

            var line = new ObjectMapper().readTree(captured.list.getLast().getFormattedMessage());
            assertEquals(runId.toString(), line.path("runId").asText());
            assertEquals("tool.result", line.path("event").asText());
            assertEquals("Bài tập 4", line.path("output").get(0).path("name").asText());
            assertEquals("[REDACTED]", line.path("output").get(0).path("gatewaySecret").asText());
            assertFalse(line.toString().contains("gateway-secret"));
            assertFalse(line.toString().contains("sk-testApiKey123"));
            assertFalse(line.toString().contains("abc"));
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }
}
