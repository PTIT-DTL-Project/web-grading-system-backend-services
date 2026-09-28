package vn.edu.ptit.web_grading_system.submission_service.controller;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingRequestHeaderException;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.ApiResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.PresignedUrlResponse;
import vn.edu.ptit.web_grading_system.submission_service.exception.GlobalExceptionHandler;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import org.mockito.Mockito;

class SubmissionControllerTest {

    private final SubmissionService service = Mockito.mock(SubmissionService.class);
    private final SubmissionController controller = new SubmissionController(service);

    @Test
    void requestUpload_withHeader_passesParsedStudentIdToService() throws Exception {
        var expected = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
        when(service.requestUpload(any(), eq(expected), eq("x.zip"), isNull()))
                .thenReturn(PresignedUrlResponse.builder().submissionId(UUID.randomUUID()).build());

        var resp = controller.requestUpload(UUID.randomUUID(), "x.zip", null, expected.toString());

        assertEquals(201, resp.getStatusCode().value());
        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(service).requestUpload(any(), captor.capture(), eq("x.zip"), isNull());
        assertEquals(expected, captor.getValue());
    }

    @Test
    void requestUpload_malformedHeader_throwsIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> controller.requestUpload(UUID.randomUUID(), "x.zip", null, "not-a-uuid"));
    }

    @Test
    void missingHeader_returns400_not500() throws Exception {
        Method handleUpload = SubmissionController.class.getDeclaredMethod(
                "requestUpload", UUID.class, String.class, UUID.class, String.class);
        var mp = new MethodParameter(handleUpload, 3);
        var ex = new MissingRequestHeaderException("X-User-Id", mp);

        var resp = new GlobalExceptionHandler().handleMissingHeader(ex);

        assertEquals(400, resp.getStatusCode().value());
        assertEquals("Missing required header: X-User-Id", resp.getBody().getMessage());
    }
}
