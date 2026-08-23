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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ResultInternalControllerTest {

    @Test
    void average_nullResult_returnsNullValue_withoutNpe() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID student = UUID.randomUUID();
        Mockito.when(service.averageBand10(Mockito.any(), Mockito.eq(student))).thenReturn(null);

        ResponseEntity<Map<String, BigDecimal>> response =
                new ResultInternalController(service).average(
                        new ResultInternalController.AverageRequest(List.of(UUID.randomUUID()), student));

        assertEquals(200, response.getStatusCode().value());
        assertNull(response.getBody().get("average")); // Map.of would have NPE'd here
    }
}