package vn.edu.ptit.web_grading_system.course_service.spec.filter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

/**
 * Generic structured-search parser.
 *
 * <p>Parses expressions of the form {@code field:value;field2:value2} into a
 * {@code Map<String, String>} keyed by field name. Field names are validated
 * against an allow-list supplied by the caller, so the parser is reusable
 * across entities without knowing their fields.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li>Null / blank input → empty map (no filter)</li>
 *   <li>Each token is {@code field:value}; field names are lower-cased before lookup</li>
 *   <li>A token without {@code :} is rejected with {@link InvalidFilterException}</li>
 *   <li>Blank values are rejected with {@link InvalidFilterException}</li>
 *   <li>Unknown fields are rejected with {@link InvalidFilterException} listing allowed fields</li>
 *   <li>Values are returned raw — escaping happens at the LIKE-pattern level via {@link LikePatterns}</li>
 * </ul>
 */
public final class FilterParser {

    private FilterParser() {}

    private static final int MAX_TERMS = 10;
    private static final int MAX_VALUE_LENGTH = 200;

    /**
     * Parses a raw search string into a map of field → value.
     *
     * @param search     raw query string from the request
     * @param searchable set of allowed field names for this entity
     * @return immutable map of parsed filters; empty when search is blank
     * @throws InvalidFilterException on malformed input, unknown fields, blank values, or limit violations
     */
    public static Map<String, String> parse(String search, Set<String> searchable) {
        if (search == null || search.isBlank()) return Map.of();

        String term = search.trim();
        if (term.length() > 500) {
            throw new InvalidFilterException("search must be ≤ 500 characters");
        }

        String[] tokens = term.split(";", -1);
        if (tokens.length > MAX_TERMS) {
            throw new InvalidFilterException("search may contain at most " + MAX_TERMS + " filters");
        }

        Map<String, String> result = new LinkedHashMap<>();

        for (String token : tokens) {
            int colon = token.indexOf(':');
            if (colon < 0) {
                throw missingColon(token, searchable);
            }

            String field = token.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = token.substring(colon + 1).trim();

            if (value.isBlank()) {
                throw new InvalidFilterException("Filter value for '" + field + "' must not be blank");
            }
            if (value.length() > MAX_VALUE_LENGTH) {
                throw new InvalidFilterException("Filter value for '" + field + "' must be ≤ " + MAX_VALUE_LENGTH + " characters");
            }
            if (!searchable.contains(field)) {
                throw unknownField(field, searchable);
            }
            if (result.containsKey(field)) {
                throw new InvalidFilterException("Duplicate filter field: '" + field + "'");
            }

            result.put(field, value);
        }

        return Map.copyOf(result);
    }

    private static InvalidFilterException missingColon(String token, Set<String> searchable) {
        return new InvalidFilterException(
                "Malformed filter '" + token + "': expected 'field:value'. Allowed fields: " + String.join(", ", sorted(searchable)));
    }

    private static InvalidFilterException unknownField(String field, Set<String> searchable) {
        return new InvalidFilterException(
                "Unknown filter field: '" + field + "'. Allowed fields: " + String.join(", ", sorted(searchable)));
    }

    private static String[] sorted(Set<String> set) {
        return set.stream().sorted().toArray(String[]::new);
    }
}
