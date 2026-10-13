package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One step row of a graded plan. Mirrors {@code result_service StepResultResponse}
 * field-for-field so the Feign payload and the FE response share one record.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResultStepResponse {

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
    // Review: 2026-10-12, lecturer evidence parity — mirrors result_service
    // StepResultResponse verbose fields (NULL = not captured on old rows).
    private String assertionDetails;
    private String extractedVars;
    private String requestUrl;
    private String requestBody;
    private Integer responseStatus;
}
