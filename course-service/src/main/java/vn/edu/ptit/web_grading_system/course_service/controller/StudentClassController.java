package vn.edu.ptit.web_grading_system.course_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentClassResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentScoresResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.service.ScoreService;
import vn.edu.ptit.web_grading_system.course_service.service.StudentClassService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/classes")
@RequiredArgsConstructor
public class StudentClassController {

    private final StudentClassService studentClassService;
    private final ScoreService scoreService;

    @GetMapping
    public ResponseEntity<Page<StudentClassResponse>> list(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ClassStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(studentClassService.listEnrolledClasses(
                UUID.fromString(studentId), email, search, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentClassResponse> detail(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email,
            @PathVariable UUID id) {
        return ResponseEntity.ok(studentClassService.getEnrolledClass(UUID.fromString(studentId), email, id));
    }

    // Review: 2026-10-09 — student "my scores" tab (FE StudentScoresTab).
    @GetMapping("/{id}/my-scores")
    public ResponseEntity<StudentScoresResponse> myScores(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email,
            @PathVariable UUID id) {
        UUID studentUuid = UUID.fromString(studentId);
        studentClassService.getEnrolledClass(studentUuid, email, id);
        return ResponseEntity.ok(scoreService.getMyScores(id, studentUuid));
    }
}
