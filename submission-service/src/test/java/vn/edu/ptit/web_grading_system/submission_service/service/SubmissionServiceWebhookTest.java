package vn.edu.ptit.web_grading_system.submission_service.service;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.submission_service.config.SubmissionProperties;
import vn.edu.ptit.web_grading_system.submission_service.entities.Submission;
import vn.edu.ptit.web_grading_system.submission_service.entities.SubmissionStatus;
import vn.edu.ptit.web_grading_system.submission_service.event.WgsEventsProducer;
import vn.edu.ptit.web_grading_system.submission_service.mapper.SubmissionMapper;
import vn.edu.ptit.web_grading_system.submission_service.repositories.SubmissionRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SubmissionServiceWebhookTest {

    private record Fixture(SubmissionService service, SubmissionRepository repo,
                           WgsEventsProducer producer) {
    }

    private Fixture fixture() {
        SubmissionRepository repo = Mockito.mock(SubmissionRepository.class);
        WgsEventsProducer producer = Mockito.mock(WgsEventsProducer.class);
        SubmissionService service = new SubmissionService(repo,
                Mockito.mock(SubmissionMapper.class),
                Mockito.mock(RustFSService.class),
                producer,
                Mockito.mock(EntityManager.class),
                Mockito.mock(SubmissionProperties.class));
        return new Fixture(service, repo, producer);
    }

    private Submission submission(SubmissionStatus status) {
        return Submission.builder()
                .id(UUID.randomUUID())
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .rustfsPath("submissions/x.zip")
                .status(status)
                .latest(true)
                .build();
    }

    @Test
    void webhookOnPending_publishesOnce() {
        Fixture f = fixture();
        Submission sub = submission(SubmissionStatus.PENDING);
        Mockito.when(f.repo().findByRustfsPath("submissions/x.zip"))
                .thenReturn(Optional.of(sub));

        f.service().handleUploadComplete("submissions/x.zip");

        assertEquals(SubmissionStatus.PENDING, sub.getStatus());
        Mockito.verify(f.repo()).save(sub);
        Mockito.verify(f.producer()).publishGradeSubmission(Mockito.argThat(
                p -> p.getSubmissionId().equals(sub.getId())));
    }

    @Test
    void lateWebhookOnDone_neitherWritesNorPublishes() {
        Fixture f = fixture();
        Submission sub = submission(SubmissionStatus.DONE);
        Mockito.when(f.repo().findByRustfsPath("submissions/x.zip"))
                .thenReturn(Optional.of(sub));

        f.service().handleUploadComplete("submissions/x.zip");

        assertEquals(SubmissionStatus.DONE, sub.getStatus());
        Mockito.verify(f.repo(), Mockito.never()).save(Mockito.any());
        Mockito.verify(f.producer(), Mockito.never()).publishGradeSubmission(Mockito.any());
    }

    @Test
    void webhookOnUnknownObject_ignored() {
        Fixture f = fixture();
        Mockito.when(f.repo().findByRustfsPath("submissions/nope.zip"))
                .thenReturn(Optional.empty());

        f.service().handleUploadComplete("submissions/nope.zip");

        Mockito.verify(f.repo(), Mockito.never()).save(Mockito.any());
        Mockito.verify(f.producer(), Mockito.never()).publishGradeSubmission(Mockito.any());
    }
}
