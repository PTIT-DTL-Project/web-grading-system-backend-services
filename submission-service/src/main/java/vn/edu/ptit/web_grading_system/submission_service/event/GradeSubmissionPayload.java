package vn.edu.ptit.web_grading_system.submission_service.event;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload for action = GRADE_SUBMISSION on the wgs-events topic.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradeSubmissionPayload {
    private UUID submissionId;
    private UUID assignmentId;
    private UUID studentId;
    private UUID planId;
    private String rustfsPath;
}
