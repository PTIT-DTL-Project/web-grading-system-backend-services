package vn.edu.ptit.web_grading_system.course_service.spec.filter;

import vn.edu.ptit.web_grading_system.course_service.Constant.CourseClassAttr;

/**
 * Typed filter object for {@code CourseClass} search.
 *
 * <p>Each field corresponds to one searchable attribute declared in
 * {@link CourseClassAttr#SEARCHABLE}. Null means "no filter" on
 * that field.
 *
 * <p>Instances are created via {@link #parse(String)} rather than direct
 * construction, so validation lives in one place.
 */
public record ClassFilter(String name, String semester) {

    public static ClassFilter empty() {
        return new ClassFilter(null, null);
    }

    /**
     * Parses a structured search expression into a typed filter.
     *
     * <p>Accepted forms:
     * <ul>
     *   <li>{@code "name:PTIT;semester:20261"} → both fields set</li>
     *   <li>{@code "semester:20261"} → only semester set</li>
     *   <li>{@code "name:PTIT"} → only name set</li>
     * </ul>
     *
     * <p>Note: {@code ;} is the token separator and has no escape mechanism.
     * Values containing {@code ;} cannot be searched.
     *
     * <p>A token without {@code :}, an unknown field, or a blank value all
     * produce a 400-level {@link InvalidFilterException}.
     *
     * @param search raw query string from the request
     * @return parsed and validated filter
     * @throws InvalidFilterException on malformed input
     */
    public static ClassFilter parse(String search) {
        java.util.Map<String, String> map = FilterParser.parse(search, CourseClassAttr.SEARCHABLE);
        return new ClassFilter(
                map.getOrDefault(CourseClassAttr.NAME, null),
                map.getOrDefault(CourseClassAttr.SEMESTER, null)
        );
    }
}
