package vn.edu.ptit.web_grading_system.course_service.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import vn.edu.ptit.web_grading_system.course_service.entity.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.spec.CourseClassSpecifications;
import vn.edu.ptit.web_grading_system.course_service.spec.filter.ClassFilter;

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
     * Owner-scoped listing with optional structured search and status filter.
     * Null/blank arguments are ignored.
     *
     * @param ownerId  caller identity from X-User-Id
     * @param filter   parsed search filter; null or empty → no text filter
     * @param status   optional status filter; null → both ACTIVE and ARCHIVED
     * @param pageable paging + sort from controller
     */
    // Review: 2026-09-29, structured-filter design — findMine now accepts a
    // ClassFilter instead of a raw String. Parsing + validation lives in
    // ClassFilter.parse(), so this method only composes specs.
    default Page<CourseClass> findMine(
            UUID ownerId,
            ClassFilter filter,
            ClassStatus status,
            Pageable pageable) {
        return findAll(
                CourseClassSpecifications.ownedBy(ownerId)
                        .and(CourseClassSpecifications.from(filter))
                        .and(CourseClassSpecifications.statusIs(status)),
                pageable);
    }
}
