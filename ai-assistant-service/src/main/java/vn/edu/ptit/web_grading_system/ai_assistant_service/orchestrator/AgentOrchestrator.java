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
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiProviderException;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.EffectType;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolRegistry;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

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

                Set<String> callerRoles = run.getCallerRoles() == null ? Set.of()
                        : java.util.Arrays.stream(run.getCallerRoles().split(","))
                                .filter(role -> role.equals("ROLE_STUDENT") || role.equals("ROLE_LECTURER"))
                                .collect(java.util.stream.Collectors.toUnmodifiableSet());
                String systemPrompt = contextBuilder.buildSystemPrompt(String.join(", ", callerRoles), task);

                AgentContext agentContext = new AgentContext(
                        conv.getOwnerId(),
                        run.getCallerEmail(),
                        callerRoles,
                        conv.getId(),
                        runId,
                        gatewayProperties != null ? gatewayProperties.secret() : ""
                );

                AtomicReference<ToolResult> failedWrite = new AtomicReference<>();
                List<ToolCallback> toolCallbacks = (toolRegistry != null)
                        ? toolRegistry.getToolCallbacksForRoles(agentContext.roles(), agentContext, (tool, result) -> {
                            if (tool.getDefinition().effectType() == EffectType.DOMAIN_WRITE) {
                                failedWrite.set(result.isSuccess() ? null : result);
                            }
                        })
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
                        if (failedWrite.get() == null) throw new AiProviderException(ex);
                    }
                }

                String completion = responseBuffer.toString().trim();
                ToolResult writeFailure = failedWrite.get();
                if (writeFailure != null) {
                    completion = "Không thể hoàn tất thao tác: " + writeFailure.getMessage();
                    sseBroadcaster.sendEvent(runId, "assistant.replace", completion);
                }
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
                if (writeFailure != null) {
                    run.setStatus(RunStatus.FAILED);
                    run.setErrorCode(writeFailure.getCode());
                    run.setErrorMessage(writeFailure.getMessage());
                }
            }

            Run finishedRun = runRepository.findById(runId).orElse(run);
            if (finishedRun.getStatus() != RunStatus.CANCELLED && finishedRun.getStatus() != RunStatus.FAILED) {
                finishedRun.setStatus(RunStatus.SUCCEEDED);
                finishedRun.setFinishedAt(Instant.now());
                runRepository.save(finishedRun);

                recordEvent(runId, eventSeq++, "run.completed", Map.of("runId", runId.toString(), "status", "SUCCEEDED"));
                sseBroadcaster.sendEvent(runId, "run.completed", Map.of("runId", runId.toString(), "status", "SUCCEEDED"));
            } else if (finishedRun.getStatus() == RunStatus.FAILED) {
                finishedRun.setFinishedAt(Instant.now());
                runRepository.save(finishedRun);
                recordEvent(runId, eventSeq++, "run.failed", Map.of("runId", runId.toString(), "error", finishedRun.getErrorMessage()));
                sseBroadcaster.sendEvent(runId, "run.failed", Map.of("runId", runId.toString(), "error", finishedRun.getErrorMessage()));
            }
            sseBroadcaster.complete(runId);

        } catch (Exception e) {
            log.error("Execution failed for run {}", runId, e);
            boolean providerFailure = hasProviderFailure(e);
            String userMessage = providerFailure
                    ? "Trợ lý AI tạm thời chưa thể xử lý yêu cầu. Vui lòng thử lại sau."
                    : "Đã xảy ra lỗi khi xử lý yêu cầu. Vui lòng thử lại sau.";
            run.setStatus(RunStatus.FAILED);
            run.setErrorCode(providerFailure ? "AI_PROVIDER_UNAVAILABLE" : "RUN_ERROR");
            run.setErrorMessage(userMessage);
            run.setFinishedAt(Instant.now());
            runRepository.save(run);

            try {
                int nextSeq = messageRepository.findMaxSequenceNo(conversationId) + 1;
                messageRepository.save(Message.builder().conversationId(conversationId).runId(runId)
                        .role("assistant").content(userMessage).sequenceNo(nextSeq).build());
            } catch (Exception saveError) {
                log.warn("Could not persist user-facing failure for run {}", runId, saveError);
            }
            sseBroadcaster.sendEvent(runId, "assistant.replace", userMessage);
            recordEvent(runId, 999, "run.failed", Map.of("runId", runId.toString(), "error", userMessage));
            sseBroadcaster.sendEvent(runId, "run.failed", Map.of("runId", runId.toString(), "error", userMessage));
            sseBroadcaster.complete(runId);
        }
    }

    private boolean hasProviderFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderException) return true;
        }
        return false;
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
