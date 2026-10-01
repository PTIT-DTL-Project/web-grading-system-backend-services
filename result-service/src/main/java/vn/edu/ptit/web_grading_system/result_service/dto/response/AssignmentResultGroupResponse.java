package vn.edu.ptit.web_grading_system.result_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One student's latest results for one assignment, served by
 * {@code GET /api/v1/internal/results/assignment/{id}} to course-service's lecturer
 * grading view.
 *
 * <p>{@code exerciseScore} is the weight-weighted average over {@code results} — the very
 * same formula {@code ResultService.weightedScoreByPlan} applies, so the class-wide view
 * and the transcript use the **same formula** — scoped to this assignment, while the
 * transcript applies the same formula across **all assignments of the class**.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResultGroupResponse {

    private UUID studentUserId;
    private BigDecimal exerciseScore;

    /**
     * One entry per graded plan. Named {@code results} (not {@code plans}) so the same
     * record binds the Feign payload and the FE response in course-service without a
     * second, byte-identical DTO. {@code steps} is null unless requested (see
     * {@code includeSteps}).
     */
    private List<ResultResponse> results;
}
