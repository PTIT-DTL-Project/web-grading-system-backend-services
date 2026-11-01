package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
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
    private final AgentRuntime agentRuntime;
    @Autowired(required = false)
    private RunTimelineService timeline;
    private final com.fasterxml.jackson.databind.ObjectMapper timelineMapper = new com.fasterxml.jackson.databind.ObjectMapper();

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
            JpaChatMemory jpaChatMemory,
            AgentRuntime agentRuntime
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
        this.agentRuntime = agentRuntime;
    }

    public AgentOrchestrator(
            RunRepository runRepository, ConversationRepository conversationRepository,
            MessageRepository messageRepository, TaskRepository taskRepository,
            RunEventRepository runEventRepository, GuardChecker guardChecker,
            ContextBuilder contextBuilder, SseBroadcaster sseBroadcaster, LlmClient llmClient,
            ToolRegistry toolRegistry, GatewayTrustProperties gatewayProperties, JpaChatMemory jpaChatMemory) {
        this(runRepository, conversationRepository, messageRepository, taskRepository,
                runEventRepository, guardChecker, contextBuilder, sseBroadcaster, llmClient,
                toolRegistry, gatewayProperties, jpaChatMemory, null);
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
                null, null, null, null);
    }

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
        long runStarted = System.nanoTime();
        log.info("AI runId={} conversationId={} started", runId, conversationId);
        AgentTrace.record(runId, gatewayProperties == null ? "" : gatewayProperties.secret(),
                "run.started", Map.of("conversationId", conversationId.toString(),
                        "callerRoles", run.getCallerRoles() == null ? "" : run.getCallerRoles()));

        recordEvent(runId, 1, "run.started", Map.of("runId", runId.toString()));
        publish(runId, "run.started", null, null, Map.of("runId", runId.toString()));

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
                StringBuilder responseBuffer = new StringBuilder();
                if (agentRuntime != null) {
                    String latestUserText = messageRepository.findFirstByRunIdAndRoleOrderBySequenceNoAsc(runId, "user")
                            .map(Message::getContent).orElse("");
                    try {
                        java.util.function.Consumer<String> onDelta = delta -> {
                                    if (isCancelled(runId)) return;
                                    responseBuffer.append(delta);
                                    publish(runId, "assistant.delta", null, "answer", Map.of("text", delta));
                                };
                        java.util.function.BiConsumer<vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentTool, ToolResult> onResult = (tool, result) -> {
                                    if (tool.getDefinition().effectType() == EffectType.DOMAIN_WRITE) {
                                        failedWrite.set(result.isSuccess() ? null : result);
                                    }
                                };
                        if (timeline == null) {
                            agentRuntime.execute(agentContext, systemPrompt, latestUserText, llmClient,
                                    onDelta, onResult, () -> isCancelled(runId));
                        } else {
                            agentRuntime.execute(agentContext, systemPrompt, latestUserText, llmClient,
                                    onDelta, onResult, event -> publish(runId, event.type(), event.modelTurn(),
                                            event.itemId(), event.data()), () -> isCancelled(runId));
                        }
                    } catch (Exception ex) {
                        if (ex instanceof RunCancelledException || isCancelled(runId)) throw new RunCancelledException();
                        log.warn("AI runId={} agent execution error: {}", runId, ex.getMessage());
                        if (failedWrite.get() == null) throw ex;
                    }
                } else {
                    List<ToolCallback> toolCallbacks = (toolRegistry != null)
                            ? toolRegistry.getToolCallbacksForRoles(agentContext.roles(), agentContext, (tool, result) -> {
                                if (tool.getDefinition().effectType() == EffectType.DOMAIN_WRITE) {
                                    failedWrite.set(result.isSuccess() ? null : result);
                                }
                            }) : List.of();
                    Flux<String> stream = (llmClient != null)
                            ? llmClient.streamChatWithMemory(systemPrompt, conv.getId().toString(), jpaChatMemory, toolCallbacks)
                            : null;
                    if (stream != null) {
                        try {
                            stream.doOnNext(delta -> {
                                if (isCancelled(runId)) return;
                                responseBuffer.append(delta);
                                publish(runId, "assistant.delta", null, "answer", Map.of("text", delta));
                            }).takeUntilOther(Flux.interval(java.time.Duration.ofMillis(200))
                                    .filter(tick -> isCancelled(runId)).next()).blockLast();
                            if (isCancelled(runId)) throw new RunCancelledException();
                        } catch (Exception ex) {
                            if (isCancelled(runId)) throw new RunCancelledException();
                            log.warn("AI runId={} streaming error: {}", runId, ex.getMessage());
                            if (failedWrite.get() == null) throw ex;
                        }
                    }
                }

                if (isCancelled(runId)) throw new RunCancelledException();

                String completion = responseBuffer.toString().trim();
                AgentTrace.record(runId, agentContext.gatewaySecret(), "assistant.final", Map.of(
                        "content", completion, "step", stepCount));
                ToolResult writeFailure = failedWrite.get();
                if (writeFailure != null) {
                    completion = "Không thể hoàn tất thao tác: " + writeFailure.getMessage();
                    publish(runId, "assistant.replace", null, "answer", Map.of("text", completion));
                }
                if (completion.isEmpty()) {
                    throw new IllegalStateException("AI returned no response");
                }

                // Persist assistant message into jpaChatMemory
                if (jpaChatMemory != null && !completion.isEmpty()) {
                    jpaChatMemory.addForRun(conv.getId().toString(), runId,
                            List.of(new org.springframework.ai.chat.messages.AssistantMessage(completion)));
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
                publish(runId, "run.completed", null, null, Map.of("status", "SUCCEEDED"));
                AgentTrace.record(runId, gatewayProperties == null ? "" : gatewayProperties.secret(),
                        "run.completed", Map.of("status", "SUCCEEDED"));
            } else if (finishedRun.getStatus() == RunStatus.FAILED) {
                finishedRun.setFinishedAt(Instant.now());
                runRepository.save(finishedRun);
                recordEvent(runId, eventSeq++, "run.failed", Map.of("runId", runId.toString(), "error", finishedRun.getErrorMessage()));
                publish(runId, "run.failed", null, null, Map.of("error", finishedRun.getErrorMessage()));
                AgentTrace.record(runId, gatewayProperties == null ? "" : gatewayProperties.secret(),
                        "run.failed", Map.of("status", "FAILED", "error",
                                String.valueOf(finishedRun.getErrorMessage())));
            } else {
                AgentTrace.record(runId, gatewayProperties == null ? "" : gatewayProperties.secret(),
                        "run.cancelled", Map.of("status", "CANCELLED"));
            }
            sseBroadcaster.complete(runId);

        } catch (RunCancelledException cancelled) {
            log.info("AI runId={} cancelled", runId);
            AgentTrace.record(runId, gatewayProperties == null ? "" : gatewayProperties.secret(),
                    "run.cancelled", Map.of("status", "CANCELLED"));
            publish(runId, "run.cancelled", null, null, Map.of("status", "CANCELLED"));
            sseBroadcaster.complete(runId);
        } catch (Exception e) {
            if (isCancelled(runId)) {
                AgentTrace.record(runId, gatewayProperties == null ? "" : gatewayProperties.secret(),
                        "run.cancelled", Map.of("status", "CANCELLED"));
                sseBroadcaster.complete(runId);
                return;
            }
            log.error("Execution failed for run {}", runId, e);
            AgentTrace.record(runId, gatewayProperties == null ? "" : gatewayProperties.secret(),
                    "run.error", Map.of("error", String.valueOf(e),
                            "errorType", e.getClass().getSimpleName()));
            boolean providerFailure = hasProviderFailure(e);
            boolean emptyModelResponse = e instanceof AgentRuntime.EmptyModelResponseException;
            boolean repeatedRead = hasRepeatedReadFailure(e);
            String userMessage = repeatedRead
                    ? "Trợ lý đã lặp lại cùng một truy vấn đọc mà không có kết quả mới. Chưa có thao tác ghi nào được xác nhận."
                    : emptyModelResponse
                    ? "Mô hình AI không trả về nội dung sau khi thử lại. Chưa có kết quả nào được xác nhận."
                    : providerFailure
                    ? "Trợ lý AI tạm thời chưa thể xử lý yêu cầu. Vui lòng thử lại sau."
                    : "Đã xảy ra lỗi khi xử lý yêu cầu. Vui lòng thử lại sau.";
            run.setStatus(RunStatus.FAILED);
            run.setErrorCode(repeatedRead ? "REPEATED_READ_CALL"
                    : emptyModelResponse ? "AI_EMPTY_RESPONSE"
                    : providerFailure ? "AI_PROVIDER_UNAVAILABLE" : "RUN_ERROR");
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
            publish(runId, "assistant.replace", null, "answer", Map.of("text", userMessage));
            recordEvent(runId, 999, "run.failed", Map.of("runId", runId.toString(), "error", userMessage));
            publish(runId, "run.failed", null, null, Map.of("error", userMessage));
            AgentTrace.record(runId, gatewayProperties == null ? "" : gatewayProperties.secret(),
                    "run.failed", Map.of("status", "FAILED", "error", userMessage));
            sseBroadcaster.complete(runId);
        } finally {
            log.info("AI runId={} totalMs={}", runId,
                    java.time.Duration.ofNanos(System.nanoTime() - runStarted).toMillis());
        }
    }

    private boolean isCancelled(UUID runId) {
        return runRepository.findById(runId)
                .map(current -> current.getStatus() == RunStatus.CANCELLED)
                .orElse(false);
    }

    private boolean hasProviderFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof AiProviderException) return true;
        }
        return false;
    }

    private boolean hasRepeatedReadFailure(Throwable error) {
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof ReadToolCallGuard.RepeatedReadCallException) return true;
        }
        return false;
    }

    private final com.google.gson.Gson gson = new com.google.gson.Gson();

    private void recordEvent(UUID runId, int seq, String type, Map<String, Object> payload) {
        if (timeline != null) return;
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

    private void publish(UUID runId, String type, Integer modelTurn, String itemId, Object data) {
        if (timeline != null) {
            timeline.append(runId, type, modelTurn, itemId, timelineMapper.valueToTree(data));
        } else if ("assistant.delta".equals(type)) {
            sseBroadcaster.sendDelta(runId, String.valueOf(((Map<?, ?>) data).get("text")));
        } else if ("assistant.replace".equals(type)) {
            sseBroadcaster.sendEvent(runId, type, ((Map<?, ?>) data).get("text"));
        } else {
            sseBroadcaster.sendEvent(runId, type, data);
        }
    }
}
