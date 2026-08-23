package vn.edu.ptit.web_grading_system.course_service.entities;

import java.math.BigDecimal;

/**
 * PTIT grading conversion table. Inclusive lower bound; upper bound is the next band's min.
 */
public enum LetterGrade {
    A_PLUS("A+", new BigDecimal("9.0"), new BigDecimal("4.0")),
    A("A", new BigDecimal("8.5"), new BigDecimal("3.7")),
    B_PLUS("B+", new BigDecimal("8.0"), new BigDecimal("3.5")),
    B("B", new BigDecimal("7.0"), new BigDecimal("3.0")),
    C_PLUS("C+", new BigDecimal("6.5"), new BigDecimal("2.5")),
    C("C", new BigDecimal("5.5"), new BigDecimal("2.0")),
    D_PLUS("D+", new BigDecimal("5.0"), new BigDecimal("1.5")),
    D("D", new BigDecimal("4.0"), new BigDecimal("1.0")),
    F("F", BigDecimal.ZERO, BigDecimal.ZERO);

    private final String display;
    private final BigDecimal minTotal;
    private final BigDecimal gpa;

    LetterGrade(String display, BigDecimal minTotal, BigDecimal gpa) {
        this.display = display;
        this.minTotal = minTotal;
        this.gpa = gpa;
    }

    public String getDisplay() {
        return display;
    }

    public BigDecimal getGpa() {
        return gpa;
    }

    /**
     * Table lookup on the rounded total. Pass rules (total >= 4, all sub-scores > 0)
     * are enforced by the caller, which maps failing cases directly to F.
     */
    public static LetterGrade fromTotal(BigDecimal total) {
        for (LetterGrade g : values()) {
            if (g == F || total.compareTo(g.minTotal) >= 0) {
                return g;
            }
        }
        return F;
    }
}