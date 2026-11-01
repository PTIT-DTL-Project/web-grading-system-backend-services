package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiModelProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmClientTraceTest {

    @Test
    void toolSelectionCapturesCompleteModelPromptAndRawReplyForRun() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("AI_AGENT_TRACE");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            ChatModel model = mock(ChatModel.class);
            when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                    new Generation(new AssistantMessage("[\"listMyClasses\"]")))));
            AgentTool tool = mock(AgentTool.class);
            when(tool.getName()).thenReturn("listMyClasses");
            when(tool.getDescription()).thenReturn("List accessible classes");
            when(tool.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                    .name("listMyClasses").description("List accessible classes")
                    .inputSchema("{\"type\":\"object\",\"properties\":{}}")
                    .build());
            LlmClient client = new LlmClient(new AiModelProperties("openai", "live-test-key",
                    "test-model", "https://example.test/v1"), model);
            UUID runId = UUID.randomUUID();
            AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                    UUID.randomUUID(), runId, "");

            try (var scope = AgentTrace.bind(context)) {
                assertEquals(List.of("listMyClasses"), client.chooseRelevantTools("lớp của tôi", List.of(tool)));
            }

            var mapper = new ObjectMapper();
            var request = mapper.readTree(captured.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(line -> line.contains("model.request.toolSelection")).findFirst().orElseThrow());
            var response = mapper.readTree(captured.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(line -> line.contains("model.response.toolSelection")).findFirst().orElseThrow());
            assertEquals(runId.toString(), request.path("runId").asText());
            assertTrue(request.path("prompt").asText().contains("lớp của tôi"));
            assertTrue(request.path("prompt").asText().contains("listMyClasses"));
            assertEquals("[\"listMyClasses\"]", response.path("content").asText());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }
}
