package vn.edu.ptit.web_grading_system.course_service.spec.filter;

import java.util.Locale;
import java.util.Set;

/**
 * Typed filter object for {@link CourseClass} search.
 *
 * <p>Each field corresponds to one searchable attribute declared in
 * {@code Constant.CourseClassAttr.SEARCHABLE}. Null means "no filter" on
 * that field.
 *
 * <p>Instances are created via {@link #parse(String)} rather than direct
 * construction, so validation lives in one place.
 */
public record ClassFilter(String name, String semester) {

    private static final Set<String> SEARCHABLE = Set.of(
            "name",
            "semester"
    );

    public static ClassFilter empty() {
        return new ClassFilter(null, null);
    }

    public boolean hasName() {
        return name != null && !name.isBlank();
    }

    public boolean hasSemester() {
        return semester != null && !semester.isBlank();
    }

    public boolean isEmpty() {
        return !hasName() && !hasSemester();
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
     * <p>A token without {@code :}, an unknown field, or a blank value all
     * produce a 400-level {@link InvalidFilterException}.
     *
     * @param search raw query string from the request
     * @return parsed and validated filter
     * @throws InvalidFilterException on malformed input
     */
    public static ClassFilter parse(String search) {
        if (search == null || search.isBlank()) {
            return empty();
        }

        String trimmed = search.trim();
        if (trimmed.length() > 500) {
            throw new InvalidFilterException("search must be ≤ 500 characters");
        }

        String[] tokens = trimmed.split(";", -1);
        if (tokens.length > 10) {
            throw new InvalidFilterException("search may contain at most 10 filters");
        }

        String resultName = null;
        String resultSemester = null;

        for (String token : tokens) {
            int colon = token.indexOf(':');
            if (colon < 0) {
                throw new InvalidFilterException(
                        "Malformed filter '" + token + "': expected 'field:value'. Allowed fields: name, semester");
            }

            String field = token.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1).trim();

            if (value.isBlank()) {
                throw new InvalidFilterException("Filter value for '" + field + "' must not be blank");
            }
            if (value.length() > 200) {
                throw new InvalidFilterException("Filter value for '" + field + "' must be ≤ 200 characters");
            }
            if (!SEARCHABLE.contains(field)) {
                throw new InvalidFilterException(
                        "Unknown filter field: '" + field + "'. Allowed fields: " + String.join(", ", sorted(SEARCHABLE)));
            }

            switch (field) {
                case "name" -> {
                    if (resultName != null) {
                        throw new InvalidFilterException("Duplicate filter field: 'name'");
                    }
                    resultName = value;
                }
                case "semester" -> {
                    if (resultSemester != null) {
                        throw new InvalidFilterException("Duplicate filter field: 'semester'");
                    }
                    resultSemester = value;
                }
                default -> {
                    // unreachable — SEARCHABLE guard above
                }
            }
        }

        return new ClassFilter(resultName, resultSemester);
    }

    private static String[] sorted(java.util.Set<String> set) {
        return set.stream().sorted().toArray(String[]::new);
    }
}
