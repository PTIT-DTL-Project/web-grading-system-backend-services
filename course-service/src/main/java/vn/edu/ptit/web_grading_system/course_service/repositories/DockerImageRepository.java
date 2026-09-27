package vn.edu.ptit.web_grading_system.course_service.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entities.DockerImage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface DockerImageRepository extends JpaRepository<DockerImage, UUID> {

    /** Active images only (soft delete is enforced by the entity's @SQLRestriction). */
    Page<DockerImage> findAll(Pageable pageable);

    List<DockerImage> findAllByIdIn(List<UUID> ids);

    @Modifying
    @Query("""
            update DockerImage d
            set d.deletedAt = :now
            where d.id = :id
              and d.deletedAt is null""")
    void softDeleteById(@Param("id") UUID id, @Param("now") OffsetDateTime now);
}
