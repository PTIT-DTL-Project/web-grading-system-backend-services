package vn.edu.ptit.web_grading_system.course_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "result-service", url = "${feign.result-service.url}")
public interface ResultServiceClient {

    @PostMapping("/api/v1/internal/results/average")
    Map<String, BigDecimal> average(@RequestBody AverageRequest request);

    record AverageRequest(List<UUID> assignmentIds, UUID studentId) {
    }
}