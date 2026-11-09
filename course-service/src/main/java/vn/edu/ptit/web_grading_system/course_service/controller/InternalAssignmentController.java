package vn.edu.ptit.web_grading_system.course_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import vn.edu.ptit.web_grading_system.course_service.client.ExecutorInternalClient;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentExistsResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentGradingConfigDto;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentOwnerResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.DryRunReport;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.DryRunRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.InternalPlanDto;
import vn.edu.ptit.web_grading_system.course_service.entity.StepType;
import vn.edu.ptit.web_grading_system.course_service.service.StepConfigValidator;
import vn.edu.ptit.web_grading_system.course_service.service.TestPlanService;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Internal contracts for executor-service (config + plans) and submission-service
 * (exists validation). Raw DTOs — no envelope. Never exposed via the gateway.
 */
@RestController
@RequestMapping("/api/v1/internal/assignments")
public class InternalAssignmentController {

    private final TestPlanService testPlanService;
    private final ExecutorInternalClient executorInternalClient;
    private final Tracer tracer;

    public InternalAssignmentController(TestPlanService testPlanService,
            ExecutorInternalClient executorInternalClient,
            Tracer tracer) {
        this.testPlanService = testPlanService;
        this.executorInternalClient = executorInternalClient;
        this.tracer = tracer;
    }

    // Review: 2026-11-09, PR #93 follow-up — wrap internal Feign calls in explicit spans
    // so service-to-service calls are visible in Tempo (not blank gaps).
    private <T> T traceFeign(String spanName, Supplier<T> call) {
        Span span = tracer.nextSpan().name(spanName).start();
        try (Tracer.SpanInScope scope = tracer.withSpan(span)) {
            return call.get();
        } catch (Throwable ex) {
            span.error(ex);
            throw ex;
        } finally {
            span.end();
        }
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
     * Trả về ownerId của assignment, dùng để submission-service kiểm tra lecturer
     * có quyền xem submission của lớp đó hay không. Internal-only, không qua gateway.
     */
    @GetMapping("/{id}/owner")
    public ResponseEntity<AssignmentOwnerResponse> owner(@PathVariable UUID id) {
        return ResponseEntity.ok(testPlanService.internalAssignmentOwner(id));
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
        return traceFeign("client.executor-service.dryRun",
                () -> executorInternalClient.dryRun(normalized));
    }

    /**
     * Phase 7 — Returns the JSON Schema (draft-07) for step config.
     * The frontend uses this to validate the step editor in real time before
     * saving to the backend.
     */
    @GetMapping("/{id}/config-schema")
    public ResponseEntity<JsonNode> configSchema(@PathVariable UUID id,
                                                 @RequestParam(value = "type", required = false) String type)
    {
        // Assignment existence is implicitly checked by the caller; this endpoint
        // is schema-only and does not depend on assignment state.
        StepType stepType = StepType.HTTP_REQUEST;
        if (type != null && !type.isBlank()) {
            try {
                stepType = StepType.valueOf(type.trim());
            } catch (Exception ignored) {
                stepType = StepType.HTTP_REQUEST;
            }
        }
        JsonNode schema = StepConfigValidator.generateSchema(stepType);
        return ResponseEntity.ok(schema);
    }
}