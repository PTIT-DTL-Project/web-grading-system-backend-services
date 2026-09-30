package vn.edu.ptit.web_grading_system.course_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentResultResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.course_service.service.AssignmentGradingService;

import java.util.List;
import java.util.UUID;

/**
 * The lecturer's grading view for one assignment: auto-grading results and submissions.
 *
 * <p>Both handlers carry TWO guards — a role gate ({@code @PreAuthorize}) and, inside the
 * service, an assignment-ownership check that answers 404. That pairing is the whole point
 * of this controller: the endpoints it replaced relied on the role gate alone, which let any
 * lecturer read any class in the system (plan role-split-result-apis-v1.0).
 *
 * <p>Split from {@link AssignmentController} the same way {@link TestPlanController} already
 * is: same {@code /api/v1/assignments/{assignmentId}} base path, one responsibility each.
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
    public ResponseEntity<List<StudentResultResponse>> results(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestParam(required = false) String studentCode,
            @RequestParam(defaultValue = "false") boolean includeSteps) {
        return ResponseEntity.ok(assignmentGradingService.results(
                assignmentId, UUID.fromString(ownerId), studentCode, includeSteps));
    }

    /** Every submission of one assignment (former public route, now owner-scoped here). */
    @PreAuthorize("hasRole('LECTURER')")
    @GetMapping("/submissions")
    public ResponseEntity<List<SubmissionResponse>> submissions(
            @PathVariable UUID assignmentId,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(
                assignmentGradingService.submissions(assignmentId, UUID.fromString(ownerId)));
    }
}
