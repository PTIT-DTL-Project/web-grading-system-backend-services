package vn.edu.ptit.web_grading_system.course_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * A submission row of one assignment, as returned by
 * {@code GET /api/v1/internal/submissions/assignment/{id}} (submission-service) and passed
 * straight through to the FE by course-service's lecturer grading view.
 *
 * <p>Mirrors {@code submission_service SubmissionResponse} field-for-field so the Feign
 * payload and the public response share one record, plus roster enrichment below.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {

    private UUID id;
    private UUID assignmentId;
    private UUID studentId;
    private String zipFileName;
    private String status;
    private Boolean latest;
    private OffsetDateTime createdAt;
    /** Null = whole-assignment submission; set when the student picked one plan. */
    private UUID planId;
    /**
     * Roster code/name enriched by course-service (submission-service only knows
     * the Keycloak user id). Null when the student left the roster — the row is
     * still returned, like the results view does.
     */
    private String studentCode;
    private String studentName;
}
