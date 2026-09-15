package vn.edu.ptit.web_grading_system.result_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResultResponse {
    private UUID id;
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
