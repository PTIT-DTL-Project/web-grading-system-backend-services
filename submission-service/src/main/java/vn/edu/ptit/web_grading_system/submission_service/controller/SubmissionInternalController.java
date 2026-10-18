package vn.edu.ptit.web_grading_system.submission_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.submission_service.dto.request.UpdateStatusRequest;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;

import java.util.UUID;

/**
 * Internal-only endpoints. Not routed by the api-gateway and excluded from the ApiResponse
 * envelope (see FormatRestResponse). Callers: executor-service (status updates) and
 * course-service (the lecturer's per-assignment submission list).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/submissions")
@RequiredArgsConstructor
public class SubmissionInternalController {

    private final SubmissionService submissionService;

    /**
     * The former public route {@code GET /api/v1/submissions/assignment/{id}} moved here
     * (plan role-split-result-apis-v1.0, D4): the per-assignment list is course-service's
     * lecturer grading view, which is role-gated AND owner-scoped before it ever reaches
     * this internal endpoint. On the public route the only guard was a role check, so any
     * lecturer could list any class's submissions.
     */
    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<Page<SubmissionResponse>> listByAssignment(
            @PathVariable UUID assignmentId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        Pageable pageable = (page != null && size != null)
                ? PageRequest.of(page, size)
                : Pageable.unpaged();
        return ResponseEntity.ok(submissionService.listByAssignment(assignmentId, pageable));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateStatusRequest request) {
        submissionService.updateStatus(id, request.getStatus());
        return ResponseEntity.noContent().build();
    }
}
