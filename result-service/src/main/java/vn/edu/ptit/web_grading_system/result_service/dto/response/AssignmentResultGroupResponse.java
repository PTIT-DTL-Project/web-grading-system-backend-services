package vn.edu.ptit.web_grading_system.result_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One student's attempt rows for one assignment, served by
 * {@code GET /api/v1/internal/results/assignment/{id}} to course-service's lecturer
 * grading view.
 *
 * <p>{@code exerciseScore} is the max-per-plan best over <b>every</b> attempt
 * (see {@code ResultService}), and {@code results} carries exactly the rows
 * that score is made of — the best attempt per plan — so the displayed rows
 * can no longer be lower than the headline score. The set falls back to the
 * latest per-plan rows only when nothing is scorable (e.g. a sole stepless
 * failed FULL run), so the detail is never blank.
 *
 * <p>Review: 2026-10-10, max-per-plan policy (was latest-wins average).
 * Updated 2026-10-17: displayed rows are now the graded attempts, not the
 * latest ones (a 10-scored-then-5-resubmit student read 10 next to a 5 row).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResultGroupResponse {

    private UUID studentUserId;
    private BigDecimal exerciseScore;

    /**
     * Display rows: the attempts the exercise score is made of — the best attempt per
     * plan scope (a FULL row plus one surviving per-plan row per plan may both be
     * present, all from the graded attempts). Named {@code results} (not {@code plans})
     * so the same record binds the Feign payload and the FE response in
     * course-service without a second, byte-identical DTO. {@code steps} is null
     * unless requested (see {@code includeSteps}).
     */
    private List<ResultResponse> results;
}
