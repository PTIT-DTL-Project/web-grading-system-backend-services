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

    // Review: 2026-09-29, Pullfrog PR — removed misleading Javadoc and redundant
    // findAll(Specification, Pageable) redeclaration. The inherited method from
    // JpaSpecificationExecutor already provides this; the Javadoc was copy-pasted
    // from AssignmentRepository.findMine and described semantics this method does
    // not implement (no owner scope, no null-filter ignoring).
    /**
     * Shortcut for owner-scoped listing with optional q + status filters.
     * Null/blank filter arguments are ignored.
     */
    // Review: 2026-09-29, Pullfrog PR — moved q trim/blank normalization here
    // so ClassService no longer duplicates it. findMine is now the single source
    // of truth for the spec chain composition.
    default Page<CourseClass> findMine(
            UUID ownerId,
            String q,
            ClassStatus status,
            Pageable pageable) {
        String trimmed = (q == null || q.isBlank()) ? null : q.trim();
        return findAll(
                CourseClassSpecifications.ownedBy(ownerId)
                        .and(CourseClassSpecifications.qMatches(trimmed))
                        .and(CourseClassSpecifications.statusIs(status)),
                pageable);
    }
}