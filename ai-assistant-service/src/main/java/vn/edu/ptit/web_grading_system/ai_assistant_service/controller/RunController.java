package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.RunResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/runs")
@RequiredArgsConstructor
public class RunController {

    private final RunRepository runRepository;

    @GetMapping("/{id}")
    public ResponseEntity<RunResponse> getRunStatus(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        Run run = runRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Run not found: " + id));

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

        if (run.getStatus() == RunStatus.QUEUED || run.getStatus() == RunStatus.RUNNING
                || run.getStatus() == RunStatus.WAITING_USER || run.getStatus() == RunStatus.WAITING_APPROVAL) {
            run.setStatus(RunStatus.CANCELLED);
            runRepository.save(run);
        }
        return ResponseEntity.ok().build();
    }
}
