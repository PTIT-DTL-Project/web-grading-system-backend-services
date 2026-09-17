package vn.edu.ptit.web_grading_system.executor_service.config;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingSaga;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingSagaRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingSagaStepRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingStepResultRepository;
import vn.edu.ptit.web_grading_system.executor_service.service.ResetGradingJobService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResetGradingJobServiceTest {

    @Test
    void reset_jobNotFound_returnsFalse() {
        var repo = mock(GradingJobRepository.class);
        when(repo.findBySubmissionId(any())).thenReturn(Optional.empty());
        var svc = new ResetGradingJobService(repo, mock(GradingSagaRepository.class),
                mock(GradingSagaStepRepository.class),
                mock(GradingStepResultRepository.class));
        var r = svc.reset(UUID.randomUUID());
        assertFalse(r.success());
        assertEquals("No grading job found for submission", r.message());
    }

    @Test
    void reset_jobNotFAILED_returnsFalse() {
        var repo = mock(GradingJobRepository.class);
        GradingJob job = GradingJob.builder().status(GradingJobStatus.PENDING).build();
        when(repo.findBySubmissionId(any())).thenReturn(Optional.of(job));
        var svc = new ResetGradingJobService(repo, mock(GradingSagaRepository.class),
                mock(GradingSagaStepRepository.class),
                mock(GradingStepResultRepository.class));
        var r = svc.reset(UUID.randomUUID());
        assertFalse(r.success());
        assertTrue(r.message().contains("not FAILED"));
        verify(repo, never()).save(any());
    }

    @Test
    void reset_success_callsDeleteAndSave() {
        var repo = mock(GradingJobRepository.class);
        GradingJob job = GradingJob.builder().status(GradingJobStatus.FAILED).retryCount(1).build();
        when(repo.findBySubmissionId(any())).thenReturn(Optional.of(job));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID sagaId = UUID.randomUUID();
        var saga = GradingSaga.builder().id(sagaId).build();
        var stepRepo = mock(GradingStepResultRepository.class);
        var sagaRepo = mock(GradingSagaRepository.class);
        var sagaStepRepo = mock(GradingSagaStepRepository.class);
        when(sagaRepo.findByJobId(nullable(UUID.class))).thenReturn(List.of(saga));
        var svc = new ResetGradingJobService(repo, sagaRepo, sagaStepRepo, stepRepo);
        var r = svc.reset(UUID.randomUUID());

        assertTrue(r.success());
        assertEquals("Job reset to PENDING", r.message());
        verify(repo).findBySubmissionId(any());
        verify(stepRepo).deleteByJobId(any());
        verify(sagaRepo).findByJobId(any());
        verify(sagaStepRepo).deleteBySagaId(any());
        verify(sagaRepo).resetByJobId(any(), any());
        verify(repo).save(any());
    }
}
