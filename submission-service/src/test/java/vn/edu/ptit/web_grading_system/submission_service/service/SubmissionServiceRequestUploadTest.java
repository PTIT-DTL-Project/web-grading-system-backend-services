package vn.edu.ptit.web_grading_system.submission_service.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.submission_service.config.SubmissionProperties;
import vn.edu.ptit.web_grading_system.submission_service.entity.Submission;
import vn.edu.ptit.web_grading_system.submission_service.event.WgsEventsProducer;
import vn.edu.ptit.web_grading_system.submission_service.mapper.SubmissionMapper;
import vn.edu.ptit.web_grading_system.submission_service.repository.SubmissionRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SubmissionServiceRequestUploadTest {

    private record Fixture(SubmissionService service, SubmissionRepository repo,
                           WgsEventsProducer producer, EntityManager em,
                           RustFSService rustfs, SubmissionProperties props) {
    }

    private Fixture fixture() {
        SubmissionRepository repo = Mockito.mock(SubmissionRepository.class);
        WgsEventsProducer producer = Mockito.mock(WgsEventsProducer.class);
        EntityManager em = Mockito.mock(EntityManager.class);
        RustFSService rustfs = Mockito.mock(RustFSService.class);
        SubmissionProperties props = Mockito.mock(SubmissionProperties.class);
        SubmissionService service = new SubmissionService(repo,
                Mockito.mock(SubmissionMapper.class),
                rustfs, producer, em, props);
        return new Fixture(service, repo, producer, em, rustfs, props);
    }

    private void stubPresigned(Fixture f) {
        Mockito.when(f.rustfs.buildObjectName(any())).thenReturn("obj");
        Mockito.when(f.rustfs.generatePresignedUploadUrl("obj")).thenReturn("url");
        Mockito.when(f.props.presignedUrlExpiryMinutes()).thenReturn(10L);
    }

    @Test
    void requestUpload_multipleLatestRows_demotesAll() {
        Fixture f = fixture();
        UUID a = UUID.randomUUID(), s = UUID.randomUUID();
        Submission prev1 = Submission.builder().id(UUID.randomUUID()).latest(true).build();
        Submission prev2 = Submission.builder().id(UUID.randomUUID()).latest(true).build();
        Mockito.when(f.repo.findAllLatestByAssignmentAndStudent(a, s))
                .thenReturn(List.of(prev1, prev2));
        stubPresigned(f);

        f.service.requestUpload(a, s, "x.zip", null);

        assertFalse(prev1.getLatest(), "previous latest row must be demoted");
        assertFalse(prev2.getLatest(), "previous latest row must be demoted");
        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        Mockito.verify(f.em).persist(captor.capture());
        assertTrue(captor.getValue().getLatest(), "new row must be latest=true");
    }

    @Test
    void requestUpload_noExistingLatest_persistsNewRow() {
        Fixture f = fixture();
        UUID a = UUID.randomUUID(), s = UUID.randomUUID();
        Mockito.when(f.repo.findAllLatestByAssignmentAndStudent(a, s))
                .thenReturn(List.of());
        stubPresigned(f);

        f.service.requestUpload(a, s, "x.zip", null);

        ArgumentCaptor<Submission> captor = ArgumentCaptor.forClass(Submission.class);
        Mockito.verify(f.em).persist(captor.capture());
        assertTrue(captor.getValue().getLatest(), "new row must be latest=true");
    }
}
