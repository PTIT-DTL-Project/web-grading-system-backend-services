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
    private Boolean skipped;
    private Integer weight;
    private BigDecimal score;
    private String actualValue;
    private String expectedValue;
    private String errorMessage;
    private Integer durationMs;
    // Review: 2026-10-11, phase A2 verbose evidence (NULL = not captured).
    private String assertionDetails;
    private String extractedVars;
    private String requestUrl;
    private String requestBody;
    private Integer responseStatus;
    private Integer dbQueryCount;
    private String dbQueryLogs;
    private String stackTrace;
    private Integer peakMemoryMb;
}
