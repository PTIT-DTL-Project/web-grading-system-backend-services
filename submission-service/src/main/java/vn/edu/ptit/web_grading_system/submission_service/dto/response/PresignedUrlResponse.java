package vn.edu.ptit.web_grading_system.submission_service.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresignedUrlResponse {
    private UUID submissionId;
    private String uploadUrl;
    private String objectName;
    private long expiresInMinutes;
}
