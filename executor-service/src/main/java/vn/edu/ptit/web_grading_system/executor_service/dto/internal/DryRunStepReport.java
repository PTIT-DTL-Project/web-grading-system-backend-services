package vn.edu.ptit.web_grading_system.executor_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** Single-step result in a dry-run report. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DryRunStepReport {
    private Integer stepOrder;
    private String stepName;
    private String stepType;
    private String status;           // PASSED / FAILED / ERROR / SKIPPED
    private Integer durationMs;
    private Map<String, Object> extractedVariables;
    private String errorMessage;
}
