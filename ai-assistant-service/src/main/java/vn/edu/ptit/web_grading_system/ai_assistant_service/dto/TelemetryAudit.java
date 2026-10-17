package vn.edu.ptit.web_grading_system.ai_assistant_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TelemetryAudit {
    private Integer durationMs;
    private Integer expectedThresholdMs;
    private Boolean nPlusOneDetected;
    private Integer queryCount;
    private List<String> sensitiveFieldsExposed;
    private Integer peakMemoryMb;
}
