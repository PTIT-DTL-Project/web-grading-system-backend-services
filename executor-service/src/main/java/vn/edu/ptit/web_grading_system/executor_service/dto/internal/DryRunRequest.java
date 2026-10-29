package vn.edu.ptit.web_grading_system.executor_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Dry-run request: assignment + plans + reference code zip URL. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DryRunRequest {
    private UUID assignmentId;
    private String referenceCodeZipUrl;
    private List<InternalPlanDto> plans;
}
