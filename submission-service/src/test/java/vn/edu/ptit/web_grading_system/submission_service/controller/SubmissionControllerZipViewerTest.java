package vn.edu.ptit.web_grading_system.submission_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipDownloadResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipFileResponse;
import vn.edu.ptit.web_grading_system.submission_service.dto.response.SubmissionZipTreeResponse;
import vn.edu.ptit.web_grading_system.submission_service.exception.GlobalExceptionHandler;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for zip viewer controller endpoints:
 * GET /api/v1/submissions/{submissionId}/tree
 * GET /api/v1/submissions/{submissionId}/file
 * GET /api/v1/submissions/{submissionId}/download
 */
class SubmissionControllerZipViewerTest {

    private static final UUID SUBMISSION_ID = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID CALLER_ID = UUID.fromString("3e93941a-4221-458b-a03d-43bd6315d02e");

    private final SubmissionService service = mock(SubmissionService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SubmissionController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void getTree_canonicalHeader_returns200() throws Exception {
        SubmissionZipTreeResponse tree = SubmissionZipTreeResponse.builder()
                .submissionId(SUBMISSION_ID)
                .build();
        when(service.getSubmissionTree(eq(SUBMISSION_ID), eq(CALLER_ID))).thenReturn(tree);

        mvc.perform(get("/api/v1/submissions/{submissionId}/tree", SUBMISSION_ID)
                        .header("X-User-Id", CALLER_ID.toString()))
                .andExpect(status().isOk());

        verify(service).getSubmissionTree(eq(SUBMISSION_ID), eq(CALLER_ID));
    }

    @Test
    void getTree_missingHeader_returns400() throws Exception {
        mvc.perform(get("/api/v1/submissions/{submissionId}/tree", SUBMISSION_ID))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required header: X-User-Id"));
    }

    @Test
    void getTree_malformedHeader_returns400() throws Exception {
        mvc.perform(get("/api/v1/submissions/{submissionId}/tree", SUBMISSION_ID)
                        .header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getTree_shorthandHeader_returns400() throws Exception {
        mvc.perform(get("/api/v1/submissions/{submissionId}/tree", SUBMISSION_ID)
                        .header("X-User-Id", "2d93941a-4221-458b-a03d-43bd6315d02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("X-User-Id must be a canonical UUID"));
    }

    @Test
    void getTree_serviceThrowsNotFound_returns404() throws Exception {
        when(service.getSubmissionTree(eq(SUBMISSION_ID), eq(CALLER_ID)))
                .thenThrow(new vn.edu.ptit.web_grading_system.submission_service.exception.ResourceNotFoundException(
                        "Submission not found: " + SUBMISSION_ID));

        mvc.perform(get("/api/v1/submissions/{submissionId}/tree", SUBMISSION_ID)
                        .header("X-User-Id", CALLER_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getFile_canonicalHeader_returns200() throws Exception {
        SubmissionZipFileResponse file = SubmissionZipFileResponse.builder()
                .submissionId(SUBMISSION_ID)
                .path("src/Main.java")
                .content("public class Main {}")
                .build();
        when(service.getZipFileContent(eq(SUBMISSION_ID), anyString(), eq(CALLER_ID))).thenReturn(file);

        mvc.perform(get("/api/v1/submissions/{submissionId}/file", SUBMISSION_ID)
                        .param("path", "src/Main.java")
                        .header("X-User-Id", CALLER_ID.toString()))
                .andExpect(status().isOk());

        verify(service).getZipFileContent(eq(SUBMISSION_ID), eq("src/Main.java"), eq(CALLER_ID));
    }

    @Test
    void getFile_missingPathParam_returns400() throws Exception {
        mvc.perform(get("/api/v1/submissions/{submissionId}/file", SUBMISSION_ID)
                        .header("X-User-Id", CALLER_ID.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFile_serviceThrowsNotFound_returns404() throws Exception {
        when(service.getZipFileContent(eq(SUBMISSION_ID), anyString(), eq(CALLER_ID)))
                .thenThrow(new vn.edu.ptit.web_grading_system.submission_service.exception.ResourceNotFoundException(
                        "File not found in zip: src/Main.java"));

        mvc.perform(get("/api/v1/submissions/{submissionId}/file", SUBMISSION_ID)
                        .param("path", "src/Main.java")
                        .header("X-User-Id", CALLER_ID.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDownload_canonicalHeader_returns200() throws Exception {
        SubmissionZipDownloadResponse resp = SubmissionZipDownloadResponse.builder()
                .submissionId(SUBMISSION_ID)
                .downloadUrl("https://rustfs.example.com/presigned")
                .expiresInMinutes(15L)
                .build();
        when(service.getSubmissionDownloadUrl(eq(SUBMISSION_ID), eq(CALLER_ID))).thenReturn(resp);

        mvc.perform(get("/api/v1/submissions/{submissionId}/download", SUBMISSION_ID)
                        .header("X-User-Id", CALLER_ID.toString()))
                .andExpect(status().isOk());

        verify(service).getSubmissionDownloadUrl(eq(SUBMISSION_ID), eq(CALLER_ID));
    }

    @Test
    void getDownload_serviceThrowsNotFound_returns404() throws Exception {
        when(service.getSubmissionDownloadUrl(eq(SUBMISSION_ID), eq(CALLER_ID)))
                .thenThrow(new vn.edu.ptit.web_grading_system.submission_service.exception.ResourceNotFoundException(
                        "Submission not found: " + SUBMISSION_ID));

        mvc.perform(get("/api/v1/submissions/{submissionId}/download", SUBMISSION_ID)
                        .header("X-User-Id", CALLER_ID.toString()))
                .andExpect(status().isNotFound());
    }
}
