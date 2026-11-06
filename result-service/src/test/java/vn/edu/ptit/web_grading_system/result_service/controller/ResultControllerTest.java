package vn.edu.ptit.web_grading_system.result_service.controller;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.ResponseEntity;
import vn.edu.ptit.web_grading_system.result_service.dto.response.AssignmentResultGroupResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.StepResultResponse;
import vn.edu.ptit.web_grading_system.result_service.service.ResultService;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    @Test
    void getBySubmission_stripsSensitiveStepDetails_forStudent() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID submissionId = UUID.randomUUID();
        UUID studentId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        List<ResultResponse> payload = List.of(ResultResponse.builder()
                .submissionId(submissionId)
                .studentId(studentId)
                .score(new BigDecimal("7.50"))
                .steps(List.of(StepResultResponse.builder()
                        .id(UUID.randomUUID())
                        .stepId(UUID.randomUUID())
                        .stepOrder(1)
                        .stepName("GET /api/books")
                        .stepType("HTTP_REQUEST")
                        .planId(UUID.randomUUID())
                        .requestUrl("/api/v1/books/${id}")
                        .requestBody("{\"title\":\"X\"}")
                        .score(new BigDecimal("1.00"))

                        .passed(true)
                        .skipped(false)
                        .weight(1)
                        .assertionDetails("[{\"kind\":\"status\",\"equals\":201}]")
                        .expectedValue("{\"id\":\"abc\"}")
                        .actualValue("{\"id\":\"abc\"}")
                        .errorMessage("assertion failed")
                        .durationMs(100)
                        .responseStatus(201)
                        .build()))
                .build());
        Mockito.when(service.getBySubmissionId(submissionId)).thenReturn(payload);

        ResponseEntity<List<ResultResponse>> response =
                new ResultController(service).getBySubmission(submissionId, studentId.toString());

        assertEquals(200, response.getStatusCode().value());
        List<ResultResponse> body = response.getBody();
        assertNotNull(body);
        assertEquals(1, body.size());

        ResultResponse row = body.get(0);
        List<StepResultResponse> steps = row.getSteps();
        assertNotNull(steps);
        assertEquals(1, steps.size());

        StepResultResponse step = steps.get(0);
        // Safe fields preserved
        assertEquals("/api/v1/books/${id}", step.getRequestUrl());
        assertEquals("{\"title\":\"X\"}", step.getRequestBody());
        assertEquals(new BigDecimal("1.00"), step.getScore());

        assertTrue(step.getPassed());
        assertFalse(step.getSkipped());

        // Sensitive fields stripped
        assertNull(step.getAssertionDetails(), "assertionDetails must be stripped for student");
        assertNull(step.getExpectedValue(), "expectedValue must be stripped for student");
        assertNull(step.getActualValue(), "actualValue must be stripped for student");
        assertNull(step.getErrorMessage(), "errorMessage must be stripped for student");
    }

    @Test
    void getBySubmission_keepsFullPayload_forLecturer() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID submissionId = UUID.randomUUID();
        UUID studentId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        List<ResultResponse> payload = List.of(ResultResponse.builder()
                .submissionId(submissionId)
                .studentId(studentId)
                .score(new BigDecimal("7.50"))
                .steps(List.of(StepResultResponse.builder()
                        .id(UUID.randomUUID())
                        .stepId(UUID.randomUUID())
                        .stepOrder(1)
                        .stepName("GET /api/books")
                        .stepType("HTTP_REQUEST")
                        .planId(UUID.randomUUID())
                        .requestUrl("/api/v1/books/${id}")
                        .requestBody("{\"title\":\"X\"}")
                        .score(new BigDecimal("1.00"))

                        .passed(true)
                        .skipped(false)
                        .weight(1)
                        .assertionDetails("[{\"kind\":\"status\",\"equals\":201}]")
                        .expectedValue("{\"id\":\"abc\"}")
                        .actualValue("{\"id\":\"abc\"}")
                        .errorMessage("assertion failed")
                        .durationMs(100)
                        .responseStatus(201)
                        .build()))
                .build());
        Mockito.when(service.getBySubmissionId(submissionId)).thenReturn(payload);

        // Lecturer bypass: controller checks SecurityContext for exact ROLE_LECTURER authority
        // (matches HeaderAuthenticationFilter + @PreAuthorize behavior). Review: 2026-11-06, PR #83.
        var auth = new UsernamePasswordAuthenticationToken(
                "lecturer", null, List.of(new SimpleGrantedAuthority("ROLE_LECTURER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            ResponseEntity<List<ResultResponse>> response =
                    new ResultController(service).getBySubmission(submissionId, studentId.toString());

            assertEquals(200, response.getStatusCode().value());
            List<ResultResponse> body = response.getBody();
            assertNotNull(body);
            assertEquals(1, body.size());

            StepResultResponse step = body.get(0).getSteps().get(0);
            // Lecturer sees everything including sensitive fields
            assertEquals("/api/v1/books/${id}", step.getRequestUrl());
            assertEquals("[{\"kind\":\"status\",\"equals\":201}]", step.getAssertionDetails());
            assertEquals("{\"id\":\"abc\"}", step.getExpectedValue());
            assertEquals("{\"id\":\"abc\"}", step.getActualValue());
            assertEquals("assertion failed", step.getErrorMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void myAssignmentResults_stripsSensitiveStepDetails_forStudent() {
        ResultService service = Mockito.mock(ResultService.class);
        UUID assignmentId = UUID.randomUUID();
        UUID studentId = UUID.fromString("00000000-0000-0000-0000-000000000001");

        StepResultResponse step = StepResultResponse.builder()
                .id(UUID.randomUUID())
                .stepId(UUID.randomUUID())
                .stepOrder(1)
                .stepName("GET /api/books")
                .stepType("HTTP_REQUEST")
                .planId(UUID.randomUUID())
                .requestUrl("/api/v1/books/${id}")
                .requestBody("{\"title\":\"X\"}")
                .score(new BigDecimal("8.00"))
                .passed(true)
                .skipped(false)
                .weight(1)
                .assertionDetails("[{\"kind\":\"status\",\"equals\":201}]")
                .expectedValue("{\"id\":\"abc\"}")
                .actualValue("{\"id\":\"abc\"}")
                .errorMessage("assertion failed")
                .durationMs(100)
                .responseStatus(201)
                .build();

        ResultResponse result = ResultResponse.builder()
                .id(UUID.randomUUID())
                .submissionId(UUID.randomUUID())
                .assignmentId(assignmentId)
                .studentId(studentId)
                .planId(UUID.randomUUID())
                .score(new BigDecimal("8.00"))
                .maxScore(new BigDecimal("10.00"))
                .status("DONE")
                .steps(List.of(step))
                .build();

        AssignmentResultGroupResponse group = AssignmentResultGroupResponse.builder()
                .studentUserId(studentId)
                .exerciseScore(new BigDecimal("5.11"))
                .results(List.of(result))
                .build();

        Mockito.when(service.getByAssignment(assignmentId, studentId, true))
                .thenReturn(List.of(group));

        ResponseEntity<AssignmentResultGroupResponse> response =
                new ResultController(service).myAssignmentResults(assignmentId, studentId.toString());

        assertEquals(200, response.getStatusCode().value());
        AssignmentResultGroupResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(new BigDecimal("5.11"), body.getExerciseScore());
        assertEquals(1, body.getResults().size());

        List<StepResultResponse> resultSteps = body.getResults().get(0).getSteps();
        assertNotNull(resultSteps);
        assertEquals(1, resultSteps.size());

        StepResultResponse resultStep = resultSteps.get(0);
        assertEquals("/api/v1/books/${id}", resultStep.getRequestUrl());
        assertEquals("{\"title\":\"X\"}", resultStep.getRequestBody());
        assertEquals(new BigDecimal("8.00"), resultStep.getScore());

        assertTrue(resultStep.getPassed());

        assertNull(resultStep.getAssertionDetails(), "assertionDetails must be stripped for student");
        assertNull(resultStep.getExpectedValue(), "expectedValue must be stripped for student");
        assertNull(resultStep.getActualValue(), "actualValue must be stripped for student");
        assertNull(resultStep.getErrorMessage(), "errorMessage must be stripped for student");
    }
}
