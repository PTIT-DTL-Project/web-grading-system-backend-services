package vn.edu.ptit.web_grading_system.course_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import vn.edu.ptit.web_grading_system.course_service.client.ExecutorInternalClient;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentExistsResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentGradingConfigDto;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.DryRunReport;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.DryRunRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.InternalPlanDto;
import vn.edu.ptit.web_grading_system.course_service.service.StepConfigValidator;
import vn.edu.ptit.web_grading_system.course_service.service.TestPlanService;

import java.util.List;
import java.util.UUID;

/**
 * Internal contracts for executor-service (config + plans) and submission-service
 * (exists validation). Raw DTOs — no envelope. Never exposed via the gateway.
 */
@RestController
@RequestMapping("/api/v1/internal/assignments")
public class InternalAssignmentController {

    private final TestPlanService testPlanService;
    private final ExecutorInternalClient executorInternalClient;

    public InternalAssignmentController(TestPlanService testPlanService,
            ExecutorInternalClient executorInternalClient) {
        this.testPlanService = testPlanService;
        this.executorInternalClient = executorInternalClient;
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentGradingConfigDto> gradingConfig(@PathVariable UUID id) {
        return ResponseEntity.ok(testPlanService.internalGradingConfig(id));
    }

    @GetMapping("/{id}/plans")
    public ResponseEntity<List<InternalPlanDto>> plans(@PathVariable UUID id) {
        return ResponseEntity.ok(testPlanService.internalPlans(id));
    }

    @GetMapping("/{id}/exists")
    public ResponseEntity<AssignmentExistsResponse> exists(@PathVariable UUID id) {
        return ResponseEntity.ok(testPlanService.internalExists(id));
    }

    /**
     * Phase 6 — Dry-run. Runs the grading config against the reference code
     * without posting results to result-service or updating submission status.
     * Returns a detailed per-step report.
     */
    @PostMapping("/{id}/dry-run")
    public ResponseEntity<DryRunReport> dryRun(@PathVariable UUID id,
            @RequestBody DryRunRequest request) {
        // Override assignmentId from the path variable for consistency.
        var normalized = DryRunRequest.builder()
                .assignmentId(id)
                .referenceCodeZipUrl(request.getReferenceCodeZipUrl())
                .plans(request.getPlans())
                .build();
        return executorInternalClient.dryRun(normalized);
    }

    /**
     * Phase 7 — Returns the JSON Schema (draft-07) for HTTP_REQUEST step config.
     * The frontend uses this to validate the step editor in real time before
     * saving to the backend.
     */
    @GetMapping("/{id}/config-schema")
    public ResponseEntity<JsonNode> configSchema(@PathVariable UUID id)
    {
        // Assignment existence is implicitly checked by the caller; this endpoint
        // is schema-only and does not depend on assignment state.
        JsonNode schema = StepConfigValidator.generateHttpSchema();
        return ResponseEntity.ok(schema);
    }
}