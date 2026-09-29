package vn.edu.ptit.web_grading_system.course_service.spec.filter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LikePatternsTest {

    @Test
    void escapeContains_backslashIsEscapedFirst() {
        assertEquals("%back\\\\slash\\%off%", LikePatterns.escapeContains("back\\slash%off"));
    }

    @Test
    void escapeContains_percentIsEscaped() {
        assertEquals("%50\\%%", LikePatterns.escapeContains("50%"));
    }

    @Test
    void escapeContains_underscoreIsEscaped() {
        assertEquals("%a\\_b%", LikePatterns.escapeContains("a_b"));
    }

    @Test
    void escapeContains_lowercasesInput() {
        assertEquals("%ptit%", LikePatterns.escapeContains("PTIT"));
    }
}
