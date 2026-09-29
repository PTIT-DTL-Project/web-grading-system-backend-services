package vn.edu.ptit.web_grading_system.course_service.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

import vn.edu.ptit.web_grading_system.course_service.entity.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.Constant.CourseClassAttr;

public final class CourseClassSpecifications {

    private CourseClassSpecifications() {}

    public static Specification<CourseClass> ownedBy(UUID ownerId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(CourseClassAttr.OWNER_ID), ownerId);
    }

    // Review: 2026-09-29, Pullfrog PR — removed SEARCH_PATTERN_FORMAT constant.
    // String.formatted("%s%") throws UnknownFormatConversionException on the trailing
    // bare '%'; build the LIKE pattern by concatenation instead.
    // Also use Locale.ROOT for toLowerCase() to avoid Turkish-I edge case.
    public static Specification<CourseClass> qMatches(String q) {
        if (q == null || q.isBlank()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        String term = q.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + term + "%";
        // Review: 2026-09-29, Pullfrog PR — escape LIKE wildcards.
        // criteriaBuilder.like() treats '%' and '_' as wildcards; passing '\\'
        // as the escape char makes user-typed '%' and '_' literal, matching the
        // sibling AssignmentRepository behavior.
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get(CourseClassAttr.NAME)), pattern, '\\'),
                criteriaBuilder.like(criteriaBuilder.lower(root.get(CourseClassAttr.SEMESTER)), pattern, '\\')
        );
    }

    public static Specification<CourseClass> statusIs(ClassStatus status) {
        if (status == null) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(CourseClassAttr.STATUS), status);
    }
}