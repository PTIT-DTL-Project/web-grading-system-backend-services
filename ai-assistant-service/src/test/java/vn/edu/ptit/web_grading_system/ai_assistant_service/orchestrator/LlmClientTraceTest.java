package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiModelProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LlmClientTraceTest {

    private JsonNode event(ListAppender<ILoggingEvent> captured, String name) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        for (ILoggingEvent item : captured.list) {
            JsonNode parsed = mapper.readTree(item.getFormattedMessage());
            if (name.equals(parsed.path("event").asText())) return parsed;
        }
        throw new java.util.NoSuchElementException("Missing trace event: " + name);
    }

    @Test
    void streamedReasoningMetadataIsWrittenAsJsonForTheRun() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("AI_AGENT_TRACE");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            ChatModel model = mock(ChatModel.class);
            when(model.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());
            when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                    new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                            .content("Đáp ").properties(Map.of("reasoningContent", "Kiểm tra dữ liệu. ")).build()))),
                    new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                            .content("án").properties(Map.of("reasoningContent", "Kiểm tra dữ liệu. Chọn kết quả.")).build())))));
            LlmClient client = new LlmClient(new AiModelProperties("openai", "live-test-key",
                    "test-model", "https://example.test/v1"), model);
            UUID runId = UUID.randomUUID();
            AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                    UUID.randomUUID(), runId, "");
            StringBuilder streamedReasoning = new StringBuilder();

            try (var scope = AgentTrace.bind(context)) {
                assertEquals("Đáp án", String.join("", client.streamAgentTurn("system",
                        List.of(new org.springframework.ai.chat.messages.UserMessage("question")), List.of(),
                        null, streamedReasoning::append)
                        .collectList().block()));
            }
            assertEquals("Kiểm tra dữ liệu. Chọn kết quả.", streamedReasoning.toString());

            var reasoning = event(captured, "model.reasoning");
            assertEquals(runId.toString(), reasoning.path("runId").asText());
            assertEquals("Kiểm tra dữ liệu. Chọn kết quả.", reasoning.path("content").asText());
            assertTrue(reasoning.path("available").asBoolean());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void cumulativeReasoningSnapshotsAreEmittedAsNewTextOnly() {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                        .content("").properties(Map.of("reasoningContent", "Check ")).build()))),
                new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                        .content("").properties(Map.of("reasoningContent", "Check data")).build()))),
                new ChatResponse(List.of(new Generation(AssistantMessage.builder()
                        .content("Answer").properties(Map.of("reasoningContent", "Check data")).build())))));
        LlmClient client = new LlmClient(new AiModelProperties("groq", "live-test-key",
                "openai/gpt-oss-120b", "https://example.test/openai/v1"), model);
        StringBuilder streamedReasoning = new StringBuilder();

        assertEquals("Answer", String.join("", client.streamAgentTurn("system",
                List.of(new org.springframework.ai.chat.messages.UserMessage("question")), List.of(),
                null, streamedReasoning::append).collectList().block()));
        assertEquals("Check data", streamedReasoning.toString());
    }

    @Test
    void missingReasoningMetadataIsRecordedAsUnavailable() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("AI_AGENT_TRACE");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            ChatModel model = mock(ChatModel.class);
            when(model.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());
            when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(
                    new Generation(new AssistantMessage("Answer"))))));
            LlmClient client = new LlmClient(new AiModelProperties("openai", "live-test-key",
                    "test-model", "https://example.test/v1"), model);
            AgentContext context = new AgentContext(UUID.randomUUID(), "", Set.of("ROLE_STUDENT"),
                    UUID.randomUUID(), UUID.randomUUID(), "");

            try (var scope = AgentTrace.bind(context)) {
                assertEquals("Answer", String.join("", client.streamAgentTurn("system",
                        List.of(new org.springframework.ai.chat.messages.UserMessage("question")), List.of())
                        .collectList().block()));
            }

            var reasoning = event(captured, "model.reasoning");
            assertTrue(!reasoning.path("available").asBoolean());
            assertEquals("", reasoning.path("content").asText());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }

    @Test
    void toolSelectionCapturesCompleteModelPromptAndRawReplyForRun() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("AI_AGENT_TRACE");
        ListAppender<ILoggingEvent> captured = new ListAppender<>();
        captured.start();
        logger.addAppender(captured);
        try {
            ChatModel model = mock(ChatModel.class);
            when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                    new Generation(AssistantMessage.builder().content("[\"listMyClasses\"]")
                            .properties(Map.of("reasoningContent", "Need class lookup.")).build()))));
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

            var request = event(captured, "model.request.toolSelection");
            var response = event(captured, "model.response.toolSelection");
            assertEquals(runId.toString(), request.path("runId").asText());
            assertTrue(request.path("prompt").asText().contains("lớp của tôi"));
            assertTrue(request.path("prompt").asText().contains("listMyClasses"));
            assertEquals("[\"listMyClasses\"]", response.path("content").asText());
            var reasoning = event(captured, "model.reasoning");
            assertEquals("Need class lookup.", reasoning.path("content").asText());
        } finally {
            logger.detachAppender(captured);
            captured.stop();
        }
    }
}
