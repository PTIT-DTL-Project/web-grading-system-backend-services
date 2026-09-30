package vn.edu.ptit.web_grading_system.result_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.result_service.dto.response.AssignmentResultGroupResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.entity.Result;
import vn.edu.ptit.web_grading_system.result_service.entity.ResultStatus;
import vn.edu.ptit.web_grading_system.result_service.entity.StepResult;
import vn.edu.ptit.web_grading_system.result_service.entity.StepType;
import vn.edu.ptit.web_grading_system.result_service.repository.ResultRepository;
import vn.edu.ptit.web_grading_system.result_service.repository.StepResultRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultServiceReadTest {

    private ResultService service(ResultRepository results, StepResultRepository steps) {
        return new ResultService(results, steps);
    }

    @Test
    void getBySubmissionId_returnsResultsWithSteps() {
        UUID submissionId = UUID.randomUUID();
        UUID resultId = UUID.randomUUID();
        Result result = Result.builder()
                .submissionId(submissionId)
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .score(new BigDecimal("7.50"))
                .maxScore(new BigDecimal("10.00"))
                .status(ResultStatus.DONE)
                .summaryLog("Passed 3/4 steps")
                .latest(true)
                .build();
        result.setId(resultId);
        StepResult step = StepResult.builder()
                .resultId(resultId)
                .stepOrder(0)
                .stepName("create book")
                .stepType(StepType.HTTP_REQUEST)
                .passed(true)
                .build();
        step.setId(UUID.randomUUID());
        ResultRepository results = Mockito.mock(ResultRepository.class);
        StepResultRepository steps = Mockito.mock(StepResultRepository.class);
        Mockito.when(results.findBySubmissionId(submissionId)).thenReturn(List.of(result));
        Mockito.when(steps.findByResultId(resultId)).thenReturn(List.of(step));

        List<ResultResponse> responses = service(results, steps).getBySubmissionId(submissionId);

        assertEquals(1, responses.size());
        ResultResponse response = responses.get(0);
        assertEquals(resultId, response.getId());
        assertEquals("DONE", response.getStatus());
        assertEquals(new BigDecimal("7.50"), response.getScore());
        assertEquals(1, response.getSteps().size());
        assertEquals("create book", response.getSteps().get(0).getStepName());
        assertEquals("HTTP_REQUEST", response.getSteps().get(0).getStepType());
    }

    @Test
    void getBySubmissionId_ungraded_returnsEmpty() {
        ResultRepository results = Mockito.mock(ResultRepository.class);
        StepResultRepository steps = Mockito.mock(StepResultRepository.class);
        UUID submissionId = UUID.randomUUID();
        Mockito.when(results.findBySubmissionId(submissionId)).thenReturn(List.of());

        List<ResultResponse> responses =
                service(results, steps).getBySubmissionId(submissionId);

        assertTrue(responses.isEmpty());
        Mockito.verify(steps, Mockito.never()).findByResultId(Mockito.any());
    }

    // --- getByAssignment (course-service's lecturer grading view) -------------

    /** One latest result row of an assignment; caller assigns the id so steps can key off it. */
    private static Result planRow(UUID resultId, UUID studentId, int planWeight, String score) {
        Result result = Result.builder()
                .submissionId(UUID.randomUUID())
                .assignmentId(ASSIGNMENT)
                .studentId(studentId)
                .planId(UUID.randomUUID())
                .planWeight(planWeight)
                .score(new BigDecimal(score))
                .maxScore(new BigDecimal("10.00"))
                .status(ResultStatus.DONE)
                .latest(true)
                .build();
        result.setId(resultId);
        return result;
    }

    private static final UUID ASSIGNMENT = UUID.fromString("11111111-0000-4000-8000-0000000000a1");
    private static final UUID STUDENT_A = UUID.fromString("11111111-0000-4000-8000-0000000000a2");
    private static final UUID STUDENT_B = UUID.fromString("11111111-0000-4000-8000-0000000000a3");

    @Test
    void getByAssignment_groupsEachStudentAndAppliesTheSharedWeightedFormula() {
        ResultRepository results = Mockito.mock(ResultRepository.class);
        StepResultRepository steps = Mockito.mock(StepResultRepository.class);
        Mockito.when(results.findByAssignmentIdAndLatestTrue(ASSIGNMENT)).thenReturn(List.of(
                planRow(UUID.randomUUID(), STUDENT_A, 1, "8.00"),
                planRow(UUID.randomUUID(), STUDENT_A, 3, "6.00"),
                planRow(UUID.randomUUID(), STUDENT_B, 1, "10.00")));

        List<AssignmentResultGroupResponse> groups =
                service(results, steps).getByAssignment(ASSIGNMENT, null, false);

        assertEquals(2, groups.size());
        AssignmentResultGroupResponse groupA = groups.get(0);
        assertEquals(STUDENT_A, groupA.getStudentUserId());
        assertEquals(2, groupA.getResults().size());
        // (8/10×10 × 1 + 6/10×10 × 3) / (1 + 3) = 26 / 4 = 6.50 — the same formula
        // weightedScoreByPlan uses, so this view and the transcript cannot drift apart.
        assertEquals(new BigDecimal("6.50"), groupA.getExerciseScore());

        AssignmentResultGroupResponse groupB = groups.get(1);
        assertEquals(STUDENT_B, groupB.getStudentUserId());
        assertEquals(new BigDecimal("10.00"), groupB.getExerciseScore());
        assertEquals(1, groupB.getResults().size());
    }

    @Test
    void getByAssignment_filteredToOneStudentReturnsOnlyThatStudentsRows() {
        ResultRepository results = Mockito.mock(ResultRepository.class);
        StepResultRepository steps = Mockito.mock(StepResultRepository.class);
        Mockito.when(results.findByAssignmentIdAndLatestTrue(ASSIGNMENT)).thenReturn(List.of(
                planRow(UUID.randomUUID(), STUDENT_A, 1, "8.00"),
                planRow(UUID.randomUUID(), STUDENT_B, 1, "10.00")));

        List<AssignmentResultGroupResponse> groups =
                service(results, steps).getByAssignment(ASSIGNMENT, STUDENT_B, false);

        assertEquals(1, groups.size());
        assertEquals(STUDENT_B, groups.get(0).getStudentUserId());
    }

    @Test
    void getByAssignment_withoutStepsLoadsNoStepRows() {
        ResultRepository results = Mockito.mock(ResultRepository.class);
        StepResultRepository steps = Mockito.mock(StepResultRepository.class);
        Mockito.when(results.findByAssignmentIdAndLatestTrue(ASSIGNMENT))
                .thenReturn(List.of(planRow(UUID.randomUUID(), STUDENT_A, 1, "8.00")));

        List<AssignmentResultGroupResponse> groups =
                service(results, steps).getByAssignment(ASSIGNMENT, null, false);

        // The class-wide list stays on one query; N students × M plans would otherwise be
        // N×M round trips. Null, not empty, so the caller can tell "not loaded" from "none".
        Mockito.verify(steps, Mockito.never()).findByResultIdIn(Mockito.any());
        assertNull(groups.get(0).getResults().get(0).getSteps());
    }

    @Test
    void getByAssignment_withStepsBatchesThemIntoOneQuery() {
        UUID resultId = UUID.randomUUID();
        Result row = planRow(resultId, STUDENT_A, 1, "8.00");
        ResultRepository results = Mockito.mock(ResultRepository.class);
        StepResultRepository steps = Mockito.mock(StepResultRepository.class);
        Mockito.when(results.findByAssignmentIdAndLatestTrue(ASSIGNMENT)).thenReturn(List.of(row));
        StepResult step = StepResult.builder()
                .resultId(resultId)
                .stepOrder(0)
                .stepName("create book")
                .stepType(StepType.HTTP_REQUEST)
                .passed(true)
                .build();
        step.setId(UUID.randomUUID());
        Mockito.when(steps.findByResultIdIn(List.of(resultId))).thenReturn(List.of(step));

        List<AssignmentResultGroupResponse> groups =
                service(results, steps).getByAssignment(ASSIGNMENT, null, true);

        assertEquals(1, groups.get(0).getResults().get(0).getSteps().size());
        assertEquals("create book", groups.get(0).getResults().get(0).getSteps().get(0).getStepName());
        Mockito.verify(steps, Mockito.never()).findByResultId(Mockito.any());
    }

    @Test
    void getByAssignment_ungradedAssignmentReturnsEmptyWithoutAnyQuery() {
        ResultRepository results = Mockito.mock(ResultRepository.class);
        StepResultRepository steps = Mockito.mock(StepResultRepository.class);
        Mockito.when(results.findByAssignmentIdAndLatestTrue(ASSIGNMENT)).thenReturn(List.of());

        assertTrue(service(results, steps).getByAssignment(ASSIGNMENT, null, true).isEmpty());

        Mockito.verify(steps, Mockito.never()).findByResultIdIn(Mockito.any());
    }
}
