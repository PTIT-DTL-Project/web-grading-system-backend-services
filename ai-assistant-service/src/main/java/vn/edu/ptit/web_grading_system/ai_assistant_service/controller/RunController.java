package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.RunResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator.SseBroadcaster;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;

import java.util.UUID;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/ai/runs")
@RequiredArgsConstructor
public class RunController {

    private final RunRepository runRepository;
    private final ConversationRepository conversationRepository;
    private final SseBroadcaster sseBroadcaster;

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeEvents(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        Run run = runRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + id));
        verifyOwner(principal, run);
        return sseBroadcaster.subscribe(id);
    }

    @GetMapping("/{id}")
    public ResponseEntity<RunResponse> getRunStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        Run run = runRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + id));
        verifyOwner(principal, run);

        return ResponseEntity.ok(new RunResponse(
                run.getId(),
                run.getConversationId(),
                run.getStatus(),
                run.getErrorCode(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt()
        ));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelRun(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        Run run = runRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + id));
        verifyOwner(principal, run);
        runRepository.cancelActiveRun(id, RunStatus.CANCELLED, Instant.now(),
                List.of(RunStatus.QUEUED, RunStatus.RUNNING, RunStatus.WAITING_USER,
                        RunStatus.WAITING_APPROVAL));
        return ResponseEntity.ok().build();
    }

    private void verifyOwner(UserPrincipal principal, Run run) {
        conversationRepository.findByIdAndOwnerId(run.getConversationId(), principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + run.getId()));
    }
}
