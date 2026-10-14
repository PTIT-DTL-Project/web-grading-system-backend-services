package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultDto {
    private UUID id;
    private UUID submissionId;
    private UUID assignmentId;
    private UUID studentId;
    private UUID planId;
    private Integer planWeight;
    private String scope;
    private BigDecimal score;
    private BigDecimal maxScore;
    private String status;
    private String summaryLog;
    private Boolean latest;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private List<StepResultDto> steps;
}
