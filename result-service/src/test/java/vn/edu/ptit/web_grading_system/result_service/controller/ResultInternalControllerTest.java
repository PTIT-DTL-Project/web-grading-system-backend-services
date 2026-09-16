package vn.edu.ptit.web_grading_system.result_service.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResultInternalControllerTest {

    @Test
    void weighted_nullResult_returnsNullValue_withoutNpe() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID student = UUID.randomUUID();
        Mockito.when(service.weightedScoreByPlan(Mockito.any(), Mockito.eq(student))).thenReturn(null);

        ResponseEntity<Map<String, BigDecimal>> response =
                new ResultInternalController(service).weighted(
                        new ResultInternalController.AverageRequest(List.of(UUID.randomUUID()), student));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(null, response.getBody().get("average")); // Map.of would have NPE'd here
    }

    @Test
    void create_returns201WithId() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID id = UUID.randomUUID();
        Mockito.when(service.createResult(Mockito.any())).thenReturn(id);

        vn.edu.ptit.web_grading_system.result_service.dto.request.CreateResultRequest request =
                vn.edu.ptit.web_grading_system.result_service.dto.request.CreateResultRequest.builder()
                        .submissionId(UUID.randomUUID())
                        .assignmentId(UUID.randomUUID())
                        .studentId(UUID.randomUUID())
                        .score(BigDecimal.TEN)
                        .status("DONE")
                        .build();

        ResponseEntity<Map<String, UUID>> response = new ResultInternalController(service).create(request);

        assertEquals(201, response.getStatusCode().value());
        assertEquals(id, response.getBody().get("id"));
    }
}