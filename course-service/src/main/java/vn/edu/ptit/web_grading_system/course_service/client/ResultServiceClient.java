package vn.edu.ptit.web_grading_system.course_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import vn.edu.ptit.web_grading_system.course_service.config.FeignLoggingConfiguration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "result-service", url = "${feign.result-service.url}",
        configuration = FeignLoggingConfiguration.class)
public interface ResultServiceClient {

    @PostMapping("/api/v1/internal/results/weighted")
    Map<String, BigDecimal> weighted(@RequestBody AverageRequest request);

    record AverageRequest(List<UUID> assignmentIds, UUID studentId) {
    }
}