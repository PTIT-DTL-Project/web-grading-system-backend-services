package vn.edu.ptit.web_grading_system.result_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.UUID;

/**
 * Reads test-plan weights from course-service for max-per-plan scoring.
 * Only {@code id} + {@code weight} are used — the rest of
 * {@code InternalPlanDto} is ignored, so course-side additions don't break us.
 *
 * <p>Review: 2026-10-10, max-per-plan scoring with real plan weights.
 */
@FeignClient(name = "course-service", url = "${feign.course-service.url}")
public interface CourseServicePlansClient {

    @GetMapping("/api/v1/internal/assignments/{id}/plans")
    List<PlanWeight> plans(@PathVariable("id") UUID assignmentId);

    record PlanWeight(UUID id, Integer weight) {
    }
}
