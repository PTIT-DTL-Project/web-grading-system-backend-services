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

    @Test
    void averageBand10_averagesPercentages() {
        UUID student = UUID.randomUUID();
        ResultRepository repo = Mockito.mock(ResultRepository.class);
        ResultService service = new ResultService(repo);
        Mockito.when(repo.findByAssignmentIdInAndStudentIdAndLatestTrue(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of(
                        result(student, "8.00", "10.00"),
                        result(student, "5.00", "10.00")));

        assertEquals(new BigDecimal("6.50"), service.averageBand10(List.of(UUID.randomUUID(), UUID.randomUUID()), student));
    }

    @Test
    void averageBand10_nullWhenNoResults() {
        UUID student = UUID.randomUUID();
        ResultRepository repo = Mockito.mock(ResultRepository.class);
        Mockito.when(repo.findByAssignmentIdInAndStudentIdAndLatestTrue(Mockito.any(), Mockito.eq(student)))
                .thenReturn(List.of());
        assertNull(new ResultService(repo).averageBand10(List.of(UUID.randomUUID()), student));
    }

    private Result result(UUID student, String score, String maxScore) {
        return Result.builder()
                .studentId(student)
                .assignmentId(UUID.randomUUID())
                .score(new BigDecimal(score))
                .maxScore(new BigDecimal(maxScore))
                .status(ResultStatus.DONE)
                .build();
    }
}