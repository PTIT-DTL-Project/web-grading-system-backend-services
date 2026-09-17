package vn.edu.ptit.web_grading_system.executor_service.dto.request;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class ResetGradingJobRequest {
    private UUID submissionId;
    private UUID assignmentId;
    private UUID studentId;
    private UUID planId;
    private String rustfsPath;
    private String traceId;
}
