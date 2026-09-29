package vn.edu.ptit.web_grading_system.course_service.spec;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
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

    public static final String SEARCH_PATTERN_FORMAT = "%s%";

    public static Specification<CourseClass> qMatches(String q) {
        if (q == null || q.isBlank()) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        final String namePattern = SEARCH_PATTERN_FORMAT.formatted(q.trim().toLowerCase());
        final String semesterPattern = SEARCH_PATTERN_FORMAT.formatted(q.trim().toLowerCase());
        return (root, query, criteriaBuilder) -> criteriaBuilder.or(
                criteriaBuilder.like(criteriaBuilder.lower(root.get(CourseClassAttr.NAME)), namePattern),
                criteriaBuilder.like(criteriaBuilder.lower(root.get(CourseClassAttr.SEMESTER)), semesterPattern)
        );
    }

    public static Specification<CourseClass> statusIs(ClassStatus status) {
        if (status == null) {
            return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(CourseClassAttr.STATUS), status);
    }
}