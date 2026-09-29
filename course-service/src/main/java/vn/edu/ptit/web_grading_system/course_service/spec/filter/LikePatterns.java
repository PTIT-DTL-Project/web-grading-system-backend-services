package vn.edu.ptit.web_grading_system.course_service.spec.filter;

import java.util.Locale;

/**
 * Shared LIKE-pattern escaping for structured search filters.
 *
 * <p>Escapes the three characters that Hibernate/JPA treats as wildcards:
 * <ul>
 *   <li>{@code \} → {@code \\} (escape the escape char first)</li>
 *   <li>{@code %} → {@code \\%}</li>
 *   <li>{@code _} → {@code \\_}</li>
 * </ul>
 *
 * <p>After escaping, wrap the result in {@code %...%} for a contains-search.
 */
public final class LikePatterns {

    private LikePatterns() {}

    /**
     * Lower-cases and escapes a raw user term for use in a LIKE pattern.
     *
     * <p>The returned string is safe to interpolate between {@code %} wildcards
     * and pass to {@code criteriaBuilder.like(..., pattern, '\\')}.
     *
     * <p>Order matters: backslash is replaced first so the newly inserted
     * backslashes for {@code %} and {@code _} are not themselves re-escaped.
     */
    public static String escapeContains(String term) {
        if (term == null || term.isBlank()) return "";
        String lower = term.trim().toLowerCase(Locale.ROOT);
        return "%" + lower.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
