package vn.edu.ptit.web_grading_system.executor_service.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.task.TaskRejectedException;
import vn.edu.ptit.web_grading_system.executor_service.dto.request.ResetGradingJobRequest;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.service.GradingOrchestrator;
import vn.edu.ptit.web_grading_system.executor_service.service.ResetGradingJobService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResetGradingJobControllerTest {

    @Test
    void reset_saturatedPool_stillReturns200() {
        UUID submissionId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        GradingJob job = GradingJob.builder()
                .submissionId(submissionId)
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .status(GradingJobStatus.PENDING)
                .build();
        job.setId(jobId);
        ResetGradingJobService resetService = Mockito.mock(ResetGradingJobService.class);
        Mockito.when(resetService.reset(submissionId))
                .thenReturn(new ResetGradingJobService.ResetResult(true, "Job reset to PENDING", jobId, job));
        GradingJobRepository jobRepo = Mockito.mock(GradingJobRepository.class);
        Mockito.when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        GradingOrchestrator orchestrator = Mockito.mock(GradingOrchestrator.class);
        Mockito.doThrow(new TaskRejectedException("pool saturated")).when(orchestrator)
                .gradeAsync(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                        Mockito.isNull(), Mockito.isNull(), Mockito.any());

        var controller = new ResetGradingJobController(resetService, orchestrator, jobRepo);
        var request = ResetGradingJobRequest.builder().traceId("trace-1").build();

        var response = assertDoesNotThrow(() -> controller.reset(submissionId, request));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("success"));
    }
}
