package vn.edu.ptit.web_grading_system.submission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Presigned download URL for a submission zip file.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionZipDownloadResponse {

    private UUID submissionId;

    private String downloadUrl;

    private long expiresInMinutes;
}
