package vn.edu.ptit.web_grading_system.course_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateDockerImageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateDockerImageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.DockerImageResponse;
import vn.edu.ptit.web_grading_system.course_service.service.DockerImageService;
import vn.edu.ptit.web_grading_system.course_service.util.annotation.ApiMessage;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/docker-images")
@RequiredArgsConstructor
public class DockerImageController {

    private final DockerImageService dockerImageService;

    @PreAuthorize("hasRole('LECTURER')")
    @PostMapping
    @ApiMessage("Docker image created")
    public ResponseEntity<DockerImageResponse> create(
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody CreateDockerImageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(dockerImageService.create(UUID.fromString(ownerId), request));
    }

    @PreAuthorize("hasRole('LECTURER')")
    @GetMapping
    public ResponseEntity<Page<DockerImageResponse>> list(
            @RequestParam(required = false) String name,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(dockerImageService.list(name, pageable));
    }

    @PreAuthorize("hasRole('LECTURER')")
    @GetMapping("/{id}")
    public ResponseEntity<DockerImageResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(dockerImageService.getById(id));
    }

    @PreAuthorize("hasRole('LECTURER')")
    @PutMapping("/{id}")
    @ApiMessage("Docker image updated")
    public ResponseEntity<DockerImageResponse> update(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId,
            @Valid @RequestBody UpdateDockerImageRequest request) {
        return ResponseEntity.ok(
                dockerImageService.update(id, UUID.fromString(ownerId), request));
    }

    @PreAuthorize("hasRole('LECTURER')")
    @DeleteMapping("/{id}")
    @ApiMessage("Docker image deleted")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String ownerId) {
        dockerImageService.delete(id, UUID.fromString(ownerId));
        return ResponseEntity.ok().build();
    }
}
