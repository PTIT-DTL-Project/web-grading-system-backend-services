package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import reactor.core.publisher.Flux;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AiModelProperties;

import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LlmClientTest {

    @Mock
    private ChatModel chatModel;

    private AiModelProperties liveProperties;
    private AiModelProperties dummyProperties;

    @BeforeEach
    void setUp() {
        liveProperties = new AiModelProperties("openai", "sk-live-test-key", "gpt-4o-mini", "https://api.openai.com/v1");
        dummyProperties = new AiModelProperties("openai", "dummy-key", "gpt-4o-mini", "https://api.openai.com/v1");
    }

    @Test
    void testSpringAiChatModelCalledWhenKeyConfigured() {
        LlmClient client = new LlmClient(liveProperties, chatModel);

        AssistantMessage assistantOutput = new AssistantMessage("Xin chào! Tôi có thể giúp gì cho bạn?");
        Generation generation = new Generation(assistantOutput);
        ChatResponse mockResponse = new ChatResponse(List.of(generation));

        when(chatModel.call(any(Prompt.class))).thenReturn(mockResponse);

        String result = client.generateCompletion("Hệ thống WGS", "Chào bạn");

        assertNotNull(result);
        assertEquals("Xin chào! Tôi có thể giúp gì cho bạn?", result);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void testFallbackGeneratedWhenDummyKey() {
        LlmClient client = new LlmClient(dummyProperties, chatModel);

        String result = client.generateCompletion("System Prompt", "{\"steps\":[]}");

        assertNotNull(result);
        assertTrue(result.contains("overallSummary"));
        verifyNoInteractions(chatModel);
    }

    @Test
    void testStreamCompletionWithSpringAi() {
        LlmClient client = new LlmClient(liveProperties, chatModel);

        Generation gen1 = new Generation(new AssistantMessage("Xin "));
        Generation gen2 = new Generation(new AssistantMessage("chào!"));
        ChatResponse r1 = new ChatResponse(List.of(gen1));
        ChatResponse r2 = new ChatResponse(List.of(gen2));

        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(r1, r2));

        List<String> deltas = client.streamCompletion("System Prompt", "User Prompt")
                .collectList()
                .block();

        assertNotNull(deltas);
        assertEquals(2, deltas.size());
        assertEquals("Xin ", deltas.get(0));
        assertEquals("chào!", deltas.get(1));
    }

    @Test
    void streamChatWithMemoryDoesNotPersistTheUserMessageAgain() {
        LlmClient client = new LlmClient(liveProperties, chatModel);
        ChatMemory memory = mock(ChatMemory.class);
        String conversationId = "conversation-1";
        when(memory.get(conversationId)).thenReturn(List.of(new UserMessage("Danh sách lớp của tôi")));
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("Có một lớp"))))));

        List<String> output = client.streamChatWithMemory("System prompt", conversationId, memory, List.of())
                .collectList().block();

        assertEquals(List.of("Có một lớp"), output);
        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(1)).stream(promptCaptor.capture());
        assertEquals(1, promptCaptor.getValue().getInstructions().stream()
                .filter(message -> message.getMessageType() == MessageType.USER).count());
        verify(memory, never()).add(eq(conversationId), any(org.springframework.ai.chat.messages.Message.class));
        verify(memory, never()).add(eq(conversationId), anyList());
    }

    @Test
    void streamChatWithMemoryPropagatesProviderFailureInsteadOfFakeFallback() {
        LlmClient client = new LlmClient(liveProperties, chatModel);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.error(
                new RuntimeException("413 TPM Limit 8000 Requested 8163")));

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> client.streamChatWithMemory("System prompt", "conversation-1", null, List.of())
                        .collectList().block());

        assertInstanceOf(AiProviderException.class, error);
    }

    @Test
    void strictSummaryCompletionDoesNotReplaceProviderFailureWithPedagogicalJson() {
        LlmClient client = new LlmClient(liveProperties, chatModel);
        when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("provider unavailable"));
        assertThrows(AiProviderException.class,
                () -> client.generateStrictCompletion("Summarize decisions", "Decision: Friday"));
    }

    @Test
    void directToolResultEndsCurrentProviderTurnForPreflight() {
        LlmClient client = new LlmClient(liveProperties, chatModel);
        when(chatModel.getOptions()).thenReturn(org.springframework.ai.model.tool.ToolCallingChatOptions.builder().build());
        AssistantMessage toolCall = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("call-1", "function", "search_tools", "{\"query\":\"bài nộp\"}")))
                .build();
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(new Generation(toolCall)))));
        java.util.concurrent.atomic.AtomicInteger invoked = new java.util.concurrent.atomic.AtomicInteger();
        org.springframework.ai.tool.ToolCallback callback = new org.springframework.ai.tool.ToolCallback() {
            public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                return org.springframework.ai.tool.definition.ToolDefinition.builder().name("search_tools")
                        .description("search").inputSchema("{\"type\":\"object\"}").build();
            }
            public org.springframework.ai.tool.metadata.ToolMetadata getToolMetadata() {
                return org.springframework.ai.tool.metadata.ToolMetadata.builder().returnDirect(true).build();
            }
            public String call(String input) { invoked.incrementAndGet(); return "found-tool"; }
        };

        List<String> output = client.streamAgentTurn("system", List.of(new UserMessage("question")), List.of(callback))
                .collectList().block();
        assertNotNull(output);
        assertEquals(1, invoked.get(), "output=" + output);
        assertTrue(String.join("", output).contains("found-tool"), "output=" + output);
        verify(chatModel, times(1)).stream(any(Prompt.class));
    }

    @Test
    void lookupTurnForcesTheSearchTool() {
        LlmClient client = new LlmClient(liveProperties, chatModel);
        when(chatModel.getOptions()).thenReturn(org.springframework.ai.openai.OpenAiChatOptions.builder().build());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("done"))))));
        org.springframework.ai.tool.ToolCallback callback = mock(org.springframework.ai.tool.ToolCallback.class);
        when(callback.getToolDefinition()).thenReturn(org.springframework.ai.tool.definition.ToolDefinition.builder()
                .name("search_tools").description("search").inputSchema("{\"type\":\"object\"}").build());

        client.streamAgentTurn("system", List.of(new UserMessage("question")),
                List.of(callback), "search_tools").collectList().block();
        client.streamAgentTurn("system", List.of(new UserMessage("question")),
                List.of(callback), "required").collectList().block();

        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).stream(prompt.capture());
        assertEquals(java.util.Map.of("type", "function", "function", java.util.Map.of("name", "search_tools")),
                ((org.springframework.ai.openai.OpenAiChatOptions) prompt.getAllValues().get(0).getOptions()).getToolChoice());
        assertEquals("required",
                ((org.springframework.ai.openai.OpenAiChatOptions) prompt.getAllValues().get(1).getOptions()).getToolChoice());
    }

    @Test
    void classifiesExplicitChatConsentForThePendingAction() {
        LlmClient client = new LlmClient(liveProperties, chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("CONFIRM")))));

        assertEquals(LlmClient.ApprovalDecision.CONFIRM,
                client.classifyPendingActionReply("đồng ý, tạo đi", "Tạo câu kiểm tra Q1"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertTrue(prompt.getValue().getContents().contains("Tạo câu kiểm tra Q1"));
        assertTrue(prompt.getValue().getContents().contains("đồng ý, tạo đi"));
    }

    @Test
    void ambiguousClassifierOutputCannotAuthorizeAWrite() {
        LlmClient client = new LlmClient(liveProperties, chatModel);
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(
                new Generation(new AssistantMessage("CONFIRM, nếu hợp lệ")))));

        assertEquals(LlmClient.ApprovalDecision.OTHER,
                client.classifyPendingActionReply("có thể xem lại không", "Tạo câu kiểm tra Q1"));
    }
}
