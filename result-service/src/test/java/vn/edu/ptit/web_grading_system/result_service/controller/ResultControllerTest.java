package vn.edu.ptit.web_grading_system.result_service.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultControllerTest {

    @Test
    void getBySubmission_returnsServicePayload() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID submissionId = UUID.randomUUID();
        List<ResultResponse> payload = List.of(ResultResponse.builder()
                .submissionId(submissionId)
                .score(new BigDecimal("7.50"))
                .build());
        Mockito.when(service.getBySubmissionId(submissionId)).thenReturn(payload);

        ResponseEntity<List<ResultResponse>> response =
                new ResultController(service).getBySubmission(submissionId);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(payload, response.getBody());
    }

    @Test
    void getBySubmission_ungraded_returnsEmptyList() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID submissionId = UUID.randomUUID();
        Mockito.when(service.getBySubmissionId(submissionId)).thenReturn(List.of());

        ResponseEntity<List<ResultResponse>> response =
                new ResultController(service).getBySubmission(submissionId);

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
    }
}
