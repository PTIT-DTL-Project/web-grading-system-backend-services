package vn.edu.ptit.web_grading_system.result_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.ptit.web_grading_system.result_service.dto.response.AssignmentResultGroupResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;
import vn.edu.ptit.web_grading_system.result_service.util.annotation.ApiMessage;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Public read API (routed via gateway). Empty list while the submission is
 * still queued or grading — callers poll.
 */
@RestController
@RequestMapping("/api/v1/results")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    @GetMapping("/{submissionId}")
    @ApiMessage("Results fetched")
    public ResponseEntity<List<ResultResponse>> getBySubmission(
            @PathVariable UUID submissionId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        List<ResultResponse> results = resultService.getBySubmissionId(submissionId);
        if (xUserId == null || xUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Caller identity required");
        }
        UUID callerId = UUID.fromString(xUserId);
        // REVIEW: 2026-10-21 — lecturer bypass for grading deep-link (?submission=<id>).
        // Uses the SecurityContext authority that HeaderAuthenticationFilter already
        // parsed from the gateway-trusted X-User-Roles header, matching exactly how
        // course-service's @PreAuthorize("hasRole('LECTURER')") checks work.
        // Pullfrog flagged that a substring match would silently accept future roles
        // like LECTURER_ASSISTANT; ROLE_LECTURER is exact and consistent.
        boolean isLecturer = SecurityContextHolder.getContext().getAuthentication() != null
            && SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_LECTURER"));
        if (!isLecturer && !results.stream().allMatch(r -> callerId.equals(r.getStudentId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of submission");
        }
        return ResponseEntity.ok(results);
    }

    /**
     * The calling student's results on one assignment shown on the assignment
     * detail page: the weighted exercise score plus the best attempt per plan
     * with its step rows. Identity comes from the gateway-injected X-User-Id
     * header — there is no student id in the path, so a caller can never ask
     * for someone else's results. Delegates to the same {@code getByAssignment}
     * the lecturer's view uses, so both audiences read identical numbers.
     *
     * <p>No submission at all reads {@code exerciseScore = 0.00} (unsubmitted
     * plans count as 0 by policy, Review: 2026-10-31).
     */
    @GetMapping("/student/assignment/{assignmentId}")
    @ApiMessage("My assignment results fetched")
    public ResponseEntity<AssignmentResultGroupResponse> myAssignmentResults(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "X-User-Id", required = false) String xUserId) {
        if (xUserId == null || xUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Caller identity required");
        }
        UUID studentId = UUID.fromString(xUserId);
        List<AssignmentResultGroupResponse> groups =
                resultService.getByAssignment(assignmentId, studentId, true);
        if (groups.isEmpty()) {
            return ResponseEntity.ok(AssignmentResultGroupResponse.builder()
                    .studentUserId(studentId)
                    .exerciseScore(BigDecimal.ZERO)
                    .results(List.of())
                    .build());
        }
        return ResponseEntity.ok(groups.get(0));
    }
}
