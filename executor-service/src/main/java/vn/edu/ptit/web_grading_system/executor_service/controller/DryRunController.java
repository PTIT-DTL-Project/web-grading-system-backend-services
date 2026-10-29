package vn.edu.ptit.web_grading_system.executor_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.DryRunRequest;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.DryRunReport;
import vn.edu.ptit.web_grading_system.executor_service.service.grading.GradingOrchestrator;

import java.util.UUID;

/**
 * Dry-run grading endpoint: runs the full grading flow (boot compose, run
 * steps) but does NOT post results to result-service or update submission
 * status. Returns a detailed per-step report for lecturer review.
 * Internal-only — not exposed via the API gateway.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/grading")
@RequiredArgsConstructor
public class DryRunController {

    private final GradingOrchestrator gradingOrchestrator;

    @PostMapping("/dry-run")
    public ResponseEntity<DryRunReport> dryRun(@RequestBody DryRunRequest request) {
        log.info("Dry-run request: assignmentId={}, plans={}, referenceCodeZipUrl={}",
                request.getAssignmentId(), request.getPlans() == null ? 0 : request.getPlans().size(),
                request.getReferenceCodeZipUrl());
        DryRunReport report = gradingOrchestrator.dryRun(request);
        return ResponseEntity.ok(report);
    }
}
