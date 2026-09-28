package vn.edu.ptit.web_grading_system.executor_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.executor_service.entity.DockerImageState;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DockerImageStateRepository extends JpaRepository<DockerImageState, UUID> {

    Optional<DockerImageState> findByImageUrlAndPodId(String imageUrl, String podId);

    // Review: 2026-09-27, Pullfrog PR #21 — a declared @Modifying @Query gets no
    // transaction from Spring Data (SimpleJpaRepository's @Transactional only
    // covers methods it declares itself), so the scheduled scan aborted at the
    // prune before inspecting a single image; this gives the prune its own tx.
    @Modifying
    @Transactional
    @Query("""
            update DockerImageState s
            set s.deletedAt = :now
            where s.updatedAt < :cutoff
              and s.deletedAt is null""")
    int pruneBefore(@Param("cutoff") OffsetDateTime cutoff,
                    @Param("now") OffsetDateTime now);
}
