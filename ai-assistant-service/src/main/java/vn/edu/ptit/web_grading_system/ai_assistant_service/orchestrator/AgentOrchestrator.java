package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunEvent;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.memory.JpaChatMemory;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolRegistry;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class AgentOrchestrator {

    private final RunRepository runRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final TaskRepository taskRepository;
    private final RunEventRepository runEventRepository;
    private final GuardChecker guardChecker;
    private final ContextBuilder contextBuilder;
    private final SseBroadcaster sseBroadcaster;
    private final LlmClient llmClient;
    private final ToolRegistry toolRegistry;
    private final GatewayTrustProperties gatewayProperties;
    private final JpaChatMemory jpaChatMemory;

    @Autowired
    public AgentOrchestrator(
            RunRepository runRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            TaskRepository taskRepository,
            RunEventRepository runEventRepository,
            GuardChecker guardChecker,
            ContextBuilder contextBuilder,
            SseBroadcaster sseBroadcaster,
            LlmClient llmClient,
            ToolRegistry toolRegistry,
            GatewayTrustProperties gatewayProperties,
            JpaChatMemory jpaChatMemory
    ) {
        this.runRepository = runRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.taskRepository = taskRepository;
        this.runEventRepository = runEventRepository;
        this.guardChecker = guardChecker;
        this.contextBuilder = contextBuilder;
        this.sseBroadcaster = sseBroadcaster;
        this.llmClient = llmClient;
        this.toolRegistry = toolRegistry;
        this.gatewayProperties = gatewayProperties;
        this.jpaChatMemory = jpaChatMemory;
    }

    public AgentOrchestrator(
            RunRepository runRepository,
            ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            TaskRepository taskRepository,
            RunEventRepository runEventRepository,
            GuardChecker guardChecker,
            ContextBuilder contextBuilder,
            SseBroadcaster sseBroadcaster,
            LlmClient llmClient
    ) {
        this(runRepository, conversationRepository, messageRepository, taskRepository,
                runEventRepository, guardChecker, contextBuilder, sseBroadcaster, llmClient,
                null, null, null);
    }

    @Transactional
    public void executeRun(UUID runId) {
        Run run = runRepository.findById(runId).orElse(null);
        if (run == null) {
            log.warn("Run {} not found, aborting execution", runId);
            return;
        }

        if (run.getStatus() != RunStatus.QUEUED) {
            log.info("Run {} is in status {}, skipping execution", runId, run.getStatus());
            return;
        }

        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(Instant.now());
        Run currentRun = runRepository.save(run);
        UUID conversationId = currentRun.getConversationId();

        recordEvent(runId, 1, "run.started", Map.of("runId", runId.toString()));
        sseBroadcaster.sendEvent(runId, "run.started", Map.of("runId", runId));

        try {
            Conversation conv = conversationRepository.findById(conversationId)
                    .orElseThrow(() -> new IllegalStateException("Conversation " + conversationId + " not found"));

            Task task = conv.getActiveTaskId() != null
                    ? taskRepository.findById(conv.getActiveTaskId()).orElse(null)
                    : null;

            int stepCount = 0;
            boolean completed = false;
            int eventSeq = 2;

            while (guardChecker.canContinue(run, stepCount) && !completed) {
                stepCount++;

                // Verify run cancellation status
                Run refreshed = runRepository.findById(runId).orElse(run);
                if (refreshed.getStatus() == RunStatus.CANCELLED) {
                    log.info("Run {} was cancelled, halting loop", runId);
                    break;
                }

                String systemPrompt = contextBuilder.buildSystemPrompt("ROLE_USER", task);

                AgentContext agentContext = new AgentContext(
                        conv.getOwnerId(),
                        "",
                        Set.of("ROLE_STUDENT", "ROLE_LECTURER"),
                        conv.getId(),
                        runId,
                        gatewayProperties != null ? gatewayProperties.secret() : ""
                );

                List<ToolCallback> toolCallbacks = (toolRegistry != null)
                        ? toolRegistry.getToolCallbacksForRoles(agentContext.roles(), agentContext)
                        : List.of();

                StringBuilder responseBuffer = new StringBuilder();
                Flux<String> stream = (llmClient != null)
                        ? llmClient.streamChatWithMemory(systemPrompt, conv.getId().toString(), jpaChatMemory, toolCallbacks)
                        : null;

                if (stream != null) {
                    try {
                        stream.doOnNext(delta -> {
                            responseBuffer.append(delta);
                            sseBroadcaster.sendDelta(runId, delta);
                        }).blockLast();
                    } catch (Exception ex) {
                        log.warn("Streaming encountered error: {}", ex.getMessage());
                    }
                }

                String completion = responseBuffer.toString().trim();
                if (completion.isEmpty()) {
                    throw new IllegalStateException("AI returned no response");
                }

                // Persist assistant message into jpaChatMemory
                if (jpaChatMemory != null && !completion.isEmpty()) {
                    jpaChatMemory.add(conv.getId().toString(), List.of(new org.springframework.ai.chat.messages.AssistantMessage(completion)));
                } else if (!completion.isEmpty()) {
                    int nextSeq = messageRepository.findMaxSequenceNo(conv.getId()) + 1;
                    Message assistantMsg = Message.builder()
                            .conversationId(conv.getId())
                            .runId(runId)
                            .role("assistant")
                            .content(completion)
                            .sequenceNo(nextSeq)
                            .build();
                    messageRepository.save(assistantMsg);
                }

                completed = true;
            }

            Run finishedRun = runRepository.findById(runId).orElse(run);
            if (finishedRun.getStatus() != RunStatus.CANCELLED) {
                finishedRun.setStatus(RunStatus.SUCCEEDED);
                finishedRun.setFinishedAt(Instant.now());
                runRepository.save(finishedRun);

                recordEvent(runId, eventSeq++, "run.completed", Map.of("runId", runId.toString(), "status", "SUCCEEDED"));
                sseBroadcaster.sendEvent(runId, "run.completed", Map.of("runId", runId.toString(), "status", "SUCCEEDED"));
            }
            sseBroadcaster.complete(runId);

        } catch (Exception e) {
            log.error("Execution failed for run {}", runId, e);
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(e.getMessage());
            run.setFinishedAt(Instant.now());
            runRepository.save(run);

            recordEvent(runId, 999, "run.failed", Map.of("runId", runId.toString(), "error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
            sseBroadcaster.sendEvent(runId, "run.failed", Map.of("runId", runId.toString(), "error", e.getMessage() != null ? e.getMessage() : "Unknown error"));
            sseBroadcaster.complete(runId);
        }
    }

    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    private void recordEvent(UUID runId, int seq, String type, Map<String, Object> payload) {
        try {
            RunEvent event = RunEvent.builder()
                    .runId(runId)
                    .sequence(seq)
                    .type(type)
                    .payloadJson(gson.toJson(payload))
                    .build();
            runEventRepository.save(event);
        } catch (Exception ex) {
            log.warn("Could not persist run event {}: {}", type, ex.getMessage());
        }
    }
}
