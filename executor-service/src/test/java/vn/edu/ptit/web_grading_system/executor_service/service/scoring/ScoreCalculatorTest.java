package vn.edu.ptit.web_grading_system.executor_service.service.scoring;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreCalculatorTest {

    private static final BigDecimal MAX = new BigDecimal("10.00");

    @Test
    void allPassed_fullScore() {
        List<ScoreCalculator.StepScore> steps = List.of(
                new ScoreCalculator.StepScore(true, false, 1),
                new ScoreCalculator.StepScore(true, false, 2));
        assertEquals(new BigDecimal("10.00"), ScoreCalculator.score(steps, MAX));
    }

    @Test
    void partialWeights_proportionalScore() {
        List<ScoreCalculator.StepScore> steps = List.of(
                new ScoreCalculator.StepScore(true, false, 1),
                new ScoreCalculator.StepScore(false, false, 3));
        assertEquals(new BigDecimal("2.50"), ScoreCalculator.score(steps, MAX));
    }

    @Test
    void skippedSteps_countInDenominator_dragScoreDown() {
        // 1 passed (weight 1) + 1 skipped (weight 9) => 1/10 * 10 = 1.00.
        // Review: 2026-11-06 — skipped steps count weight in denominator, contribute 0 passed.
        List<ScoreCalculator.StepScore> steps = List.of(
                new ScoreCalculator.StepScore(true, false, 1),
                new ScoreCalculator.StepScore(false, true, 9));
        assertEquals(new BigDecimal("1.00"), ScoreCalculator.score(steps, MAX));
    }

    @Test
    void emptyRun_scoresZero() {
        assertEquals(new BigDecimal("0.00"), ScoreCalculator.score(List.of(), MAX));
    }

    @Test
    void allSkipped_scoresZero() {
        List<ScoreCalculator.StepScore> steps = List.of(
                new ScoreCalculator.StepScore(false, true, 1));
        assertEquals(new BigDecimal("0.00"), ScoreCalculator.score(steps, MAX));
    }

    @Test
    void mixedPassedFailedSkipped_proportionalWithSkippedInDenominator() {
        // passed(w2) + failed(w3, not skipped) + skipped(w5) => 2/10 * 10 = 2.00
        List<ScoreCalculator.StepScore> steps = List.of(
                new ScoreCalculator.StepScore(true, false, 2),
                new ScoreCalculator.StepScore(false, false, 3),
                new ScoreCalculator.StepScore(false, true, 5));
        assertEquals(new BigDecimal("2.00"), ScoreCalculator.score(steps, MAX));
    }

    @Test
    void repeatingDecimal_roundsHalfUp() {
        List<ScoreCalculator.StepScore> steps = List.of(
                new ScoreCalculator.StepScore(true, false, 1),
                new ScoreCalculator.StepScore(false, false, 1),
                new ScoreCalculator.StepScore(false, false, 1));
        assertEquals(new BigDecimal("3.33"), ScoreCalculator.score(steps, MAX));
    }
}
