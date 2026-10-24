package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiProviderException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.EffectType;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolDefinition;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolRegistry;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AgentOrchestratorTest {

    @Mock
    private RunRepository runRepository;
    @Mock
    private ConversationRepository conversationRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private TaskRepository taskRepository;
    @Mock
    private RunEventRepository runEventRepository;
    @Mock
    private GuardChecker guardChecker;
    @Mock
    private ContextBuilder contextBuilder;
    @Mock
    private SseBroadcaster sseBroadcaster;
    @Mock
    private LlmClient llmClient;

    private AgentOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new AgentOrchestrator(
                runRepository,
                conversationRepository,
                messageRepository,
                taskRepository,
                runEventRepository,
                guardChecker,
                contextBuilder,
                sseBroadcaster,
                llmClient
        );
    }

    @Test
    void executeRun_emptyStreamDoesNotSendSecondPrompt() {
        UUID runId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();

        Run run = Run.builder()
                .id(runId)
                .conversationId(convId)
                .status(RunStatus.QUEUED)
                .build();

        Conversation conv = Conversation.builder()
                .id(convId)
                .ownerId(UUID.randomUUID())
                .title("Test Chat")
                .build();

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(guardChecker.canContinue(any(), eq(0))).thenReturn(true);
        when(contextBuilder.buildSystemPrompt(any(), any())).thenReturn("System prompt");
        when(llmClient.streamChatWithMemory(any(), any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.empty());

        orchestrator.executeRun(runId);

        assertEquals(RunStatus.FAILED, run.getStatus());
        verify(llmClient, never()).generateCompletion(any(), any());
        verify(sseBroadcaster).sendEvent(eq(runId), eq("run.failed"), any());
        verify(sseBroadcaster).complete(eq(runId));
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void executeRun_streamingExecution() {
        UUID runId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();

        Run run = Run.builder()
                .id(runId)
                .conversationId(convId)
                .status(RunStatus.QUEUED)
                .build();

        Conversation conv = Conversation.builder()
                .id(convId)
                .ownerId(UUID.randomUUID())
                .title("Test Streaming")
                .build();

        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(guardChecker.canContinue(any(), eq(0))).thenReturn(true);
        when(contextBuilder.buildSystemPrompt(any(), any())).thenReturn("System prompt");
        when(llmClient.streamChatWithMemory(any(), any(), any(), any())).thenReturn(reactor.core.publisher.Flux.just("Chunk 1", " Chunk 2"));
        when(messageRepository.findMaxSequenceNo(convId)).thenReturn(1);

        orchestrator.executeRun(runId);

        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        verify(sseBroadcaster).sendDelta(eq(runId), eq("Chunk 1"));
        verify(sseBroadcaster).sendDelta(eq(runId), eq(" Chunk 2"));
        verify(sseBroadcaster).sendEvent(eq(runId), eq("run.completed"), any());
        verify(sseBroadcaster).complete(eq(runId));
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    void executeRun_failedWriteOverridesFalseSuccessFromModel() {
        UUID runId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Run run = Run.builder().id(runId).conversationId(convId).status(RunStatus.QUEUED).build();
        Conversation conv = Conversation.builder().id(convId).ownerId(UUID.randomUUID()).build();
        ToolRegistry registry = mock(ToolRegistry.class);
        orchestrator = new AgentOrchestrator(runRepository, conversationRepository, messageRepository,
                taskRepository, runEventRepository, guardChecker, contextBuilder, sseBroadcaster,
                llmClient, registry, null, null);
        AgentTool writeTool = mock(AgentTool.class);
        when(writeTool.getDefinition()).thenReturn(new ToolDefinition("createAssignment", "Create", "1.0",
                "lecturer", "write", "assignment", EffectType.DOMAIN_WRITE,
                Set.of("ROLE_LECTURER"), Duration.ofSeconds(10), 1000));
        when(registry.getToolCallbacksForRoles(any(), any(), any())).thenAnswer(invocation -> {
            BiConsumer<AgentTool, ToolResult> observer = invocation.getArgument(2);
            observer.accept(writeTool, ToolResult.error("VALIDATION_ERROR", "Thiếu gradingStrategy", false));
            return List.of();
        });
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(guardChecker.canContinue(any(), eq(0))).thenReturn(true);
        when(contextBuilder.buildSystemPrompt(any(), any())).thenReturn("System prompt");
        when(llmClient.streamChatWithMemory(any(), any(), any(), any()))
                .thenReturn(reactor.core.publisher.Flux.just("Đã tạo thành công!"));

        orchestrator.executeRun(runId);

        assertEquals(RunStatus.FAILED, run.getStatus());
        org.mockito.ArgumentCaptor<Message> message = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(message.capture());
        org.junit.jupiter.api.Assertions.assertTrue(message.getValue().getContent().contains("Thiếu gradingStrategy"));
        org.junit.jupiter.api.Assertions.assertFalse(message.getValue().getContent().contains("thành công"));
        verify(sseBroadcaster).sendEvent(eq(runId), eq("assistant.replace"), any());
    }

    @Test
    void providerLimitFailureShowsGenericMessageWithoutQuotaDetails() {
        UUID runId = UUID.randomUUID();
        UUID convId = UUID.randomUUID();
        Run run = Run.builder().id(runId).conversationId(convId).status(RunStatus.QUEUED).build();
        Conversation conv = Conversation.builder().id(convId).ownerId(UUID.randomUUID()).build();
        when(runRepository.findById(runId)).thenReturn(Optional.of(run));
        when(runRepository.save(any(Run.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(conversationRepository.findById(convId)).thenReturn(Optional.of(conv));
        when(guardChecker.canContinue(any(), eq(0))).thenReturn(true);
        when(contextBuilder.buildSystemPrompt(any(), any())).thenReturn("System prompt");
        when(llmClient.streamChatWithMemory(any(), any(), any(), any())).thenReturn(
                reactor.core.publisher.Flux.error(new AiProviderException(
                        new RuntimeException("413 TPM Limit 8000 Requested 8163"))));

        orchestrator.executeRun(runId);

        assertEquals(RunStatus.FAILED, run.getStatus());
        assertEquals("AI_PROVIDER_UNAVAILABLE", run.getErrorCode());
        org.junit.jupiter.api.Assertions.assertFalse(run.getErrorMessage().contains("8000"));
        org.mockito.ArgumentCaptor<Message> saved = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(messageRepository).save(saved.capture());
        org.junit.jupiter.api.Assertions.assertEquals(run.getErrorMessage(), saved.getValue().getContent());
        verify(sseBroadcaster).sendEvent(eq(runId), eq("assistant.replace"), eq(run.getErrorMessage()));
    }
}
