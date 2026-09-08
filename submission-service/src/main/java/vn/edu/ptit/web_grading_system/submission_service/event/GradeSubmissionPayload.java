package vn.edu.ptit.web_grading_system.submission_service.event;

import java.util.UUID;
import lombok.Builder;

/**
 * Payload for action = GRADE_SUBMISSION on the wgs-events topic.
 */
@Builder
public record GradeSubmissionPayload(
        UUID submissionId,
        UUID assignmentId,
        UUID studentId,
        UUID planId,
        String rustfsPath) {
}
