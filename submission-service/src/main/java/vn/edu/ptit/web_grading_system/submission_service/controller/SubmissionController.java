package vn.edu.ptit.web_grading_system.submission_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.PresignedUrlResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.submission_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;
import vn.edu.ptit.web_grading_system.submission_service.util.annotation.ApiMessage;

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
            @RequestParam(required = false) UUID planId,
            // Review: 2026-09-28, Pullfrog PR #24 — identity used to be a per-call UUID.randomUUID(), so no
            // result could ever be attributed to the submitting student: result-service
            // ownership check 403'd, weighted exercise score (keyed by
            // class_students.student_user_id) was always null, and "my submissions" was
            // always empty. X-User-Id is now REQUIRED, like the sibling listMySubmissions:
            // fail fast with 400 instead of silently storing an unattributable student_id.
            // Missing → MissingRequestHeaderException → 400 (new handler; the catch-all
            // previously turned it into 500). Blank/garbage → 400 via the existing
            // IllegalArgumentException handler. Shorthand like "1-1-1-1-1" is rejected
            // (UUID.fromString is lenient) so a phantom student is never stamped.
            @RequestHeader("X-User-Id") String studentIdHeader) {
        UUID studentId = UUID.fromString(studentIdHeader);
        // Review: 2026-09-28, Pullfrog PR #24 — UUID.fromString accepts shorthand groups ("1-1-1-1-1",
        // short last group), which parse to a real-looking UUID that would stamp a
        // phantom student_id. Accept only the canonical form the client produced.
        if (!studentId.toString().equalsIgnoreCase(studentIdHeader)) {
            throw new IllegalArgumentException("X-User-Id must be a canonical UUID");
        }
        PresignedUrlResponse response = submissionService.requestUpload(
                assignmentId,
                studentId,
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

    // Review: 2026-09-30, role-split slice (plan role-split-result-apis-v1.0, D3) — this
    // endpoint had no ownership check at all: any authenticated caller could read any
    // submission by guessing its id, while "my submissions" filtered by studentId. The check
    // lives here rather than in SubmissionService so getById keeps its signature for
    // existing callers/tests.
    @GetMapping("/{id}")
    public ResponseEntity<SubmissionResponse> getById(
            @PathVariable UUID id,
            @RequestHeader("X-User-Id") String callerIdHeader) {
        UUID callerId = UUID.fromString(callerIdHeader);
        SubmissionResponse submission = submissionService.getById(id);
        // Review: 2026-09-30, role-split slice (D3) — the `hasRole("LECTURER")` bypass is
        // gone: it let ANY lecturer read ANY submission in the system with no check that the
        // assignment belonged to a class they own, and it made one route serve two audiences.
        // Lecturers now go through GET /api/v1/assignments/{id}/submissions in course-service,
        // which is role-gated AND owner-scoped. "Not found" (rather than 403) keeps an
        // existing id indistinguishable from a missing one — the project's ownership convention.
        if (!callerId.equals(submission.getStudentId())) {
            throw new ResourceNotFoundException("Submission not found: " + id);
        }
        return ResponseEntity.ok(submission);
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