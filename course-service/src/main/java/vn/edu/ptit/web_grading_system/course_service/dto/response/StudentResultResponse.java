package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * One student's auto-grading results for one assignment — the lecturer's grading view,
 * served by {@code GET /api/v1/assignments/{id}/results}.
 *
 * <p>result-service sends {@code studentUserId}, {@code exerciseScore} and {@code results};
 * course-service owns the class roster, so it is the only layer that can add
 * {@code studentCode}/{@code studentName}. A result row whose student is no longer in the
 * roster keeps its raw {@code studentUserId} with null code/name rather than being hidden.
 *
 * <p>{@code exerciseScore} comes pre-computed from result-service's single weighted
 * formula — course-service never re-derives it, so it cannot drift from the transcript.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResultResponse {

    private UUID studentUserId;
    private String studentCode;
    private String studentName;
    private BigDecimal exerciseScore;
    private List<AssignmentResultResponse> results;
}
