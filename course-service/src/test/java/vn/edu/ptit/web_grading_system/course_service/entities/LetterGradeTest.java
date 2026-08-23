package vn.edu.ptit.web_grading_system.course_service.entities;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LetterGradeTest {

    private static Stream<Object[]> boundaries() {
        return Stream.of(
                new Object[]{"10", "A+"}, new Object[]{"9.0", "A+"}, new Object[]{"8.99", "A"},
                new Object[]{"8.5", "A"}, new Object[]{"8.49", "B+"}, new Object[]{"8.0", "B+"},
                new Object[]{"7.99", "B"}, new Object[]{"7.0", "B"}, new Object[]{"6.9", "C+"},
                new Object[]{"6.5", "C+"}, new Object[]{"6.49", "C"}, new Object[]{"5.5", "C"},
                new Object[]{"5.49", "D+"}, new Object[]{"5.0", "D+"}, new Object[]{"4.99", "D"},
                new Object[]{"4.0", "D"}, new Object[]{"3.99", "F"}, new Object[]{"0", "F"});
    }

    @Test
    void fromTotal_mapsAllTableBoundaries() {
        boundaries().forEach(b -> {
            LetterGrade grade = LetterGrade.fromTotal(new BigDecimal((String) b[0]));
            assertEquals(b[1], grade.getDisplay(), "total " + b[0]);
        });
    }

    @Test
    void gpaValues_matchConversionTable() {
        assertEquals(new BigDecimal("4.0"), LetterGrade.A_PLUS.getGpa());
        assertEquals(new BigDecimal("3.7"), LetterGrade.A.getGpa());
        assertEquals(new BigDecimal("3.5"), LetterGrade.B_PLUS.getGpa());
        assertEquals(new BigDecimal("1.0"), LetterGrade.D.getGpa());
        assertEquals(BigDecimal.ZERO, LetterGrade.F.getGpa());
    }
}