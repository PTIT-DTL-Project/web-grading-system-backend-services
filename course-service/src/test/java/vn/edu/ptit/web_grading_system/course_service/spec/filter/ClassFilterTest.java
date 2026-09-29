package vn.edu.ptit.web_grading_system.course_service.spec.filter;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.course_service.Constant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassFilterTest {

    @Test
    void parse_nullOrBlank_returnsEmptyFilter() {
        assertEquals(new ClassFilter(null, null), ClassFilter.parse(null));
        assertEquals(new ClassFilter(null, null), ClassFilter.parse(""));
        assertEquals(new ClassFilter(null, null), ClassFilter.parse("   "));
    }

    @Test
    void parse_singleField_setsOnlyThatField() {
        ClassFilter f = ClassFilter.parse("name:PTIT");
        assertEquals("PTIT", f.name());
        assertNull(f.semester());

        f = ClassFilter.parse("semester:20261");
        assertNull(f.name());
        assertEquals("20261", f.semester());
    }

    @Test
    void parse_twoFields_setsBoth() {
        ClassFilter f = ClassFilter.parse("name:PTIT;semester:20261");
        assertEquals("PTIT", f.name());
        assertEquals("20261", f.semester());
    }

    @Test
    void parse_fieldNameIsCaseInsensitive() {
        ClassFilter f = ClassFilter.parse("NAME:PTIT");
        assertEquals("PTIT", f.name());
        assertNull(f.semester());
    }

    @Test
    void parse_valuesAreTrimmed() {
        ClassFilter f = ClassFilter.parse(" name : PTIT ; semester : 20261 ");
        assertEquals("PTIT", f.name());
        assertEquals("20261", f.semester());
    }

    @Test
    void parse_likeWildcardsArePreservedAsLiterals() {
        ClassFilter f = ClassFilter.parse("name:50%");
        assertEquals("50%", f.name());
    }

    @Test
    void parse_backslashIsPreserved() {
        ClassFilter f = ClassFilter.parse("name:back\\slash");
        assertEquals("back\\slash", f.name());
    }

    @Test
    void parse_missingColon_throws() {
        InvalidFilterException e = assertThrows(InvalidFilterException.class,
                () -> ClassFilter.parse("bareword"));
        assertAllowedFields(e.getMessage());
    }

    @Test
    void parse_blankValue_throws() {
        InvalidFilterException e = assertThrows(InvalidFilterException.class,
                () -> ClassFilter.parse("name:"));
        assertEquals("Filter value for 'name' must not be blank", e.getMessage());
    }

    @Test
    void parse_oversizeValue_throws() {
        String longValue = "x".repeat(201);
        InvalidFilterException e = assertThrows(InvalidFilterException.class,
                () -> ClassFilter.parse("name:" + longValue));
        assertEquals("Filter value for 'name' must be ≤ 200 characters", e.getMessage());
    }

    @Test
    void parse_oversizeTotal_throws() {
        String longValue = "x".repeat(496);
        InvalidFilterException e = assertThrows(InvalidFilterException.class,
                () -> ClassFilter.parse("name:" + longValue));
        assertEquals("search must be ≤ 500 characters", e.getMessage());
    }

    @Test
    void parse_tooManyTokens_throws() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 11; i++) {
            sb.append("name:a;");
        }
        InvalidFilterException e = assertThrows(InvalidFilterException.class,
                () -> ClassFilter.parse(sb.toString()));
        assertEquals("search may contain at most 10 filters", e.getMessage());
    }

    @Test
    void parse_unknownField_throws() {
        InvalidFilterException e = assertThrows(InvalidFilterException.class,
                () -> ClassFilter.parse("badfield:x"));
        assertEquals("Unknown filter field: 'badfield'. Allowed fields: name, semester", e.getMessage());
    }

    @Test
    void parse_duplicateField_throws() {
        InvalidFilterException e = assertThrows(InvalidFilterException.class,
                () -> ClassFilter.parse("name:a;name:b"));
        assertEquals("Duplicate filter field: 'name'", e.getMessage());
    }

    private static void assertAllowedFields(String message) {
        String[] allowed = Constant.CourseClassAttr.SEARCHABLE.stream().sorted().toArray(String[]::new);
        String expected = "Allowed fields: " + String.join(", ", allowed);
        assertEquals(expected, message.substring(message.indexOf("Allowed fields:")));
    }
}
