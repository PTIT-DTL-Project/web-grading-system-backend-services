package vn.edu.ptit.web_grading_system.submission_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.submission_service.dto.request.UpdateStatusRequest;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;

import java.util.UUID;

/**
 * Executor-only endpoints. Not routed by the api-gateway and excluded from
 * the ApiResponse envelope (see FormatRestResponse).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/internal/submissions")
@RequiredArgsConstructor
public class SubmissionInternalController {

    private final SubmissionService submissionService;

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateStatusRequest request) {
        submissionService.updateStatus(id, request.getStatus());
        return ResponseEntity.noContent().build();
    }
}
