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
import vn.edu.ptit.web_grading_system.result_service.dto.response.StepResultResponse;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;
import vn.edu.ptit.web_grading_system.result_service.util.annotation.ApiMessage;

import java.math.BigDecimal;
import java.util.ArrayList;
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
        // Strip sensitive step details for students: assertion details reveal the
        // expected answer (expected_status, json_path, field_equals, contains, etc.),
        // expectedValue/actualValue reveal the correct output, and stack traces/logs
        // leak internal debugging information.
        if (!isLecturer) {
            results = stripSensitiveStepDetails(results);
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
        AssignmentResultGroupResponse group = groups.get(0);
        // Strip sensitive step details for students (same rule as getBySubmission).
        List<ResultResponse> sanitized = stripSensitiveStepDetails(group.getResults());
        return ResponseEntity.ok(AssignmentResultGroupResponse.builder()
                .studentUserId(group.getStudentUserId())
                .exerciseScore(group.getExerciseScore())
                .results(sanitized)
                .build());
    }

    /**
     * Strips sensitive per-step fields from result rows before returning to students.
     * Keeps score/maxScore/status/passed/skipped/weight which are safe to show,
     * but removes assertion details, expected/actual values, error messages and
     * internal debugging logs that would reveal grading answers or internals.
     */
    private List<ResultResponse> stripSensitiveStepDetails(List<ResultResponse> results) {
        List<ResultResponse> sanitized = new ArrayList<>(results.size());
        for (ResultResponse r : results) {
            List<StepResultResponse> cleanSteps = null;
            if (r.getSteps() != null) {
                cleanSteps = new ArrayList<>(r.getSteps().size());
                for (StepResultResponse s : r.getSteps()) {
                    cleanSteps.add(StepResultResponse.builder()
                            .id(s.getId())
                            .stepId(s.getStepId())
                            .stepOrder(s.getStepOrder())
                            .stepName(s.getStepName())
                            .stepType(s.getStepType())
                            .planId(s.getPlanId())
                            // Students need requestUrl and requestBody to know what endpoint
                            // to call and what body to send.
                            .requestUrl(s.getRequestUrl())
                            .requestBody(s.getRequestBody())
                            .score(s.getScore())
                            .passed(s.getPassed())
                            .skipped(s.getSkipped())
                            .weight(s.getWeight())
                            .durationMs(s.getDurationMs())
                            .responseStatus(s.getResponseStatus())
                            .build());
                }
            }
            sanitized.add(ResultResponse.builder()
                    .id(r.getId())
                    .submissionId(r.getSubmissionId())
                    .assignmentId(r.getAssignmentId())
                    .studentId(r.getStudentId())
                    .planId(r.getPlanId())
                    .planWeight(r.getPlanWeight())
                    .scope(r.getScope())
                    .score(r.getScore())
                    .maxScore(r.getMaxScore())
                    .status(r.getStatus())
                    // summaryLog may contain expected values and error details — stripped for students
                    .latest(r.getLatest())
                    .startedAt(r.getStartedAt())
                    .completedAt(r.getCompletedAt())
                    .steps(cleanSteps)
                    .build());
        }
        return sanitized;
    }
}
