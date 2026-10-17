package vn.edu.ptit.web_grading_system.ai_assistant_service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiExplanationResponse {
    private UUID id;
    private UUID submissionId;
    private String category;
    private Boolean fromCache;
    private String overallSummary;
    private List<StepAnalysis> stepAnalyses;
    private OffsetDateTime createdAt;
}
