package vn.edu.ptit.web_grading_system.ai_assistant_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepAnalysis {
    private UUID stepId;
    private String stepName;
    private String stepType;
    private Boolean passed;
    private String category; // DEBUG_FAIL or OPTIMIZE_PASS
    private String diagnosis;
    private String suspectedLocation;
    private List<String> actionableHints;
    private List<String> learningKeywords;
    private TelemetryAudit telemetryAudit;
}
