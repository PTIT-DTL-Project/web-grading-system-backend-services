package vn.edu.ptit.web_grading_system.course_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.ptit.web_grading_system.course_service.service.DockerImageService;

import java.util.List;

/**
 * Internal contract for executor-service. Returns the active image URLs so the
 * async pre-pull scanner can decide what to inspect/pull. Raw strings — no
 * envelope, never exposed via the gateway.
 */
@RestController
@RequestMapping("/api/v1/internal/docker-images")
@RequiredArgsConstructor
public class InternalDockerImageController {

    private final DockerImageService dockerImageService;

    @GetMapping
    public ResponseEntity<List<String>> list() {
        return ResponseEntity.ok(dockerImageService.listActiveUrls());
    }
}
