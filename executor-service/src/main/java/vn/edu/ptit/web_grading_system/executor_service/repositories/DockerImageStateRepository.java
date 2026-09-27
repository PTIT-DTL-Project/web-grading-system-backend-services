package vn.edu.ptit.web_grading_system.executor_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.executor_service.entities.DockerImageState;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DockerImageStateRepository extends JpaRepository<DockerImageState, UUID> {

    Optional<DockerImageState> findByImageUrlAndPodId(String imageUrl, String podId);

    @Modifying
    @Query("""
            update DockerImageState s
            set s.deletedAt = :now
            where s.updatedAt < :cutoff
              and s.deletedAt is null""")
    int pruneBefore(@Param("cutoff") OffsetDateTime cutoff,
                    @Param("now") OffsetDateTime now);
}
