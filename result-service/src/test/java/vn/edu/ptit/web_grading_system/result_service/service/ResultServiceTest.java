package vn.edu.ptit.web_grading_system.result_service.service;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.result_service.client.CourseServicePlansClient;
import vn.edu.ptit.web_grading_system.result_service.entity.Result;
import vn.edu.ptit.web_grading_system.result_service.entity.ResultScope;
import vn.edu.ptit.web_grading_system.result_service.entity.ResultStatus;
import vn.edu.ptit.web_grading_system.result_service.repository.ResultRepository;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultServiceTest {

    private ResultService service(ResultRepository repo) {
        CourseServicePlansClient plans = Mockito.mock(CourseServicePlansClient.class);
        Mockito.when(plans.plans(Mockito.any())).thenReturn(List.of());
        return new ResultService(repo, Mockito.mock(
                vn.edu.ptit.web_grading_system.result_service.repository.StepResultRepository.class),
                plans);
    }

    @Test
    void weightedScoreByPlan_weightsByPlanWeight() {
        UUID student = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        UUID planA = UUID.randomUUID();
        UUID planB = UUID.randomUUID();
        ResultRepository repo = Mockito.mock(ResultRepository.class);
        Mockito.when(repo.findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(
                        result(student, assignment, planA, "8.00", "10.00", 1),
                        result(student, assignment, planB, "5.00", "10.00", 3)));

        // (8×1 + 5×3) / (1+3) = 23/4 = 5.75 — carried weights (no course plans stubbed).
        assertEquals(new BigDecimal("5.75"), service(repo)
                .weightedScoreByPlan(List.of(assignment), student));
    }

    @Test
    void weightedScoreByPlan_laterZeroKeepsEarlierTen() {
        UUID student = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        ResultRepository repo = Mockito.mock(ResultRepository.class);
        Mockito.when(repo.findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(
                        result(student, assignment, plan, "10.00", "10.00", 1),
                        result(student, assignment, plan, "0.00", "10.00", 1)));

        // Max-per-plan: the resubmitted broken zip must not nuke the 10.
        assertEquals(new BigDecimal("10.00"), service(repo)
                .weightedScoreByPlan(List.of(assignment), student));
    }

    @Test
    void weightedScoreByPlan_nullWhenNoResults() {
        UUID student = UUID.randomUUID();
        ResultRepository repo = Mockito.mock(ResultRepository.class);
        Mockito.when(repo.findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of());
        assertNull(service(repo).weightedScoreByPlan(List.of(UUID.randomUUID()), student));
    }

    private Result failedPlanRow(UUID student, UUID assignment, UUID plan) {
        return Result.builder()
                .studentId(student)
                .assignmentId(assignment)
                .planId(plan)
                .scope(ResultScope.PLAN)
                .score(BigDecimal.ZERO.setScale(2))
                .maxScore(new BigDecimal("10.00"))
                .planWeight(1)
                .status(ResultStatus.FAILED)
                .build();
    }

    @Test
    void weightedScoreByPlan_failedAttemptDoesNotBeatBest() {
        ScoringFixture f = scoringFixture();
        UUID student = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        Mockito.when(f.repo().findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(
                        result(student, assignment, plan, "10.00", "10.00", 1),
                        failedPlanRow(student, assignment, plan)));

        // The failed attempt's stored 0 is a candidate but max keeps the 10.
        assertEquals(new BigDecimal("10.00"),
                f.service().weightedScoreByPlan(List.of(assignment), student));
    }

    @Test
    void weightedScoreByPlan_failedOnlyPlanRowScoresZero() {
        ScoringFixture f = scoringFixture();
        UUID student = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        Mockito.when(f.repo().findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(failedPlanRow(student, assignment, UUID.randomUUID())));

        // Today's transcript behavior preserved: a failed attempt still reads 0.00.
        assertEquals(BigDecimal.ZERO.setScale(2),
                f.service().weightedScoreByPlan(List.of(assignment), student));
    }

    @Test
    void weightedScoreByPlan_steplessFullRowContributesNothing() {
        ScoringFixture f = scoringFixture();
        UUID student = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        Result failedFull = Result.builder()
                .studentId(student)
                .assignmentId(assignment)
                .scope(ResultScope.FULL)
                .score(BigDecimal.ZERO.setScale(2))
                .maxScore(new BigDecimal("10.00"))
                .planWeight(1)
                .status(ResultStatus.FAILED)
                .build();
        failedFull.setId(UUID.randomUUID());
        Mockito.when(f.repo().findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(failedFull));

        // Executor's fail path posts no step items, so there is nothing to
        // decompose — reads missing, not 0.00 (acked 2026-10-10).
        assertNull(f.service().weightedScoreByPlan(List.of(assignment), student));
    }

    private record ScoringFixture(ResultService service, ResultRepository repo,
            vn.edu.ptit.web_grading_system.result_service.repository.StepResultRepository steps,
            CourseServicePlansClient plans) {
    }

    private ScoringFixture scoringFixture() {
        ResultRepository repo = Mockito.mock(ResultRepository.class);
        vn.edu.ptit.web_grading_system.result_service.repository.StepResultRepository steps =
                Mockito.mock(vn.edu.ptit.web_grading_system.result_service.repository.StepResultRepository.class);
        CourseServicePlansClient plans = Mockito.mock(CourseServicePlansClient.class);
        Mockito.when(plans.plans(Mockito.any())).thenReturn(List.of());
        return new ScoringFixture(new ResultService(repo, steps, plans), repo, steps, plans);
    }

    private static vn.edu.ptit.web_grading_system.result_service.entity.StepResult step(
            UUID resultId, UUID planId, boolean passed, boolean skipped, int weight) {
        vn.edu.ptit.web_grading_system.result_service.entity.StepResult s =
                vn.edu.ptit.web_grading_system.result_service.entity.StepResult.builder()
                        .resultId(resultId)
                        .planId(planId)
                        .stepOrder(0)
                        .stepName("s")
                        .stepType(vn.edu.ptit.web_grading_system.result_service.entity.StepType.HTTP_REQUEST)
                        .passed(passed)
                        .skipped(skipped)
                        .weight(weight)
                        .score(passed ? new BigDecimal(weight).setScale(2) : BigDecimal.ZERO.setScale(2))
                        .build();
        s.setId(UUID.randomUUID());
        return s;
    }

    @Test
    void weightedScoreByPlan_fullRunDecomposesPerPlan() {
        ScoringFixture f = scoringFixture();
        UUID student = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        UUID planA = UUID.randomUUID();
        UUID planB = UUID.randomUUID();
        Result full = Result.builder()
                .studentId(student)
                .assignmentId(assignment)
                .scope(ResultScope.FULL)
                .score(new BigDecimal("7.50"))
                .maxScore(new BigDecimal("10.00"))
                .planWeight(1)
                .status(ResultStatus.DONE)
                .build();
        full.setId(UUID.randomUUID());
        Mockito.when(f.repo().findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(full));
        Mockito.when(f.steps().findByResultIdIn(Mockito.any())).thenReturn(List.of(
                step(full.getId(), planA, true, false, 1),
                step(full.getId(), planA, true, false, 1),
                step(full.getId(), planB, true, false, 1),
                step(full.getId(), planB, false, false, 1)));
        Mockito.when(f.plans().plans(assignment)).thenReturn(List.of(
                new CourseServicePlansClient.PlanWeight(planA, 2),
                new CourseServicePlansClient.PlanWeight(planB, 1)));

        // Plan A 10.00, plan B 5.00, course weights 2:1 → (20+5)/3 = 8.33.
        // The row's own 7.50 never enters the average.
        assertEquals(new BigDecimal("8.33"),
                f.service().weightedScoreByPlan(List.of(assignment), student));
    }

    @Test
    void weightedScoreByPlan_skippedExcludedExactly() {
        ScoringFixture f = scoringFixture();
        UUID student = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        UUID plan = UUID.randomUUID();
        Result full = Result.builder()
                .studentId(student)
                .assignmentId(assignment)
                .scope(ResultScope.FULL)
                .score(BigDecimal.ZERO.setScale(2))
                .maxScore(new BigDecimal("10.00"))
                .planWeight(1)
                .status(ResultStatus.DONE)
                .build();
        full.setId(UUID.randomUUID());
        Mockito.when(f.repo().findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(full));
        Mockito.when(f.steps().findByResultIdIn(Mockito.any())).thenReturn(List.of(
                step(full.getId(), plan, true, false, 1),
                step(full.getId(), plan, false, false, 1),
                step(full.getId(), plan, false, true, 2)));

        // 1 pass + 1 fail over ran weight 2 (skip's weight 2 excluded) = 5.00,
        // never 10×1/4 = 2.50.
        assertEquals(new BigDecimal("5.00"),
                f.service().weightedScoreByPlan(List.of(assignment), student));
    }

    @Test
    void weightedScoreByPlan_courseWeightsWinOverCarried() {
        ScoringFixture f = scoringFixture();
        UUID student = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        UUID planA = UUID.randomUUID();
        UUID planB = UUID.randomUUID();
        ResultRepository repo = f.repo();
        Mockito.when(repo.findByAssignmentIdInAndStudentId(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(
                        Result.builder().studentId(student).assignmentId(assignment)
                                .planId(planA).scope(ResultScope.PLAN)
                                .score(new BigDecimal("8.00")).maxScore(new BigDecimal("10.00"))
                                .planWeight(1).status(ResultStatus.DONE).build(),
                        Result.builder().studentId(student).assignmentId(assignment)
                                .planId(planB).scope(ResultScope.PLAN)
                                .score(new BigDecimal("6.00")).maxScore(new BigDecimal("10.00"))
                                .planWeight(3).status(ResultStatus.DONE).build()));
        Mockito.when(f.plans().plans(assignment)).thenReturn(List.of(
                new CourseServicePlansClient.PlanWeight(planA, 3),
                new CourseServicePlansClient.PlanWeight(planB, 1)));

        // Course weights 3:1 win over carried 1:3 → (24+6)/4 = 7.50.
        assertEquals(new BigDecimal("7.50"),
                f.service().weightedScoreByPlan(List.of(assignment), student));
    }

    private Result result(UUID student, UUID assignment, UUID plan, String score, String maxScore,
            int planWeight) {
        return Result.builder()
                .studentId(student)
                .assignmentId(assignment)
                .planId(plan)
                .scope(ResultScope.PLAN)
                .score(new BigDecimal(score))
                .maxScore(new BigDecimal(maxScore))
                .planWeight(planWeight)
                .status(ResultStatus.DONE)
                .build();
    }
}
