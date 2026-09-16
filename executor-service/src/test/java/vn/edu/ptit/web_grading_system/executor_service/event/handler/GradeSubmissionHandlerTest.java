package vn.edu.ptit.web_grading_system.executor_service.event.handler;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingLogRepository;
import vn.edu.ptit.web_grading_system.executor_service.service.GradingOrchestrator;
import vn.edu.ptit.web_grading_system.executor_service.service.ResetGradingJobService;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeSubmissionHandlerTest {

    private final GradingJobRepository jobRepo = Mockito.mock(GradingJobRepository.class);
    private final GradingLogRepository logRepo = Mockito.mock(GradingLogRepository.class);
    private final GradingOrchestrator orchestrator = Mockito.mock(GradingOrchestrator.class);
    private final ResetGradingJobService resetService = Mockito.mock(ResetGradingJobService.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final GradeSubmissionHandler handler =
            new GradeSubmissionHandler(jobRepo, logRepo, orchestrator, resetService);

    @Test
    void handle_persistsPendingJobWithLog() throws Exception {
        UUID subId = UUID.randomUUID();
        String json = """
                {"submissionId":"%s","assignmentId":"%s","studentId":"%s","planId":null}
                """.formatted(subId, UUID.randomUUID(), UUID.randomUUID());
        Mockito.when(jobRepo.save(Mockito.any())).thenAnswer(inv -> {
            GradingJob j = inv.getArgument(0);
            j.setId(UUID.randomUUID());
            return j;
        });

        handler.handle(mapper.readTree(json), "trace-1");

        Mockito.verify(jobRepo).save(Mockito.argThat(j ->
                j.getSubmissionId().equals(subId) && j.getStatus() == GradingJobStatus.PENDING));
        Mockito.verify(logRepo).save(Mockito.argThat(l -> l.getStep().equals("RECEIVED")));
        Mockito.verify(orchestrator).gradeAsync(Mockito.any(), Mockito.eq(subId),
                Mockito.any(), Mockito.any(), Mockito.isNull(), Mockito.isNull(),
                Mockito.eq("trace-1"));
    }

    @Test
    void handle_duplicateJob_skipsGracefully() throws Exception {
        UUID subId = UUID.randomUUID();
        String json = """
                {"submissionId":"%s","assignmentId":"%s","studentId":"%s","planId":null}
                """.formatted(subId, UUID.randomUUID(), UUID.randomUUID());
        Mockito.when(jobRepo.save(Mockito.any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        Mockito.when(jobRepo.findBySubmissionId(subId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> handler.handle(mapper.readTree(json), "trace-2"));
        Mockito.verify(logRepo, Mockito.never()).save(Mockito.any());
        Mockito.verify(orchestrator, Mockito.never()).gradeAsync(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any());
        Mockito.verify(resetService, Mockito.never()).reset(Mockito.any());
    }

    @Test
    void handle_duplicateFailedJob_triggersReset() throws Exception {
        UUID subId = UUID.randomUUID();
        String json = """
                {"submissionId":"%s","assignmentId":"%s","studentId":"%s","planId":null}
                """.formatted(subId, UUID.randomUUID(), UUID.randomUUID());
        Mockito.when(jobRepo.save(Mockito.any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        GradingJob failedJob = GradingJob.builder()
                .submissionId(subId)
                .status(GradingJobStatus.FAILED)
                .build();
        failedJob.setId(UUID.randomUUID());
        Mockito.when(jobRepo.findBySubmissionId(subId)).thenReturn(Optional.of(failedJob));

        assertDoesNotThrow(() -> handler.handle(mapper.readTree(json), "trace-3"));
        Mockito.verify(resetService).reset(subId);
        Mockito.verify(orchestrator, Mockito.never()).gradeAsync(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(),
                Mockito.any(), Mockito.any(), Mockito.any());
    }
}
