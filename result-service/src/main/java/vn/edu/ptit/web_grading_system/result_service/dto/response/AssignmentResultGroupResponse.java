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
 * (see {@code ResultService}), not an average of the displayed rows below — it
 * can exceed any single displayed row when an older attempt scored higher.
 *
 * <p>Review: 2026-10-10, max-per-plan policy (was latest-wins average).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssignmentResultGroupResponse {

    private UUID studentUserId;
    private BigDecimal exerciseScore;

    /**
     * Display rows: the latest row per plan scope (a FULL row plus surviving
     * per-plan rows may coexist). Named {@code results} (not {@code plans}) so the same
     * record binds the Feign payload and the FE response in course-service without a
     * second, byte-identical DTO. {@code steps} is null unless requested (see
     * {@code includeSteps}).
     */
    private List<ResultResponse> results;
}
