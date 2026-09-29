package vn.edu.ptit.web_grading_system.course_service.spec.filter;

import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

/**
 * Generic Specification composition from a parsed filter map.
 *
 * <p>Given a filter object (e.g. {@code ClassFilter}) and a map of field-name
 * → spec-builder, this composes an AND chain: one predicate per non-null
 * field. Adding a new filterable field = add one field to the filter record
 * + one entry in the builder map.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * Specification<CourseClass> spec = FilterSpecifications.compose(
 *     filter,
 *     Map.of(
 *         CourseClassAttr.NAME,     CourseClassSpecifications::nameMatches,
 *         CourseClassAttr.SEMESTER, CourseClassSpecifications::semesterMatches
 *     )
 * );
 * }</pre>
 */
public final class FilterSpecifications {

    private FilterSpecifications() {}

    /**
     * Composes a Specification by AND-ing one predicate per non-null filter field.
     *
     * <p>The caller supplies a {@code Map<String, Function<String, Specification<T>>>}
     * that maps field names to spec builders. Each builder receives the raw field
     * value (already escaped at the LIKE-pattern level). Missing builders cause
     * the field to be skipped with an {@link IllegalStateException}, so a field
     * present in the filter but absent from the map fails loudly rather than
     * silently dropping the predicate.
     *
     * @param filter     parsed filter record (may be null or have null fields)
     * @param builders   field-name → spec-builder map; every non-null filter
     *                   field must have a matching entry
     * @param <T>        entity type
     * @return a Specification that AND-s all active field predicates; bare {@code conjunction()} when no fields are set
     */
    public static <T> Specification<T> compose(Object filter, Map<String, ? extends java.util.function.Function<String, Specification<T>>> builders) {
        if (filter == null) {
            return (root, query, cb) -> cb.conjunction();
        }

        Specification<T> result = (root, query, cb) -> cb.conjunction();
        Class<?> clazz = filter.getClass();

        if (clazz.isRecord()) {
            for (java.lang.reflect.RecordComponent component : clazz.getRecordComponents()) {
                String fieldName = component.getName();
                try {
                    Object value = component.getAccessor().invoke(filter);
                    if (value == null) continue;
                    String strValue = value.toString();
                    if (strValue.isBlank()) continue;

                    var builder = builders.get(fieldName);
                    if (builder == null) {
                        throw new IllegalStateException(
                                "No spec builder registered for filter field '" + fieldName + "' in " + clazz.getName());
                    }

                    Specification<T> fieldSpec = builder.apply(strValue);
                    result = result.and(fieldSpec);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Failed to read filter field " + fieldName + " from " + clazz.getName(), e);
                }
            }
        }

        return result;
    }
}
