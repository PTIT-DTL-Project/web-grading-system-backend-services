package vn.edu.ptit.web_grading_system.course_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entities.AssignmentDockerImage;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AssignmentDockerImageRepository extends JpaRepository<AssignmentDockerImage, UUID> {

    List<AssignmentDockerImage> findByAssignmentId(UUID assignmentId);

    List<UUID> findDockerImageIdsByAssignmentId(UUID assignmentId);

    List<AssignmentDockerImage> findByDockerImageIdIn(List<UUID> dockerImageIds);

    @Modifying
    @Query("""
            update AssignmentDockerImage a
            set a.deletedAt = :now
            where a.assignmentId = :assignmentId
              and a.deletedAt is null""")
    void softDeleteByAssignmentId(@Param("assignmentId") UUID assignmentId,
                                  @Param("now") OffsetDateTime now);
}
