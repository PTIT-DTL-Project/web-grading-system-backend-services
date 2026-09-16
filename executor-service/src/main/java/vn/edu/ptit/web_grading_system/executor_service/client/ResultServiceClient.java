package vn.edu.ptit.web_grading_system.executor_service.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import vn.edu.ptit.web_grading_system.executor_service.config.FeignLoggingConfiguration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "result-service", url = "${feign.result-service.url}",
        configuration = FeignLoggingConfiguration.class)
public interface ResultServiceClient {

    @PostMapping("/api/v1/internal/results")
    Map<String, UUID> create(@RequestBody CreateResultRequest request);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class CreateResultRequest {
        private UUID submissionId;
        private UUID assignmentId;
        private UUID studentId;
        private UUID planId;
        private Integer planWeight;
        private BigDecimal score;
        private BigDecimal maxScore;
        private String status;
        private String summaryLog;
        private List<StepResultItem> stepResults;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    class StepResultItem {
        private UUID planId;
        private UUID stepId;
        private Integer stepOrder;
        private String stepName;
        private String stepType;
        private Boolean passed;
        private Integer weight;
        private BigDecimal score;
        private String actualValue;
        private String expectedValue;
        private String errorMessage;
        private Integer durationMs;
    }
}
