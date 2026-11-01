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
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            assertTrue(captured.list.getLast().getFormattedMessage().contains("\n  \"runId\""));
            assertEquals(runId.toString(), line.path("runId").asText());
            assertEquals("tool.result", line.path("event").asText());
            assertEquals("Bài tập 4", line.path("output").get(0).path("name").asText());
            assertEquals("[REDACTED]", line.path("output").get(0).path("gatewaySecret").asText());
            assertEquals("a4", line.path("inputJson").path("assignmentId").asText());
            assertFalse(line.toString().contains("gateway-secret"));
            assertFalse(line.toString().contains("sk-testApiKey123"));
            assertFalse(line.toString().contains("abc"));
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void keepsExactPromptAndAddsReadableLines() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("AI_AGENT_TRACE");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            String prompt = "System instruction\nRead class names before choosing an ID";
            AgentTrace.record(UUID.randomUUID(), "", "model.request", Map.of("systemPrompt", prompt));

            var event = new ObjectMapper().readTree(captured.list.getLast().getFormattedMessage());
            assertEquals(prompt, event.path("systemPrompt").asText());
            assertEquals("System instruction", event.path("systemPromptLines").get(0).asText());
            assertEquals("Read class names before choosing an ID",
                    event.path("systemPromptLines").get(1).asText());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void groupsInterleavedEventsIntoOneReadableBlockPerPrompt() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("AI_AGENT_TRACE");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            UUID first = UUID.randomUUID();
            UUID second = UUID.randomUUID();
            AgentTrace.record(first, "", "run.started", Map.of("conversationId", "conversation-1"));
            AgentTrace.record(second, "", "run.started", Map.of("conversationId", "conversation-2"));
            AgentTrace.record(first, "", "agent.start", Map.of("userPrompt", "Prompt one"));
            AgentTrace.record(second, "", "agent.start", Map.of("userPrompt", "Prompt two"));
            AgentTrace.record(first, "", "model.request", Map.of("modelStep", 1));
            AgentTrace.record(second, "", "tool.result", Map.of("output", "done"));
            AgentTrace.record(first, "", "run.completed", Map.of("status", "SUCCEEDED"));
            AgentTrace.record(second, "", "run.failed", Map.of("status", "FAILED"));

            assertEquals(2, captured.list.size());
            var firstBlock = new ObjectMapper().readTree(captured.list.get(0).getFormattedMessage());
            var secondBlock = new ObjectMapper().readTree(captured.list.get(1).getFormattedMessage());
            assertEquals(first.toString(), firstBlock.path("runId").asText());
            assertEquals("Prompt one", firstBlock.path("userPrompt").asText());
            assertEquals("SUCCEEDED", firstBlock.path("status").asText());
            assertEquals("run.started", firstBlock.path("events").get(0).path("event").asText());
            assertEquals("agent.start", firstBlock.path("events").get(1).path("event").asText());
            assertEquals("model.request", firstBlock.path("events").get(2).path("event").asText());
            assertEquals("run.completed", firstBlock.path("events").get(3).path("event").asText());
            assertEquals(second.toString(), secondBlock.path("runId").asText());
            assertEquals("Prompt two", secondBlock.path("userPrompt").asText());
            assertEquals("FAILED", secondBlock.path("status").asText());
            assertEquals(4, secondBlock.path("events").size());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }
}
