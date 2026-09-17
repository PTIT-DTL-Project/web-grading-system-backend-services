package vn.edu.ptit.web_grading_system.executor_service.event.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;import tools.jackson.databind.JsonNode;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingLog;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingLogLevel;
import vn.edu.ptit.web_grading_system.executor_service.event.EventHandler;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.event.WgsEventAction;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingLogRepository;
import vn.edu.ptit.web_grading_system.executor_service.service.GradingOrchestrator;
import vn.edu.ptit.web_grading_system.executor_service.service.ResetGradingJobService;

import java.util.UUID;
import java.util.Optional;

/**
 * GRADE_SUBMISSION handler: persists a PENDING grading job (idempotent — unique
 * constraint on submission_id is the race backstop; see V2 migration), then
 * hands off to the async grading orchestrator.
 * On duplicate constraint and existing job is FAILED, triggers a reset via
 * ResetGradingJobService instead of silently skipping.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeSubmissionHandler implements EventHandler {

    private final GradingJobRepository gradingJobRepository;
    private final GradingLogRepository gradingLogRepository;
    private final GradingOrchestrator gradingOrchestrator;
    private final ResetGradingJobService resetGradingJobService;

    @Override
    public WgsEventAction action() {
        return WgsEventAction.GRADE_SUBMISSION;
    }

    @Override
    public void handle(JsonNode payload, String traceId) {
        UUID submissionId = UUID.fromString(payload.path(Constant.Event.SUBMISSION_ID).asString());
        UUID assignmentId = UUID.fromString(payload.path(Constant.Event.ASSIGNMENT_ID).asString());
        UUID studentId = UUID.fromString(payload.path(Constant.Event.STUDENT_ID).asString());
        String planIdText = Optional.ofNullable(payload.path(Constant.Event.PLAN_ID).asString()).filter(s -> !s.isEmpty()).orElse(null);
        UUID planId = planIdText == null ? null : UUID.fromString(planIdText);
        String rustfsPath = Optional.ofNullable(payload.path(Constant.Event.RUSTFS_PATH).asString()).filter(s -> !s.isEmpty()).orElse(null);

        try {
            GradingJob job = gradingJobRepository.save(GradingJob.builder()
                    .submissionId(submissionId)
                    .assignmentId(assignmentId)
                    .studentId(studentId)
                    .planId(planId)
                    .rustfsPath(rustfsPath)
                    .traceId(traceId)
                    .status(GradingJobStatus.PENDING)
                    .build());
            gradingLogRepository.save(GradingLog.builder()
                    .jobId(job.getId())
                    .submissionId(submissionId)
                    .step(Constant.GradingLog.STEP_RECEIVED)
                    .level(GradingLogLevel.INFO)
                    .message(Constant.Message.GRADE_RECEIVED + traceId + Constant.Message.GRADE_RECEIVED_SUFFIX)
                    .build());
            log.info(Constant.Message.GRADE_PERSISTED_PREFIX, job.getId(), submissionId);
            gradingOrchestrator.gradeAsync(job.getId(), submissionId, assignmentId,
                    studentId, planId, rustfsPath, traceId);
        } catch (DataIntegrityViolationException duplicate) {
            Optional<GradingJob> existing = gradingJobRepository.findBySubmissionId(submissionId);
            if (existing.isPresent() && existing.get().getStatus() == GradingJobStatus.FAILED) {
                log.warn("Existing grading job for submission={} is FAILED, triggering reset and re-grading", submissionId);
                resetGradingJobService.reset(submissionId);
                try {
                    gradingOrchestrator.gradeAsync(
                            existing.get().getId(), submissionId,
                            existing.get().getAssignmentId(), existing.get().getStudentId(),
                            existing.get().getPlanId(), existing.get().getRustfsPath(),
                            Constant.Reaper.TRACE_ID);
                } catch (TaskRejectedException saturated) {
                    log.warn("Grading pool saturated for submission={}, job stays PENDING for reaper recovery",
                            submissionId);
                }
            } else {
                log.info(Constant.Message.GRADE_DUPLICATE_PREFIX, submissionId);
            }
        } catch (TaskRejectedException saturated) {
            log.warn("Grading pool saturated for submission={}, job stays PENDING for reaper recovery",
                    submissionId);
        }
    }
}
