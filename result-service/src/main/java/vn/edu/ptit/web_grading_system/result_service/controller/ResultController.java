package vn.edu.ptit.web_grading_system.result_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;
import vn.edu.ptit.web_grading_system.result_service.util.annotation.ApiMessage;

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
        // Review: 2026-09-30, role-split slice (plan role-split-result-apis-v1.0, D3) — the
        // `hasRole("LECTURER")` bypass that used to sit here skipped ownership for EVERY
        // lecturer in the system, with no check that the assignment belonged to a class they
        // own, and it made one route serve two audiences. Lecturers now read through
        // GET /api/v1/assignments/{id}/results in course-service, which is role-gated AND
        // owner-scoped, so this route answers strictly for its owner.
        //
        // Fail-closed on a present-but-unusable caller identity: the gateway substitutes
        // X-User-Id: "" when the JWT has no sub, and HeaderAuthenticationFilter rejects
        // blank ids before this handler runs. A non-blank id therefore always reaches here,
        // and ownership is enforced uniformly on every returned row.
        //
        // An empty (not-yet-graded) submission returns 200 [] regardless of caller — there
        // are no rows to check against, and result-service cannot confirm submission
        // ownership without grading rows (cross-service lookup it does not perform). This
        // is the documented polling contract: "empty list while still queued or grading."
        if (xUserId == null || xUserId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Caller identity required");
        }
        UUID callerId = UUID.fromString(xUserId);
        if (!results.stream().allMatch(r -> callerId.equals(r.getStudentId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of submission");
        }
        return ResponseEntity.ok(results);
    }
}
