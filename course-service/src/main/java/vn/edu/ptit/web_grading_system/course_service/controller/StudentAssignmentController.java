package vn.edu.ptit.web_grading_system.course_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.response.AssignmentResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.PlanResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.AssignmentPurpose;
import vn.edu.ptit.web_grading_system.course_service.service.StudentAssignmentService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/assignments")
@RequiredArgsConstructor
public class StudentAssignmentController {

    private final StudentAssignmentService studentAssignmentService;

    @GetMapping
    public ResponseEntity<Page<AssignmentResponse>> list(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) AssignmentPurpose purpose,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Review: 2026-10-26, practice-exercises feature
        // purpose defaults to GRADED. Passing purpose=PRACTICE lists published class-independent
        // exercises visible to every student (no enrollment check); topic/search are combinable filters.
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(studentAssignmentService.listAssignments(
                UUID.fromString(studentId), email, classId, purpose, topic, search, pageable));
    }

    @GetMapping("/topics")
    public ResponseEntity<List<String>> listTopics() {
        // Review: 2026-10-26, practice-exercises feature
        // Distinct topics among published practice exercises, for the student topic filter.
        return ResponseEntity.ok(studentAssignmentService.listTopics());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentResponse> detail(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email,
            @PathVariable UUID id) {
        return ResponseEntity.ok(studentAssignmentService.getAssignment(UUID.fromString(studentId), email, id));
    }

    @GetMapping("/{id}/plans")
    public ResponseEntity<List<PlanResponse>> plans(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @RequestHeader(value = "X-User-Email", defaultValue = "") String email,
            @PathVariable UUID id) {
        return ResponseEntity.ok(studentAssignmentService.listPlans(UUID.fromString(studentId), email, id));
    }
}
