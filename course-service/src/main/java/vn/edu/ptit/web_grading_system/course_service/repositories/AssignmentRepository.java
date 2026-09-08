package vn.edu.ptit.web_grading_system.course_service.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entities.Assignment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Assignment> findAllByClassId(UUID classId);

    boolean existsByIdAndPublished(UUID id, boolean published);

    boolean existsByOwnerIdAndClassIdAndTitle(UUID ownerId, UUID classId, String title);

    boolean existsByOwnerIdAndClassIdAndTitleAndIdNot(UUID ownerId, UUID classId, String title, UUID id);

    /**
     * Owner-scoped listing with optional combinable filters. Sorted oldest-last.
     * Null filter arguments are ignored.
     */
    @Query("""
            select a from Assignment a
            where a.ownerId = :ownerId
              and (:classId is null or a.classId = :classId)
              and (:published is null or a.published = :published)
              and (:search is null or lower(a.title) like lower(concat('%', cast(:search as string), '%')))
            order by a.createdAt desc
            """)
    Page<Assignment> findMine(@Param("ownerId") UUID ownerId,
                              @Param("classId") UUID classId,
                              @Param("published") Boolean published,
                              @Param("search") String search,
                              Pageable pageable);

    /**
     * Student-facing listing: only published assignments whose class the student is
     * enrolled in (class_students.student_user_id = :studentId). Null filters ignored.
     */
    @Query("""
            select a from Assignment a
            where a.published = true
              and a.classId in :classIds
              and (:classId is null or a.classId = :classId)
              and (:search is null or lower(a.title) like lower(concat('%', cast(:search as string), '%')))
            order by a.createdAt desc
            """)
    Page<Assignment> findPublishedForStudent(@Param("classIds") List<UUID> classIds,
                                             @Param("classId") UUID classId,
                                             @Param("search") String search,
                                             Pageable pageable);
}