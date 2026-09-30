package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * One graded plan of one submission, as returned by
 * {@code GET /api/v1/internal/results/assignment/{id}} (result-service) and passed straight
 * through to the FE by course-service's lecturer grading view.
 *
 * <p>Mirrors {@code result_service ResultResponse} field-for-field on purpose: the same
 * record binds the Feign payload and the public response, so there is no second copy to
 * keep in sync.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResultResponse {

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

    /** Null unless the caller asked for {@code includeSteps=true}. */
    private List<AssignmentResultStepResponse> steps;
}
