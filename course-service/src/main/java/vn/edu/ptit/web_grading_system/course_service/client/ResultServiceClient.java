package vn.edu.ptit.web_grading_system.course_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import vn.edu.ptit.web_grading_system.course_service.config.FeignLoggingConfiguration;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentResultResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "result-service", url = "${feign.result-service.url}",
        configuration = FeignLoggingConfiguration.class)
public interface ResultServiceClient {

    @PostMapping("/api/v1/internal/results/weighted")
    Map<String, BigDecimal> weighted(@RequestBody AverageRequest request);

    /**
     * Class-wide auto-grading results for one assignment, grouped per student with the
     * exercise score already computed by result-service's single weighted formula — this
     * client never re-derives it.
     *
     * <p>{@code studentUserId} is a nullable filter; OpenFeign drops a null query param
     * rather than sending the literal {@code "null"} (verified against feign-core 13.6.1 +
     * spring-cloud-openfeign 5.0.2), so a whole-class read needs no second method.
     */
    @GetMapping("/api/v1/internal/results/assignment/{assignmentId}")
    List<StudentResultResponse> assignmentResults(
            @PathVariable("assignmentId") UUID assignmentId,
            @RequestParam(value = "studentUserId", required = false) UUID studentUserId,
            @RequestParam("includeSteps") boolean includeSteps);

    record AverageRequest(List<UUID> assignmentIds, UUID studentId) {
    }
}
