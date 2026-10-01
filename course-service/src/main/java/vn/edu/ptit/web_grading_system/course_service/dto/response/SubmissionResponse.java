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
 * payload and the public response share one record.
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
}
