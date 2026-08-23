package vn.edu.ptit.web_grading_system.submission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

}
