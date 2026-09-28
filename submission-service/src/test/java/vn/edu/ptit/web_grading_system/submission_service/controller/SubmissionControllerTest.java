package vn.edu.ptit.web_grading_system.submission_service.controller;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.edu.ptit.web_grading_system.submission_service.config.SubmissionProperties;
import vn.edu.ptit.web_grading_system.submission_service.entity.Submission;
import vn.edu.ptit.web_grading_system.submission_service.event.WgsEventsProducer;
import vn.edu.ptit.web_grading_system.submission_service.exception.GlobalExceptionHandler;
import vn.edu.ptit.web_grading_system.submission_service.mapper.SubmissionMapper;
import vn.edu.ptit.web_grading_system.submission_service.repository.SubmissionRepository;
import vn.edu.ptit.web_grading_system.submission_service.service.RustFSService;
import vn.edu.ptit.web_grading_system.submission_service.service.SubmissionService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubmissionControllerTest {

    private final EntityManager entityManager = mock(EntityManager.class);
    private final SubmissionService service = new SubmissionService(mock(SubmissionRepository.class),
            mock(SubmissionMapper.class), mock(RustFSService.class),
            mock(WgsEventsProducer.class), entityManager,
            mock(SubmissionProperties.class));
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new SubmissionController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test
    void requestUpload_withCanonicalHeader_parsesAndPassesStudentId() throws Exception {
        var expected = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
        mvc.perform(post("/api/v1/submissions/presigned-url")
                        .param("assignmentId", UUID.randomUUID().toString())
                        .param("zipFileName", "x.zip")
                        .header("X-User-Id", expected.toString()))
                .andExpect(status().isCreated());
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        verify(entityManager).persist(captor.capture());
        assertEquals(expected, captor.getValue().getStudentId());
    }

    @Test
    void requestUpload_missingHeader_returns400ViaRealResolution() throws Exception {
        mvc.perform(post("/api/v1/submissions/presigned-url")
                        .param("assignmentId", UUID.randomUUID().toString())
                        .param("zipFileName", "x.zip"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Missing required header: X-User-Id"));
    }

    @Test
    void requestUpload_malformedHeader_returns400() throws Exception {
        mvc.perform(post("/api/v1/submissions/presigned-url")
                        .param("assignmentId", UUID.randomUUID().toString())
                        .param("zipFileName", "x.zip")
                        .header("X-User-Id", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requestUpload_shorthandHeader_returns400() throws Exception {
        // UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02") parses to
        // 2d93941a-4221-458b-a03d-043bd6315d02 - a phantom student. Non-canonical -> 400.
        mvc.perform(post("/api/v1/submissions/presigned-url")
                        .param("assignmentId", UUID.randomUUID().toString())
                        .param("zipFileName", "x.zip")
                        .header("X-User-Id", "2d93941a-4221-458b-a03d-43bd6315d02"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("X-User-Id must be a canonical UUID"));
    }
}
