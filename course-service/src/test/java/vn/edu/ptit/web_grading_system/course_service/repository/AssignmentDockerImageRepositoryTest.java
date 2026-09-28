package vn.edu.ptit.web_grading_system.course_service.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.course_service.entity.AssignmentDockerImage;
import vn.edu.ptit.web_grading_system.course_service.entity.DockerImage;
import vn.edu.ptit.web_grading_system.course_service.repository.AssignmentDockerImageRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.DockerImageRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review: 2026-09-27, Pullfrog PR #20 — {@link AssignmentDockerImageRepository#findDockerImageIdsByAssignmentId}
 * must return scalar {@link java.util.UUID}s. The fix replaces the unparseable derived
 * query name with an explicit JPQL projection; this slice verifies the query actually
 * builds and returns UUIDs against a real database (the only layer that catches the
 * original ConversionFailedException).
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:test"
})
class AssignmentDockerImageRepositoryTest {

    @Autowired private AssignmentDockerImageRepository linkRepo;
    @Autowired private DockerImageRepository dockerImageRepo;

    @Test
    @Transactional
    void findDockerImageIdsByAssignmentId_returnsScalarUUIDs() {
        UUID owner = UUID.randomUUID();
        DockerImage img = dockerImageRepo.save(DockerImage.builder()
                .name("db").imageUrl("db:16").ownerId(owner).build());
        UUID assignmentId = UUID.randomUUID();
        linkRepo.save(AssignmentDockerImage.builder()
                .assignmentId(assignmentId).dockerImageId(img.getId()).build());

        List<UUID> ids = linkRepo.findDockerImageIdsByAssignmentId(assignmentId);

        assertThat(ids).containsExactly(img.getId());
    }

    @Test
    @Transactional
    void findByDockerImageIdIn_returnsActiveOnly() {
        UUID owner = UUID.randomUUID();
        DockerImage img = dockerImageRepo.save(DockerImage.builder()
                .name("db").imageUrl("db:16").ownerId(owner).build());
        UUID assignmentId = UUID.randomUUID();
        AssignmentDockerImage link = linkRepo.save(AssignmentDockerImage.builder()
                .assignmentId(assignmentId).dockerImageId(img.getId()).build());
        link.setDeletedAt(OffsetDateTime.now());

        assertThat(linkRepo.findByDockerImageIdIn(List.of(img.getId()))).isEmpty();
    }

    @Test
    @Transactional
    void findByAssignmentId_returnsLinks() {
        UUID owner = UUID.randomUUID();
        DockerImage img = dockerImageRepo.save(DockerImage.builder()
                .name("db").imageUrl("db:16").ownerId(owner).build());
        UUID assignmentId = UUID.randomUUID();
        linkRepo.save(AssignmentDockerImage.builder()
                .assignmentId(assignmentId).dockerImageId(img.getId()).build());
        assertThat(linkRepo.findByAssignmentId(assignmentId)).hasSize(1);
    }
}
