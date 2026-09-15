package vn.edu.ptit.web_grading_system.result_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.result_service.dto.request.CreateResultRequest;
import vn.edu.ptit.web_grading_system.result_service.entities.Result;
import vn.edu.ptit.web_grading_system.result_service.entities.ResultStatus;
import vn.edu.ptit.web_grading_system.result_service.entities.StepResult;
import vn.edu.ptit.web_grading_system.result_service.repositories.ResultRepository;
import vn.edu.ptit.web_grading_system.result_service.repositories.StepResultRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultServiceCreateTest {

    private record Fixture(ResultService service, ResultRepository results,
                           StepResultRepository steps) {
    }

    private Fixture fixture() {
        ResultRepository results = Mockito.mock(ResultRepository.class);
        StepResultRepository steps = Mockito.mock(StepResultRepository.class);
        Mockito.when(results.save(Mockito.any())).thenAnswer(inv -> {
            Result r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        return new Fixture(new ResultService(results, steps), results, steps);
    }

    private CreateResultRequest request(UUID planId) {
        return CreateResultRequest.builder()
                .submissionId(UUID.randomUUID())
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .planId(planId)
                .planWeight(2)
                .score(new BigDecimal("7.50"))
                .status("DONE")
                .summaryLog("Passed 3/4 steps")
                .stepResults(List.of(CreateResultRequest.StepResultItem.builder()
                        .planId(planId)
                        .stepId(UUID.randomUUID())
                        .stepOrder(0)
                        .stepName("s0")
                        .stepType("HTTP_REQUEST")
                        .passed(true)
                        .weight(1)
                        .score(new BigDecimal("1.00"))
                        .build()))
                .build();
    }

    @Test
    void createResult_demotesPreviousLatestAndSavesSteps() {
        Fixture f = fixture();
        UUID planId = UUID.randomUUID();
        Result previous = Result.builder().latest(true).build();
        Mockito.when(f.results().findByStudentIdAndAssignmentIdAndPlanIdAndLatestTrue(
                        Mockito.any(), Mockito.any(), Mockito.eq(planId)))
                .thenReturn(List.of(previous));

        UUID id = f.service().createResult(request(planId));

        assertTrue(previous.getLatest() == null || !previous.getLatest());
        Mockito.verify(f.results()).saveAll(Mockito.eq(List.of(previous)));
        ArgumentCaptor<Result> saved = ArgumentCaptor.forClass(Result.class);
        Mockito.verify(f.results()).save(saved.capture());
        assertEquals(ResultStatus.DONE, saved.getValue().getStatus());
        assertEquals(new BigDecimal("10.00"), saved.getValue().getMaxScore());
        assertTrue(saved.getValue().getLatest());
        assertFalse(id == null);
        Mockito.verify(f.steps(), Mockito.times(1)).save(Mockito.any(StepResult.class));

        // null planId queries the IS NULL variant
        f.service().createResult(request(null));
        Mockito.verify(f.results()).findByStudentIdAndAssignmentIdAndPlanIdIsNullAndLatestTrue(
                Mockito.any(), Mockito.any());
    }

    @Test
    void createResult_missingIds_rejected() {
        Fixture f = fixture();
        CreateResultRequest bad = request(UUID.randomUUID());
        bad.setSubmissionId(null);
        assertThrows(IllegalArgumentException.class, () -> f.service().createResult(bad));
        Mockito.verify(f.results(), Mockito.never()).save(Mockito.any());
    }

    @Test
    void createResult_unknownStatus_rejected() {
        Fixture f = fixture();
        CreateResultRequest bad = request(UUID.randomUUID());
        bad.setStatus("NOPE");
        assertThrows(IllegalArgumentException.class, () -> f.service().createResult(bad));
    }
}
