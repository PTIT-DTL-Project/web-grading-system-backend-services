package vn.edu.ptit.web_grading_system.course_service.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.course_service.entities.DockerImage;
import vn.edu.ptit.web_grading_system.course_service.repositories.DockerImageRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review: 2026-09-27, Pullfrog PR #20 — repository slice for the
 * {@link DockerImageRepository} methods used by the service layer.
 * Runs with flyway disabled and ddl-auto=create-drop on H2 so the
 * entity mapping (including the new {@code owner_id} column) builds
 * the schema; the @SQLRestriction excludes soft-deleted rows.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:test"
})
class DockerImageRepositoryTest {

    @Autowired private DockerImageRepository repo;

    @Test
    @Transactional
    void findAllByIdIn_deduplicates() {
        DockerImage aImg = repo.save(DockerImage.builder().name("a").imageUrl("a:1").ownerId(UUID.randomUUID()).build());
        DockerImage bImg = repo.save(DockerImage.builder().name("b").imageUrl("b:2").ownerId(UUID.randomUUID()).build());

        assertThat(repo.findAllByIdIn(List.of(aImg.getId(), aImg.getId(), bImg.getId())))
                .hasSize(2)
                .extracting(DockerImage::getImageUrl)
                .containsExactlyInAnyOrder("a:1", "b:2");
    }

    @Test
    @Transactional
    void findByNameContainingIgnoreCase_filters() {
        UUID owner = UUID.randomUUID();
        repo.save(DockerImage.builder().name("My App").imageUrl("a:1").ownerId(owner).build());
        repo.save(DockerImage.builder().name("my image").imageUrl("b:2").ownerId(owner).build());

        assertThat(repo.findByNameContainingIgnoreCase("my", org.springframework.data.domain.Pageable.unpaged())
                        .getContent()).hasSize(2);
        assertThat(repo.findByNameContainingIgnoreCase("app", org.springframework.data.domain.Pageable.unpaged())
                        .getContent()).hasSize(1);
    }
}
