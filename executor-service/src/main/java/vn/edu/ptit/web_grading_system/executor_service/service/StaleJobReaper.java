package vn.edu.ptit.web_grading_system.executor_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.edu.ptit.web_grading_system.executor_service.config.ExecutorProperties;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Crash recovery: re-enqueues jobs stuck in a non-terminal state (pod died
 * mid-grading). Skips jobs younger than the stale threshold and jobs that
 * already exhausted max attempts — those need a human.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaleJobReaper {

    private static final List<GradingJobStatus> ACTIVE = List.of(
            GradingJobStatus.PENDING,
            GradingJobStatus.FETCHING,
            GradingJobStatus.BUILDING,
            GradingJobStatus.RUNNING);

    private final GradingJobRepository gradingJobRepository;
    private final GradingOrchestrator gradingOrchestrator;
    private final ExecutorProperties executorProperties;

    @Scheduled(fixedDelayString = "${executor.reaper.interval-ms:300000}")
    public void reap() {
        long staleAfterMinutes = executorProperties.reaper().staleAfterMinutes();
        int maxAttempts = executorProperties.reaper().maxAttempts();
        OffsetDateTime cutoff = OffsetDateTime.now().minusMinutes(staleAfterMinutes);
        List<GradingJob> stale = gradingJobRepository.findByStatusIn(ACTIVE).stream()
                .filter(job -> isStale(job, cutoff))
                .filter(job -> job.getRetryCount() < maxAttempts)
                .toList();
        for (GradingJob job : stale) {
            job.setRetryCount(job.getRetryCount() + 1);
            gradingJobRepository.save(job);
            log.warn(Constant.Message.REENQUEUE_PREFIX,
                    job.getId(), job.getStatus(), job.getRetryCount());
            gradingOrchestrator.gradeAsync(job.getId(), job.getSubmissionId(),
                    job.getAssignmentId(), job.getStudentId(), job.getPlanId(),
                    job.getRustfsPath(), Constant.Reaper.TRACE_ID);
        }
    }

    private static boolean isStale(GradingJob job, OffsetDateTime cutoff) {
        OffsetDateTime start = job.getStartedAt() != null ? job.getStartedAt() : job.getCreatedAt();
        return start != null && start.isBefore(cutoff);
    }
}
