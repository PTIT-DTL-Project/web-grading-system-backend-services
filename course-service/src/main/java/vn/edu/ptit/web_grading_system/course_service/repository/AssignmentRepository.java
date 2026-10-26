package vn.edu.ptit.web_grading_system.course_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entity.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entity.AssignmentPurpose;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    Optional<Assignment> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<Assignment> findAllByClassId(UUID classId);

    List<Assignment> findAllByClassIdAndPurpose(UUID classId, AssignmentPurpose purpose);

    boolean existsByIdAndPublished(UUID id, boolean published);

    // Uniqueness of title scoped to the owner (not the class): a lecturer cannot
    // reuse the same title across graded and practice assignments.
    boolean existsByOwnerIdAndTitleIgnoreCase(UUID ownerId, String title);

    boolean existsByOwnerIdAndTitleIgnoreCaseAndIdNot(UUID ownerId, String title, UUID id);

    /**
     * Owner-scoped listing with optional combinable filters. Sorted oldest-last.
     * Null filter arguments are ignored. Search matches title or topic.
     */
    @Query("""
            select a from Assignment a
            where a.ownerId = :ownerId
              and (:classId is null or a.classId = :classId)
              and (:purpose is null or a.purpose = :purpose)
              and (:topic is null or lower(a.topic) = lower(cast(:topic as string)))
              and (:published is null or a.published = :published)
              and (:search is null
                   or lower(a.title) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(a.topic, '')) like lower(concat('%', cast(:search as string), '%')))
            order by a.createdAt desc
            """)
    Page<Assignment> findMine(@Param("ownerId") UUID ownerId,
                              @Param("classId") UUID classId,
                              @Param("purpose") AssignmentPurpose purpose,
                              @Param("topic") String topic,
                              @Param("published") Boolean published,
                              @Param("search") String search,
                              Pageable pageable);

    /**
     * Student-facing listing: only published GRADED assignments whose class the
     * student is enrolled in. Practice assignments (classId NULL) are excluded
     * here — they are served via {@link #findPublishedPractice}. Null filters ignored.
     */
    @Query("""
            select a from Assignment a
            where a.published = true
              and a.purpose = vn.edu.ptit.web_grading_system.course_service.entity.AssignmentPurpose.GRADED
              and a.classId in :classIds
              and (:classId is null or a.classId = :classId)
              and (:topic is null or lower(a.topic) = lower(cast(:topic as string)))
              and (:search is null
                   or lower(a.title) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(a.topic, '')) like lower(concat('%', cast(:search as string), '%')))
            order by a.createdAt desc
            """)
    Page<Assignment> findPublishedForStudent(@Param("classIds") List<UUID> classIds,
                                             @Param("classId") UUID classId,
                                             @Param("topic") String topic,
                                             @Param("search") String search,
                                             Pageable pageable);

    /**
     * Published practice assignments (class-independent), visible to every student.
     */
    @Query("""
            select a from Assignment a
            where a.published = true
              and a.purpose = vn.edu.ptit.web_grading_system.course_service.entity.AssignmentPurpose.PRACTICE
              and a.classId is null
              and (:topic is null or lower(a.topic) = lower(cast(:topic as string)))
              and (:search is null
                   or lower(a.title) like lower(concat('%', cast(:search as string), '%'))
                   or lower(coalesce(a.topic, '')) like lower(concat('%', cast(:search as string), '%')))
            order by a.createdAt desc
            """)
    Page<Assignment> findPublishedPractice(@Param("topic") String topic,
                                           @Param("search") String search,
                                           Pageable pageable);

    @Query("""
            select distinct a.topic from Assignment a
            where a.ownerId = :ownerId
              and a.purpose = :purpose
              and a.topic is not null
              and a.topic <> ''
            order by a.topic
            """)
    List<String> findDistinctTopics(@Param("ownerId") UUID ownerId,
                                    @Param("purpose") AssignmentPurpose purpose);

    @Query("""
            select distinct a.topic from Assignment a
            where a.published = true
              and a.purpose = vn.edu.ptit.web_grading_system.course_service.entity.AssignmentPurpose.PRACTICE
              and a.classId is null
              and a.topic is not null
              and a.topic <> ''
            order by a.topic
            """)
    List<String> findDistinctPublishedTopics(@Param("purpose") AssignmentPurpose purpose);
}
