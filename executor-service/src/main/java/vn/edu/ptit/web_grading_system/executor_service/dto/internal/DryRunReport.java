package vn.edu.ptit.web_grading_system.executor_service.dto.internal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Dry-run grading report returned to the lecturer — no side effects on results/submission. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DryRunReport {
    private UUID dryRunId;
    private List<DryRunStepReport> steps;
    private String summary;
}
