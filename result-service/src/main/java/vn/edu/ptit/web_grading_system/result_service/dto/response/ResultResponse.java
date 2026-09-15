package vn.edu.ptit.web_grading_system.result_service.dto.response;

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
public class ResultResponse {
    private UUID id;
    private UUID submissionId;
    private UUID assignmentId;
    private UUID studentId;
    private UUID planId;
    private Integer planWeight;
    private BigDecimal score;
    private BigDecimal maxScore;
    private String status;
    private String summaryLog;
    private Boolean latest;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
    private List<StepResultResponse> steps;
}
