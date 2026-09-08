package vn.edu.ptit.web_grading_system.executor_service.event.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingLog;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingLogLevel;
import vn.edu.ptit.web_grading_system.executor_service.event.EventHandler;
import vn.edu.ptit.web_grading_system.executor_service.event.WgsEventAction;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingLogRepository;

import java.util.UUID;

/**
 * GRADE_SUBMISSION handler: persists a PENDING grading job (idempotent — unique
 * constraint on submission_id is the race backstop; see V2 migration).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GradeSubmissionHandler implements EventHandler {

    private final GradingJobRepository gradingJobRepository;
    private final GradingLogRepository gradingLogRepository;

    @Override
    public WgsEventAction action() {
        return WgsEventAction.GRADE_SUBMISSION;
    }

    @Override
    public void handle(JsonNode payload, String traceId) {
        UUID submissionId = UUID.fromString(payload.path("submissionId").asText());
        UUID assignmentId = UUID.fromString(payload.path("assignmentId").asText());
        UUID studentId = UUID.fromString(payload.path("studentId").asText());
        String planIdText = payload.path("planId").asText(null);
        UUID planId = planIdText == null ? null : UUID.fromString(planIdText);

        try {
            GradingJob job = gradingJobRepository.save(GradingJob.builder()
                    .submissionId(submissionId)
                    .assignmentId(assignmentId)
                    .studentId(studentId)
                     .planId(planId)
                    // ponytail: planId is captured end-to-end; the (future) grading
                    // orchestrator must run only this plan's steps when planId != null
                    // (execute-plan-v1.0.md §3 step 1), else all plans.
                    .status(GradingJobStatus.PENDING)
                    .build());
            gradingLogRepository.save(GradingLog.builder()
                    .jobId(job.getId())
                    .submissionId(submissionId)
                    .step("RECEIVED")
                    .level(GradingLogLevel.INFO)
                    .message("Grading job received from wgs-events (traceId=" + traceId + ")")
                    .build());
            log.info("Grading job persisted: id={} submission={}", job.getId(), submissionId);
        } catch (DataIntegrityViolationException duplicate) {
            // unique constraint hit — Kafka redelivery; safe to ignore
            log.info("Grading job already exists for submission={}, skipping", submissionId);
        }
    }
}