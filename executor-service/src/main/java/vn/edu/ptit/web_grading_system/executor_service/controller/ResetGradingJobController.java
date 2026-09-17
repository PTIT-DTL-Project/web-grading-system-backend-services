package vn.edu.ptit.web_grading_system.executor_service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.executor_service.dto.request.ResetGradingJobRequest;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.service.GradingOrchestrator;
import vn.edu.ptit.web_grading_system.executor_service.service.ResetGradingJobService;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/internal/grading-jobs")
@RequiredArgsConstructor
public class ResetGradingJobController {

    private final ResetGradingJobService resetGradingJobService;
    private final GradingOrchestrator gradingOrchestrator;
    private final GradingJobRepository gradingJobRepository;

    @PostMapping("/{submissionId}/reset")
    public ResponseEntity<Map<String, Object>> reset(@PathVariable UUID submissionId,
                                                     @RequestBody ResetGradingJobRequest request) {
        log.info("Reset request: submissionId={} traceId={}", submissionId, request.getTraceId());
        var result = resetGradingJobService.reset(submissionId);
        if (result.success()) {
            GradingJob job = gradingJobRepository.findById(result.jobId()).orElse(null);
            if (job != null) {
                try {
                    gradingOrchestrator.gradeAsync(
                            result.jobId(),
                            submissionId,
                            job.getAssignmentId(),
                            job.getStudentId(),
                            job.getPlanId(),
                            job.getRustfsPath(),
                            request.getTraceId()
                    );
                } catch (TaskRejectedException saturated) {
                    log.warn("Grading pool saturated for job={}, re-grade deferred to reaper",
                            result.jobId());
                }
            }
        }
        return ResponseEntity.ok(Map.of("success", result.success(), "message", result.message(), "jobId", result.jobId()));
    }
}
