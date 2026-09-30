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
import vn.edu.ptit.web_grading_system.result_service.security.SecurityUtils;
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
        // A lecturer grades across their classes, so the ownership rule must not apply to
        // them. This is not optional: the gateway now always injects X-User-Id, which would
        // otherwise turn every lecturer read of a student's result into 403.
        // Review: 2026-09-30, Pullfrog review (feat/DAT-8).
        if (!SecurityUtils.hasRole("LECTURER") && xUserId != null && !results.isEmpty()) {
            UUID callerId = UUID.fromString(xUserId);
            boolean allMatch = results.stream().allMatch(r -> r.getStudentId().equals(callerId));
            if (!allMatch) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not owner of submission");
            }
        }
        return ResponseEntity.ok(results);
    }
}
