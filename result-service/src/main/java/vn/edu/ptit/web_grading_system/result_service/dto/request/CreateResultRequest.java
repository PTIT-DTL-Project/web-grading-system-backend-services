package vn.edu.ptit.web_grading_system.result_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateResultRequest {
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResultItem {
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
