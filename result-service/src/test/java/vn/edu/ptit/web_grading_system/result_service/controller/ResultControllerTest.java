package vn.edu.ptit.web_grading_system.result_service.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import vn.edu.ptit.web_grading_system.result_service.dto.response.AssignmentResultGroupResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultControllerTest {

    @Test
    void getBySubmission_returnsServicePayload() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID submissionId = UUID.randomUUID();
        UUID studentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<ResultResponse> payload = List.of(ResultResponse.builder()
                .submissionId(submissionId)
                .studentId(studentId)
                .score(new BigDecimal("7.50"))
                .build());
        Mockito.when(service.getBySubmissionId(submissionId)).thenReturn(payload);

        ResponseEntity<List<ResultResponse>> response =
                new ResultController(service).getBySubmission(submissionId, studentId.toString());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(payload, response.getBody());
    }

    @Test
    void getBySubmission_ungraded_returnsEmptyList() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID submissionId = UUID.randomUUID();
        Mockito.when(service.getBySubmissionId(submissionId)).thenReturn(List.of());

        ResponseEntity<List<ResultResponse>> response =
                new ResultController(service).getBySubmission(submissionId,
                        "00000000-0000-0000-0000-000000000001");

        assertEquals(200, response.getStatusCode().value());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void getBySubmission_mismatchedXUserId_returnsForbidden() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID submissionId = UUID.randomUUID();
        UUID studentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        List<ResultResponse> payload = List.of(ResultResponse.builder()
                .submissionId(submissionId)
                .studentId(studentId)
                .score(new BigDecimal("7.50"))
                .build());
        Mockito.when(service.getBySubmissionId(submissionId)).thenReturn(payload);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                new ResultController(service).getBySubmission(submissionId,
                        "00000000-0000-0000-0000-000000000002"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void myAssignmentResults_noSubmission_returnsZeroScoreAndEmptyRows() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID assignmentId = UUID.randomUUID();
        UUID studentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Mockito.when(service.getByAssignment(assignmentId, studentId, true))
                .thenReturn(List.of());

        ResponseEntity<AssignmentResultGroupResponse> response =
                new ResultController(service).myAssignmentResults(assignmentId, studentId.toString());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(studentId, response.getBody().getStudentUserId());
        assertEquals(BigDecimal.ZERO, response.getBody().getExerciseScore());
        assertTrue(response.getBody().getResults().isEmpty());
    }

    @Test
    void myAssignmentResults_withResults_returnsTheSingleGroup() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID assignmentId = UUID.randomUUID();
        UUID studentId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        AssignmentResultGroupResponse group = AssignmentResultGroupResponse.builder()
                .studentUserId(studentId)
                .exerciseScore(new BigDecimal("5.11"))
                .results(List.of(ResultResponse.builder()
                        .score(new BigDecimal("8.00"))
                        .maxScore(new BigDecimal("10.00"))
                        .build()))
                .build();
        Mockito.when(service.getByAssignment(assignmentId, studentId, true))
                .thenReturn(List.of(group));

        ResponseEntity<AssignmentResultGroupResponse> response =
                new ResultController(service).myAssignmentResults(assignmentId, studentId.toString());

        assertEquals(200, response.getStatusCode().value());
        assertEquals(new BigDecimal("5.11"), response.getBody().getExerciseScore());
        assertEquals(1, response.getBody().getResults().size());
    }

    @Test
    void myAssignmentResults_missingIdentity_returnsForbidden() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID assignmentId = UUID.randomUUID();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                new ResultController(service).myAssignmentResults(assignmentId, null));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
