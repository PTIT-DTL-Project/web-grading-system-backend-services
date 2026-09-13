package vn.edu.ptit.web_grading_system.submission_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.submission_service.dto.request.UpdateStatusRequest;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.PresignedUrlResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;
import vn.edu.ptit.web_grading_system.submission_service.util.annotation.ApiMessage;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    @PostMapping("/presigned-url")
    @ApiMessage("Upload URL generated")
    public ResponseEntity<PresignedUrlResponse> requestUpload(
            @RequestParam UUID assignmentId,
            @RequestParam String zipFileName,
            @RequestParam(required = false) UUID planId) {
        UUID studentUUID = UUID.randomUUID();
        String studentId = String.valueOf(studentUUID);
        PresignedUrlResponse response = submissionService.requestUpload(
                assignmentId,
                UUID.fromString(studentId),
                zipFileName,
                planId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<Page<SubmissionResponse>> listMySubmissions(
            @RequestHeader("X-User-Id") String studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(submissionService.listByStudent(UUID.fromString(studentId), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(submissionService.getById(id));
    }

    @GetMapping("/assignment/{assignmentId}")
    public ResponseEntity<List<SubmissionResponse>> listByAssignment(
            @PathVariable UUID assignmentId) {
        return ResponseEntity.ok(submissionService.listByAssignment(assignmentId));
    }

    @PutMapping("/{id}/status")
    @ApiMessage("Submission status updated")
    public ResponseEntity<Void> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateStatusRequest request) {
        submissionService.updateStatus(id, request.getStatus());
        return ResponseEntity.noContent().build();
    }

    /**
     * Health check endpoint with version information
     * Added for GitHub Actions CI/CD testing and monitoring
     * Version is auto-incremented by CI/CD pipeline
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        log.debug("Health check endpoint called");
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) version = "dev";

        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "submission-service",
            "version", version,
            "timestamp", java.time.Instant.now().toString(),
            "description", "Submission Service - Handles code submission operations"
        ));
    }

    /**
     * Get service version and build information
     * Version is determined by git tags via CI/CD
     */
    @GetMapping("/version")
    public ResponseEntity<Map<String, String>> version() {
        String version = getClass().getPackage().getImplementationVersion();
        if (version == null) version = "dev";

        return ResponseEntity.ok(Map.of(
            "service", "submission-service",
            "version", version,
            "buildDate", java.time.LocalDate.now().toString(),
            "commitSha", System.getenv().getOrDefault("GIT_COMMIT", "unknown")
        ));
    }
}