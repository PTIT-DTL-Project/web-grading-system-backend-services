package vn.edu.ptit.web_grading_system.course_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentResultResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.course_service.service.AssignmentGradingService;

import java.util.UUID;

/**
 * The lecturer's grading view for one assignment: auto-grading results and submissions.
 *
 * <p>Both handlers accept optional {@code page}/{@code size} for server-side paging.
 * Omitting both (the legacy behaviour) returns a {@code Page} wrapping the full list —
 * the FE keeps its client-side ListPage on {@code meta.total}, so no caller breaks.
 * (plan role-split-result-apis-v1.0, pagination-via-course-service.)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assignments/{assignmentId}")
@RequiredArgsConstructor
public class AssignmentGradingController {

    private final AssignmentGradingService assignmentGradingService;

    /**
     * Class-wide auto-grading results, or one student's when {@code studentCode} is given.
     * Complements {@code GET /api/v1/classes/{id}/transcript}: that one is the point
     * summary, this one is the per-plan / per-step detail behind it.
     */
    @PreAuthorize("hasRole('LECTURER')")
    @GetMapping("/results")
    public ResponseEntity<Page<StudentResultResponse>> results(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestParam(required = false) String studentCode,
            @RequestParam(defaultValue = "false") boolean includeSteps,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(assignmentGradingService.results(
                assignmentId, UUID.fromString(ownerId), studentCode, includeSteps, page, size));
    }

    /** Every submission of one assignment (former public route, now owner-scoped here). */
    @PreAuthorize("hasRole('LECTURER')")
    @GetMapping("/submissions")
    public ResponseEntity<Page<SubmissionResponse>> submissions(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestParam(required = false) String studentCode,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(
                assignmentGradingService.submissions(assignmentId, UUID.fromString(ownerId), studentCode, page, size));
    }
}
