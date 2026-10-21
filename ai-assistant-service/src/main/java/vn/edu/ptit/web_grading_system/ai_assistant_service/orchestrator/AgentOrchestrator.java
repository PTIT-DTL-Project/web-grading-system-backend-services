package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunEvent;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.LlmClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
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

                List<Message> history = messageRepository
                        .findByConversationIdOrderBySequenceNoAsc(conv.getId(), Pageable.unpaged())
                        .getContent();

                String systemPrompt = contextBuilder.buildSystemPrompt("ROLE_USER", task);
                String userPrompt = contextBuilder.buildUserPromptWithHistory(history);

                String completion = llmClient.generateCompletion(systemPrompt, userPrompt);

                // Stream response tokens / chunk
                sseBroadcaster.sendDelta(runId, completion);

                // Persist assistant message
                int nextSeq = messageRepository.findMaxSequenceNo(conv.getId()) + 1;
                Message assistantMsg = Message.builder()
                        .conversationId(conv.getId())
                        .runId(runId)
                        .role("assistant")
                        .content(completion)
                        .sequenceNo(nextSeq)
                        .build();
                messageRepository.save(assistantMsg);

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
