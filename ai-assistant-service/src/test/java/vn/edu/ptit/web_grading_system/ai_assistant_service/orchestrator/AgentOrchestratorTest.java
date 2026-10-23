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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
        verify(messageRepository, never()).save(any(Message.class));
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
}
