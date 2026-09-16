package vn.edu.ptit.web_grading_system.result_service.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @NotNull
    private UUID submissionId;
    @NotNull
    private UUID assignmentId;
    @NotNull
    private UUID studentId;
    @NotNull
    private BigDecimal score;
    private UUID planId;
    private Integer planWeight;
    private BigDecimal maxScore;
    private String status;
    private String summaryLog;
    @Valid
    private List<@Valid StepResultItem> stepResults;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StepResultItem {
        private UUID planId;
        private UUID stepId;
        @NotNull
        private Integer stepOrder;
        @NotBlank
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
