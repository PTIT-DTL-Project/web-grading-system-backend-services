package vn.edu.ptit.web_grading_system.executor_service.event.handler;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingLogRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class GradeSubmissionHandlerTest {

    private final GradingJobRepository jobRepo = Mockito.mock(GradingJobRepository.class);
    private final GradingLogRepository logRepo = Mockito.mock(GradingLogRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final GradeSubmissionHandler handler = new GradeSubmissionHandler(jobRepo, logRepo);

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
    }

    @Test
    void handle_duplicateJob_skipsGracefully() throws Exception {
        String json = """
                {"submissionId":"%s","assignmentId":"%s","studentId":"%s","planId":null}
                """.formatted(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        Mockito.when(jobRepo.save(Mockito.any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        assertDoesNotThrow(() -> handler.handle(mapper.readTree(json), "trace-2"));
        Mockito.verify(logRepo, Mockito.never()).save(Mockito.any());
    }
}