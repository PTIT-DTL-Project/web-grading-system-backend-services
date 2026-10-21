package vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionSummaryDto {
    private UUID id;
    private UUID assignmentId;
    private UUID studentId;
    private String status;
    private Double score;
    private Instant createdAt;
}
