package vn.edu.ptit.web_grading_system.result_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.entities.Result;
import vn.edu.ptit.web_grading_system.result_service.entities.ResultStatus;
import vn.edu.ptit.web_grading_system.result_service.entities.StepResult;
import vn.edu.ptit.web_grading_system.result_service.entities.StepType;
import vn.edu.ptit.web_grading_system.result_service.repositories.ResultRepository;
import vn.edu.ptit.web_grading_system.result_service.repositories.StepResultRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
