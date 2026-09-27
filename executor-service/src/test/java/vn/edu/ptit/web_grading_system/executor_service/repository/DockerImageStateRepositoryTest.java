package vn.edu.ptit.web_grading_system.executor_service.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.executor_service.entities.DockerImageState;
import vn.edu.ptit.web_grading_system.executor_service.entities.ImageScanStatus;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Review: 2026-09-27, Pullfrog PR #20 — validates the V7
 * {@code docker_image_state} DDL, the entity's {@code @SQLRestriction}
 * and the age-based prune query on a real database schema. Runs with
 * flyway disabled and ddl-auto=create-drop on H2 so the entity mapping
 * builds the table; the partial unique index is a production-PG
 * concern and is validated against a live Postgres in CI.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:test"
})
class DockerImageStateRepositoryTest {

    @Autowired private DockerImageStateRepository repo;
    @PersistenceContext private EntityManager em;

    @Test
    @Transactional
    void saveAndFindByImageUrlAndPodId() {
        DockerImageState row = DockerImageState.builder()
                .imageUrl("ghcr.io/org/app:v1.0").podId("pod-a")
                .status(ImageScanStatus.PULLED).build();
        repo.save(row);
        assertThat(repo.findByImageUrlAndPodId("ghcr.io/org/app:v1.0", "pod-a"))
                .hasValueSatisfying(s ->
                        assertThat(s.getStatus()).isEqualTo(ImageScanStatus.PULLED));
    }

    @Test
    @Transactional
    void findExcludesSoftDeleted() {
        DockerImageState row = DockerImageState.builder()
                .imageUrl("a").podId("b").status(ImageScanStatus.FAILED).build();
        repo.save(row);
        row.setDeletedAt(OffsetDateTime.now());
        repo.save(row);
        assertThat(repo.findByImageUrlAndPodId("a", "b")).isEmpty();
    }

    @Test
    @Transactional
    void pruneBeforeDeletesStaleRows() {
        DockerImageState row = DockerImageState.builder()
                .imageUrl("a").podId("b").status(ImageScanStatus.FAILED)
                .lastPulledAt(OffsetDateTime.now().minusMinutes(20)).build();
        repo.save(row);
        // @UpdateTimestamp stamps updatedAt = now on insert; force it
        // old so the row ages past the prune horizon.
        em.createQuery("update DockerImageState s set s.updatedAt = :old where s.id = :id")
                .setParameter("old", OffsetDateTime.now().minusMinutes(60))
                .setParameter("id", row.getId())
                .executeUpdate();
        int pruned = repo.pruneBefore(OffsetDateTime.now().minusMinutes(30),
                OffsetDateTime.now());
        assertThat(pruned).isGreaterThan(0);
        assertThat(repo.findByImageUrlAndPodId("a", "b")).isEmpty();
    }

    @Test
    @Transactional
    void pruneBeforeKeepsFreshRows() {
        DockerImageState row = DockerImageState.builder()
                .imageUrl("a").podId("b").status(ImageScanStatus.FAILED)
                .lastPulledAt(OffsetDateTime.now()).build();
        repo.save(row);
        int pruned = repo.pruneBefore(OffsetDateTime.now().minusMinutes(30),
                OffsetDateTime.now());
        assertThat(pruned).isZero();
        assertThat(repo.findByImageUrlAndPodId("a", "b")).isPresent();
    }
}
