package vn.edu.ptit.web_grading_system.course_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.response.AssignmentResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.PlanResponse;
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
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(studentAssignmentService.listAssignments(
                UUID.fromString(studentId), classId, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentResponse> detail(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(studentAssignmentService.getAssignment(UUID.fromString(studentId), id));
    }

    @GetMapping("/{id}/plans")
    public ResponseEntity<List<PlanResponse>> plans(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(studentAssignmentService.listPlans(UUID.fromString(studentId), id));
    }
}
