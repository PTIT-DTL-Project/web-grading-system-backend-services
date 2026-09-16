package vn.edu.ptit.web_grading_system.result_service.service;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.result_service.entities.Result;
import vn.edu.ptit.web_grading_system.result_service.entities.ResultStatus;
import vn.edu.ptit.web_grading_system.result_service.repositories.ResultRepository;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ResultServiceTest {

    private ResultService service(ResultRepository repo) {
        return new ResultService(repo, Mockito.mock(
                vn.edu.ptit.web_grading_system.result_service.repositories.StepResultRepository.class));
    }

    @Test
    void weightedScoreByPlan_weightsByPlanWeight() {
        UUID student = UUID.randomUUID();
        ResultRepository repo = Mockito.mock(ResultRepository.class);
        Mockito.when(repo.findByAssignmentIdInAndStudentIdAndLatestTrue(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(
                        result(student, "8.00", "10.00", 1),
                        result(student, "5.00", "10.00", 3)));

        assertEquals(new BigDecimal("5.75"), service(repo)
                .weightedScoreByPlan(List.of(UUID.randomUUID(), UUID.randomUUID()), student));
    }

    @Test
    void weightedScoreByPlan_nullWhenNoResults() {
        UUID student = UUID.randomUUID();
        ResultRepository repo = Mockito.mock(ResultRepository.class);
        Mockito.when(repo.findByAssignmentIdInAndStudentIdAndLatestTrue(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of());
        assertNull(service(repo).weightedScoreByPlan(List.of(UUID.randomUUID()), student));
    }

    private Result result(UUID student, String score, String maxScore, int planWeight) {
        return Result.builder()
                .studentId(student)
                .assignmentId(UUID.randomUUID())
                .score(new BigDecimal(score))
                .maxScore(new BigDecimal(maxScore))
                .planWeight(planWeight)
                .status(ResultStatus.DONE)
                .build();
    }
}
