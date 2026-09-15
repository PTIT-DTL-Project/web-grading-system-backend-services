package vn.edu.ptit.web_grading_system.result_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
            @PathVariable UUID submissionId) {
        return ResponseEntity.ok(resultService.getBySubmissionId(submissionId));
    }
}
