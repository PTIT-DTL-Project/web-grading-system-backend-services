package vn.edu.ptit.web_grading_system.course_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import vn.edu.ptit.web_grading_system.course_service.Constant;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateDockerImageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateDockerImageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.DockerImageResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.AssignmentDockerImage;
import vn.edu.ptit.web_grading_system.course_service.entity.DockerImage;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.exception.ConflictException;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.repository.AssignmentDockerImageRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.DockerImageRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DockerImageServiceTest {

    private static final UUID OWNER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID OTHER = UUID.fromString("be7b2fdc-4e51-4f3a-9b2c-123456789abc");
    private static final String DIGEST =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Mock private DockerImageRepository dockerImageRepository;
    @Mock private AssignmentDockerImageRepository assignmentDockerImageRepository;
    @InjectMocks private DockerImageService service;

    private DockerImage activeImage(UUID id) {
        return DockerImage.builder().id(id).name("app").imageUrl("app:1").ownerId(OWNER).build();
    }

    // ---- create ----

    @Test
    void create_persistsShortImageUrlWithoutThrowing() {
        // Review: 2026-09-27, Pullfrog PR #20 — regression guard for the removed
        // substring(0, 500) which threw StringIndexOutOfBoundsException on short URLs.
        CreateDockerImageRequest req = new CreateDockerImageRequest();
        req.setName("pg");
        req.setImageUrl("postgres:16");

        when(dockerImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DockerImageResponse res = service.create(OWNER, req);
        assertThat(res.getImageUrl()).isEqualTo("postgres:16");
    }

    @Test
    void create_acceptsDigest() {
        // Review: 2026-09-27, Pullfrog PR #20 — digest pinning must be accepted
        // (the regex rewrite left this branch unreachable before this fix).
        CreateDockerImageRequest req = new CreateDockerImageRequest();
        req.setName("alpine");
        req.setImageUrl("alpine@sha256:" + DIGEST);

        when(dockerImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DockerImageResponse res = service.create(OWNER, req);
        assertThat(res.getImageUrl()).isEqualTo("alpine@sha256:" + DIGEST);
    }

    @Test
    void create_acceptsRegistryWithPort() {
        CreateDockerImageRequest req = new CreateDockerImageRequest();
        req.setName("app");
        req.setImageUrl("registry.example.com:5000/team/app:v1.0");

        when(dockerImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DockerImageResponse res = service.create(OWNER, req);
        assertThat(res.getImageUrl()).isEqualTo("registry.example.com:5000/team/app:v1.0");
    }

    @Test
    void create_rejectsLatestTag() {
        CreateDockerImageRequest req = new CreateDockerImageRequest();
        req.setName("app");
        req.setImageUrl("nginx:latest");

        assertThatThrownBy(() -> service.create(OWNER, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("':latest' is forbidden");
        verify(dockerImageRepository, never()).save(any());
    }

    @Test
    void create_rejectsNoTagRegistryPort() {
        CreateDockerImageRequest req = new CreateDockerImageRequest();
        req.setName("app");
        req.setImageUrl("registry:5000/app");

        assertThatThrownBy(() -> service.create(OWNER, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("image_url must be");
        verify(dockerImageRepository, never()).save(any());
    }

    @Test
    void create_rejectsUserinfo() {
        CreateDockerImageRequest req = new CreateDockerImageRequest();
        req.setName("app");
        req.setImageUrl("user:pass@registry.io/app:1.0");

        assertThatThrownBy(() -> service.create(OWNER, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("image_url must be");
        verify(dockerImageRepository, never()).save(any());
    }

    @Test
    void create_rejectsMalformedUrl() {
        CreateDockerImageRequest req = new CreateDockerImageRequest();
        req.setName("app");
        req.setImageUrl("not-a-valid-image-url");

        assertThatThrownBy(() -> service.create(OWNER, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("image_url must be");
        verify(dockerImageRepository, never()).save(any());
    }

    @Test
    void create_stampsOwnerId() {
        CreateDockerImageRequest req = new CreateDockerImageRequest();
        req.setName("app");
        req.setImageUrl("app:1");

        when(dockerImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(OWNER, req);
        verify(dockerImageRepository).save(argThat(image ->
                OWNER.equals(image.getOwnerId()) && "app".equals(image.getName())));
    }

    // ---- update ----

    @Test
    void update_ownerMismatch_throws404() {
        when(dockerImageRepository.findById(OWNER)).thenReturn(Optional.of(activeImage(OWNER)));
        assertThatThrownBy(() -> service.update(OWNER, OTHER, new UpdateDockerImageRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void update_rejectsLatestTag() {
        when(dockerImageRepository.findById(OWNER)).thenReturn(Optional.of(activeImage(OWNER)));
        UpdateDockerImageRequest req = new UpdateDockerImageRequest();
        req.setImageUrl("nginx:latest");

        assertThatThrownBy(() -> service.update(OWNER, OWNER, req))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("':latest' is forbidden");
    }

    @Test
    void update_systemPrincipalEditableByAnyLecturer() {
        // Review: 2026-09-27, Pullfrog PR #20 — shared system-principal defaults
        // must be editable by any lecturer (the escape hatch, now type-safe).
        DockerImage systemImage = DockerImage.builder()
                .id(OWNER).name("app").imageUrl("app:1")
                .ownerId(Constant.IMAGE_LIBRARY_SYSTEM_OWNER).build();
        when(dockerImageRepository.findById(OWNER)).thenReturn(Optional.of(systemImage));
        when(dockerImageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        UpdateDockerImageRequest req = new UpdateDockerImageRequest();
        req.setImageUrl("app:2");

        service.update(OWNER, OTHER, req);
        verify(dockerImageRepository).save(argThat(image -> "app:2".equals(image.getImageUrl())));
    }

    @Test
    void update_nullOwnerId_throws404_not500() {
        // Review: 2026-09-27, Pullfrog PR #20 — a missing ownerId (V4 rolling-deploy
        // window) must degrade to a clean 404, not a NullPointerException.
        when(dockerImageRepository.findById(OWNER)).thenReturn(Optional.of(
                DockerImage.builder().id(OWNER).name("app").imageUrl("app:1").ownerId(null).build()));
        assertThatThrownBy(() -> service.update(OWNER, OWNER, new UpdateDockerImageRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- delete ----

    @Test
    void delete_ownerMismatch_throws404() {
        when(dockerImageRepository.findById(OWNER)).thenReturn(Optional.of(activeImage(OWNER)));
        assertThatThrownBy(() -> service.delete(OWNER, OTHER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_linked_throws409() {
        when(dockerImageRepository.findById(OWNER)).thenReturn(Optional.of(activeImage(OWNER)));
        when(assignmentDockerImageRepository.findByDockerImageIdIn(List.of(OWNER)))
                .thenReturn(List.of(mock(AssignmentDockerImage.class))); // linked -> conflict

        assertThatThrownBy(() -> service.delete(OWNER, OWNER))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("still referenced by an assignment");
        verify(dockerImageRepository, never()).save(any());
    }

    @Test
    void delete_softDeletes() {
        when(dockerImageRepository.findById(OWNER)).thenReturn(Optional.of(activeImage(OWNER)));
        when(assignmentDockerImageRepository.findByDockerImageIdIn(List.of(OWNER)))
                .thenReturn(List.of());

        service.delete(OWNER, OWNER);
        verify(dockerImageRepository).save(argThat(image -> image.getDeletedAt() != null));
    }

    @Test
    void delete_systemPrincipalEditableByAnyLecturer() {
        DockerImage systemImage = DockerImage.builder()
                .id(OWNER).name("app").imageUrl("app:1")
                .ownerId(Constant.IMAGE_LIBRARY_SYSTEM_OWNER).build();
        when(dockerImageRepository.findById(OWNER)).thenReturn(Optional.of(systemImage));
        when(assignmentDockerImageRepository.findByDockerImageIdIn(List.of(OWNER)))
                .thenReturn(List.of());

        service.delete(OWNER, OTHER);
        verify(dockerImageRepository).save(argThat(image -> image.getDeletedAt() != null));
    }

    // ---- listActiveUrls / list ----

    @Test
    void listActiveUrls_returnsUrls() {
        when(dockerImageRepository.findAll()).thenReturn(List.of(
                activeImage(UUID.randomUUID()),
                DockerImage.builder().id(UUID.randomUUID()).imageUrl("db:16").build()));

        List<String> urls = service.listActiveUrls();
        assertThat(urls).containsExactlyInAnyOrder("app:1", "db:16");
    }

    @Test
    void list_filtersByName() {
        var pageable = org.springframework.data.domain.Pageable.unpaged();
        when(dockerImageRepository.findByNameContainingIgnoreCase("app", pageable))
                .thenReturn(org.springframework.data.domain.Page.empty());

        service.list("app", pageable);
        verify(dockerImageRepository).findByNameContainingIgnoreCase("app", pageable);
    }

    @Test
    void listWithoutName_usesFindAll() {
        var pageable = org.springframework.data.domain.Pageable.unpaged();
        when(dockerImageRepository.findAll(pageable)).thenReturn(org.springframework.data.domain.Page.empty());

        service.list(null, pageable);
        verify(dockerImageRepository).findAll(pageable);
    }
}
