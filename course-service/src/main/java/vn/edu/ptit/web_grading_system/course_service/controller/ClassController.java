package vn.edu.ptit.web_grading_system.course_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateClassRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ClassResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.ClassStudentResponse;
import vn.edu.ptit.web_grading_system.course_service.service.ClassService;
import vn.edu.ptit.web_grading_system.course_service.util.annotation.ApiMessage;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassService classService;

    @PostMapping
    @ApiMessage("Class created")
    public ResponseEntity<ClassResponse> create(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody CreateClassRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(classService.create(UUID.fromString(ownerId), request));
    }

    @GetMapping
    public ResponseEntity<Page<ClassResponse>> listMine(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(classService.listMine(UUID.fromString(ownerId), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClassResponse> getById(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(classService.getById(id, UUID.fromString(ownerId)));
    }

    @PutMapping("/{id}/archive")
    @ApiMessage("Class archived")
    public ResponseEntity<ClassResponse> archive(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(classService.archive(id, UUID.fromString(ownerId)));
    }

    @PostMapping("/{id}/students/import")
    @ApiMessage("Students imported")
    public ResponseEntity<ClassService.ImportResult> importStudents(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(classService.importStudents(id, UUID.fromString(ownerId), file));
    }

    @GetMapping("/{id}/students")
    public ResponseEntity<Page<ClassStudentResponse>> listStudents(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(classService.listStudents(id, UUID.fromString(ownerId), pageable));
    }
}