package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.task.TaskRejectedException;
import vn.edu.ptit.web_grading_system.executor_service.config.ExecutorProperties;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

class StaleJobReaperTest {

    private static ExecutorProperties props() {
        return new ExecutorProperties("tmp",
                new ExecutorProperties.Container(1000, 2000),
                new ExecutorProperties.Reaper(30, 300000, 3),
                new ExecutorProperties.Maven(null));
    }

    private static GradingJob job(GradingJobStatus status, OffsetDateTime startedAt,
            OffsetDateTime createdAt, int retryCount) {
        GradingJob job = GradingJob.builder()
                .submissionId(UUID.randomUUID())
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .status(status)
                .retryCount(retryCount)
                .startedAt(startedAt)
                .build();
        job.setId(UUID.randomUUID());
        job.setCreatedAt(createdAt);
        return job;
    }

    @Test
    void reapsStalePendingJob() {
        GradingJob stale = job(GradingJobStatus.PENDING, null,
                OffsetDateTime.now().minusHours(2), 0);
        GradingJobRepository repo = Mockito.mock(GradingJobRepository.class);
        Mockito.when(repo.findByStatusIn(Mockito.any())).thenReturn(List.of(stale));
        GradingOrchestrator orchestrator = Mockito.mock(GradingOrchestrator.class);

        new StaleJobReaper(repo, orchestrator, props()).reap();

        Mockito.verify(orchestrator).gradeAsync(
                stale.getId(), stale.getSubmissionId(), stale.getAssignmentId(),
                stale.getStudentId(), stale.getPlanId(), stale.getRustfsPath(), "reaper");
        Mockito.verify(repo).save(Mockito.argThat(saved -> saved.getRetryCount() == 1));
    }

    @Test
    void skipsFreshRunningJob() {
        GradingJob fresh = job(GradingJobStatus.RUNNING,
                OffsetDateTime.now().minusMinutes(2), OffsetDateTime.now().minusMinutes(5), 0);
        GradingJobRepository repo = Mockito.mock(GradingJobRepository.class);
        Mockito.when(repo.findByStatusIn(Mockito.any())).thenReturn(List.of(fresh));
        GradingOrchestrator orchestrator = Mockito.mock(GradingOrchestrator.class);

        new StaleJobReaper(repo, orchestrator, props()).reap();

        Mockito.verify(orchestrator, Mockito.never()).gradeAsync(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.isNull(), Mockito.isNull(), Mockito.any());
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void skipsJobThatExhaustedAttempts() {
        GradingJob poison = job(GradingJobStatus.FAILED, null,
                OffsetDateTime.now().minusHours(5), 3);
        GradingJobRepository repo = Mockito.mock(GradingJobRepository.class);
        // FAILED is not in the ACTIVE filter anyway; stale RUNNING with 3 attempts:
        GradingJob exhausted = job(GradingJobStatus.RUNNING,
                OffsetDateTime.now().minusHours(5), OffsetDateTime.now().minusHours(5), 3);
        Mockito.when(repo.findByStatusIn(Mockito.any()))
                .thenReturn(List.of(poison, exhausted));
        GradingOrchestrator orchestrator = Mockito.mock(GradingOrchestrator.class);

        new StaleJobReaper(repo, orchestrator, props()).reap();

        Mockito.verify(orchestrator, Mockito.never()).gradeAsync(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.isNull(), Mockito.isNull(), Mockito.any());
    }

    @Test
    void reapQueryExcludesRunning() {
        GradingJobRepository repo = Mockito.mock(GradingJobRepository.class);
        Mockito.when(repo.findByStatusIn(Mockito.any())).thenReturn(List.of());
        GradingOrchestrator orchestrator = Mockito.mock(GradingOrchestrator.class);

        new StaleJobReaper(repo, orchestrator, props()).reap();

        Mockito.verify(repo).findByStatusIn(Mockito.argThat(
                statuses -> !statuses.contains(GradingJobStatus.RUNNING)));
    }

    @Test
    void saturatedPool_doesNotThrow() {
        GradingJob stale = job(GradingJobStatus.PENDING, null,
                OffsetDateTime.now().minusHours(2), 0);
        GradingJobRepository repo = Mockito.mock(GradingJobRepository.class);
        Mockito.when(repo.findByStatusIn(Mockito.any())).thenReturn(List.of(stale));
        GradingOrchestrator orchestrator = Mockito.mock(GradingOrchestrator.class);
        Mockito.doThrow(new TaskRejectedException("pool saturated")).when(orchestrator)
                .gradeAsync(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.isNull(), Mockito.isNull(), Mockito.any());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> new StaleJobReaper(repo, orchestrator, props()).reap());
        Mockito.verify(repo, Mockito.never()).save(Mockito.any());
    }
}
