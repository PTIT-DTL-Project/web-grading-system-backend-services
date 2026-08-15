package vn.edu.ptit.web_grading_system.submission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.edu.ptit.web_grading_system.submission_service.entities.Submission;

import java.time.OffsetDateTime;
import java.util.UUID;

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

    public static SubmissionResponse from(Submission s) {
        return SubmissionResponse.builder()
                .id(s.getId())
                .assignmentId(s.getAssignmentId())
                .studentId(s.getStudentId())
                .zipFileName(s.getZipFileName())
                .status(s.getStatus().name())
                .latest(s.getLatest())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
