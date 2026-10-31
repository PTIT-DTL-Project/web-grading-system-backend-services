package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Dry-run request sent from course-service to executor-service. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DryRunRequest {
    private UUID assignmentId;
    private String referenceCodeZipUrl;
    private List<InternalPlanDto> plans;
}
