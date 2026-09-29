package vn.edu.ptit.web_grading_system.course_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import vn.edu.ptit.web_grading_system.course_service.entity.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.spec.CourseClassSpecifications;

@Repository
public interface CourseClassRepository extends JpaRepository<CourseClass, UUID>, JpaSpecificationExecutor<CourseClass> {

    Optional<CourseClass> findByIdAndOwnerId(UUID id, UUID ownerId);

    boolean existsByOwnerIdAndNameAndSemester(UUID ownerId, String name, String semester);

    /**
     * Owner-scoped listing with optional combinable filters.
     * Null filter arguments are ignored.
     */
    Page<CourseClass> findAll(Specification<CourseClass> spec, Pageable pageable);

    /**
     * Shortcut for a common pattern: owner + optional q + optional status.
     * Null filter arguments are ignored.
     */
    default Page<CourseClass> findMine(
            UUID ownerId,
            String q,
            ClassStatus status,
            Pageable pageable) {
        return findAll(
                CourseClassSpecifications.ownedBy(ownerId)
                        .and(CourseClassSpecifications.qMatches(q))
                        .and(CourseClassSpecifications.statusIs(status)),
                pageable);
    }
}