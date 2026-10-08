package vn.edu.ptit.web_grading_system.course_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentClassResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.service.StudentClassService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/student/classes")
@RequiredArgsConstructor
public class StudentClassController {

    private final StudentClassService studentClassService;

    @GetMapping
    public ResponseEntity<Page<StudentClassResponse>> list(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ClassStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ResponseEntity.ok(studentClassService.listEnrolledClasses(
                UUID.fromString(studentId), search, status, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentClassResponse> detail(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String studentId,
            @PathVariable UUID id) {
        return ResponseEntity.ok(studentClassService.getEnrolledClass(UUID.fromString(studentId), id));
    }
}
