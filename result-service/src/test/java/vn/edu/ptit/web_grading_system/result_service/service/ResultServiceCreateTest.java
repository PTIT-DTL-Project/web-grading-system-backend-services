package vn.edu.ptit.web_grading_system.result_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.result_service.dto.request.CreateResultRequest;
import vn.edu.ptit.web_grading_system.result_service.entity.Result;
import vn.edu.ptit.web_grading_system.result_service.entity.ResultScope;
import vn.edu.ptit.web_grading_system.result_service.entity.ResultStatus;
import vn.edu.ptit.web_grading_system.result_service.entity.StepResult;
import vn.edu.ptit.web_grading_system.result_service.repository.ResultRepository;
import vn.edu.ptit.web_grading_system.result_service.repository.StepResultRepository;

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
        vn.edu.ptit.web_grading_system.result_service.client.CourseServicePlansClient plans =
                Mockito.mock(vn.edu.ptit.web_grading_system.result_service.client.CourseServicePlansClient.class);
        Mockito.when(results.save(Mockito.any())).thenAnswer(inv -> {
            Result r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        return new Fixture(new ResultService(results, steps, plans), results, steps);
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

        // A null planId is a FULL run: stamps FULL scope and demotes every
        // latest row of the assignment, not just the null-plan scope.
        UUID fullId = f.service().createResult(request(null));
        Mockito.verify(f.results()).findByStudentIdAndAssignmentIdAndLatestTrue(
                Mockito.any(), Mockito.any());
        saved = ArgumentCaptor.forClass(Result.class);
        Mockito.verify(f.results(), Mockito.times(2)).save(saved.capture());
        assertEquals(ResultScope.FULL, saved.getValue().getScope());
        assertFalse(fullId == null);
    }

    @Test
    void createResult_planRun_scopesPlanAndDemotesSamePlanOnly() {
        Fixture f = fixture();
        UUID planA = UUID.randomUUID();
        UUID id = f.service().createResult(request(planA));

        ArgumentCaptor<Result> saved = ArgumentCaptor.forClass(Result.class);
        Mockito.verify(f.results()).save(saved.capture());
        assertEquals(ResultScope.PLAN, saved.getValue().getScope());
        // Same-plan demotion only — the assignment-wide lookup is never used,
        // so a sibling plan's latest row survives.
        Mockito.verify(f.results(), Mockito.never()).findByStudentIdAndAssignmentIdAndLatestTrue(
                Mockito.any(), Mockito.any());
        assertFalse(id == null);
    }

    @Test
    void createResult_fullRun_demotesMixedScopes() {
        Fixture f = fixture();
        Result perPlan = Result.builder().latest(true).build();
        Result overall = Result.builder().latest(true).build();
        Mockito.when(f.results().findByStudentIdAndAssignmentIdAndLatestTrue(
                        Mockito.any(), Mockito.any()))
                .thenReturn(List.of(perPlan, overall));

        f.service().createResult(request(null));

        // The 2026-10-10 regression: a stale per-plan 0 survived next to the
        // new overall 10 and the average halved the score. Both go false.
        assertFalse(perPlan.getLatest());
        assertFalse(overall.getLatest());
        Mockito.verify(f.results()).saveAll(Mockito.eq(List.of(perPlan, overall)));
    }

    @Test
    void createResult_persistsSkippedFlag() {
        Fixture f = fixture();
        CreateResultRequest req = request(UUID.randomUUID());
        req.getStepResults().get(0).setSkipped(true);

        f.service().createResult(req);

        ArgumentCaptor<StepResult> savedStep = ArgumentCaptor.forClass(StepResult.class);
        Mockito.verify(f.steps()).save(savedStep.capture());
        assertTrue(savedStep.getValue().getSkipped());
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
