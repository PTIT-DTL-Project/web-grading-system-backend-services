package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiExplanationService;

import java.util.UUID;

/**
 * The AI explanation of one submission, for its two audiences.
 *
 * <p>The routes are split by audience rather than sharing one path with two roles: an audience
 * is a different authorization rule (a student must own the submission, a lecturer must own the
 * assignment's class), and a route that answers to both ends up skipping one of them — the exact
 * failure that got the old {@code hasRole("LECTURER")} bypass in result-service removed.
 *
 * <p>Every handler carries a role gate here and an ownership check inside
 * {@link AiExplanationService}, so a role alone is never enough.
 */
@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiExplanationController {

    private final AiExplanationService explanationService;

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/submissions/{submissionId}/explain")
    public ResponseEntity<AiExplanationResponse> getExplanation(
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader(value = "X-User-Id", defaultValue = "00000000-0000-0000-0000-000000000000") String userId) {
        return explanationService.getExplanation(submissionId, UUID.fromString(userId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping("/submissions/{submissionId}/explain")
    public ResponseEntity<AiExplanationResponse> explainSubmission(
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader(value = "X-User-Id", defaultValue = "00000000-0000-0000-0000-000000000000") String userId) {
        return ResponseEntity.ok(explanationService.explainSubmission(submissionId, UUID.fromString(userId)));
    }

    /** Cache-only lecturer read; {@code 404} = never generated, and 404 also covers every
     *  submission outside the caller's own assignments. */
    @PreAuthorize("hasRole('LECTURER')")
    @GetMapping("/assignments/{assignmentId}/submissions/{submissionId}/explain")
    public ResponseEntity<AiExplanationResponse> getExplanationForLecturer(
            @PathVariable("assignmentId") UUID assignmentId,
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader(value = "X-User-Id", defaultValue = "00000000-0000-0000-0000-000000000000") String lecturerId) {
        return explanationService.getExplanationForLecturer(assignmentId, submissionId, UUID.fromString(lecturerId))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Generates the explanation (or returns the cached row) for a submission in one of the
     *  caller's own assignments. Costs an LLM round-trip; idempotent. */
    @PreAuthorize("hasRole('LECTURER')")
    @PostMapping("/assignments/{assignmentId}/submissions/{submissionId}/explain")
    public ResponseEntity<AiExplanationResponse> explainSubmissionForLecturer(
            @PathVariable("assignmentId") UUID assignmentId,
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader(value = "X-User-Id", defaultValue = "00000000-0000-0000-0000-000000000000") String lecturerId) {
        return ResponseEntity.ok(
                explanationService.explainSubmissionForLecturer(assignmentId, submissionId, UUID.fromString(lecturerId)));
    }
}
