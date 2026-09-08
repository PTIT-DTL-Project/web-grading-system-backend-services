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
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateAssignmentRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateAssignmentRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.AssignmentResponse;
import vn.edu.ptit.web_grading_system.course_service.service.AssignmentService;
import vn.edu.ptit.web_grading_system.course_service.util.annotation.ApiMessage;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService assignmentService;

    @PostMapping
    @ApiMessage("Assignment created")
    public ResponseEntity<AssignmentResponse> create(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody CreateAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.create(UUID.fromString(ownerId), request));
    }

    @GetMapping
    public ResponseEntity<Page<AssignmentResponse>> listMine(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @RequestParam(required = false) UUID classId,
            @RequestParam(required = false) Boolean published,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(
                assignmentService.listMine(UUID.fromString(ownerId), classId, published, search, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssignmentResponse> getById(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(assignmentService.getById(id, UUID.fromString(ownerId)));
    }

    @PutMapping("/{id}")
    @ApiMessage("Assignment updated")
    public ResponseEntity<AssignmentResponse> update(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody UpdateAssignmentRequest request) {
        return ResponseEntity.ok(assignmentService.update(id, UUID.fromString(ownerId), request));
    }

    @DeleteMapping("/{id}")
    @ApiMessage("Assignment deleted")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        assignmentService.delete(id, UUID.fromString(ownerId));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/publish")
    @ApiMessage("Assignment published")
    public ResponseEntity<AssignmentResponse> publish(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        return ResponseEntity.ok(assignmentService.publish(id, UUID.fromString(ownerId)));
    }
}