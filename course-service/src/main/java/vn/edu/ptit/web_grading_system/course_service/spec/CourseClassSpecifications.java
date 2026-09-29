package vn.edu.ptit.web_grading_system.course_service.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

import vn.edu.ptit.web_grading_system.course_service.entity.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.Constant.CourseClassAttr;
import vn.edu.ptit.web_grading_system.course_service.spec.filter.ClassFilter;
import vn.edu.ptit.web_grading_system.course_service.spec.filter.FilterSpecifications;
import vn.edu.ptit.web_grading_system.course_service.spec.filter.LikePatterns;

public final class CourseClassSpecifications {

    private CourseClassSpecifications() {}

    public static Specification<CourseClass> ownedBy(UUID ownerId) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(CourseClassAttr.OWNER_ID), ownerId);
    }

    // Review: 2026-09-29, Pullfrog PR — removed SEARCH_PATTERN_FORMAT constant.
    // String.formatted("%s%") throws UnknownFormatConversionException on the trailing
    // bare '%'; build the LIKE pattern by concatenation instead.
    // Also use Locale.ROOT for toLowerCase() to avoid Turkish-I edge case.

    /**
     * Case-insensitive contains filter on class name.
     * Null / blank input returns {@code conjunction()} (no predicate).
     */
    public static Specification<CourseClass> nameMatches(String name) {
        if (name == null || name.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = LikePatterns.escapeContains(name);
        return (root, query, cb) -> cb.like(cb.lower(root.get(CourseClassAttr.NAME)), pattern, '\\');
    }

    /**
     * Case-insensitive contains filter on semester.
     * Null / blank input returns {@code conjunction()} (no predicate).
     */
    public static Specification<CourseClass> semesterMatches(String semester) {
        if (semester == null || semester.isBlank()) {
            return (root, query, cb) -> cb.conjunction();
        }
        String pattern = LikePatterns.escapeContains(semester);
        return (root, query, cb) -> cb.like(cb.lower(root.get(CourseClassAttr.SEMESTER)), pattern, '\\');
    }

    /**
     * Builds a Specification from a parsed {@link ClassFilter} by composing
     * one predicate per non-null field.
     *
     * <p>The {@code builders} map keys are the {@link CourseClassAttr} constant
     * names; values are functions that turn a raw string value into a predicate.
     * Adding a searchable field = one entry in the filter record + one entry here.
     */
    // Review: 2026-09-29, structured-filter design — from() is the single
    // composition point for class search. It delegates to FilterSpecifications.compose(),
    // which reflects over the filter record's getters and AND-s the matching specs.
    public static Specification<CourseClass> from(ClassFilter filter) {
        Map<String, java.util.function.Function<String, Specification<CourseClass>>> builders = Map.of(
                CourseClassAttr.NAME, CourseClassSpecifications::nameMatches,
                CourseClassAttr.SEMESTER, CourseClassSpecifications::semesterMatches
        );
        return FilterSpecifications.compose(filter, builders);
    }

    /**
     * @deprecated Use {@link #nameMatches(String)} and/or {@link #semesterMatches(String)}
     *             with {@link ClassFilter#parse(String)} instead. This method's parse-and-match
     *             dual duty is now split: parsing lives in {@link ClassFilter}, matching lives
     *             in the named methods above.
     */
    @Deprecated(since = "structured-search-refactor")
    public static Specification<CourseClass> qMatches(String q) {
        if (q == null || q.isBlank()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        String term = q.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        // Review: 2026-09-29, Pullfrog PR — escape LIKE wildcards before wrapping
        // in %...%. criteriaBuilder.like() with escape '\\' treats user-typed '%'
        // and '_' as literals. This is stricter than AssignmentRepository, which
        // does not escape wildcards.
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
