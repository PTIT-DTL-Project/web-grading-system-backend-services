package vn.edu.ptit.web_grading_system.executor_service.service;

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
    void skippedSteps_excludedFromBothSides() {
        List<ScoreCalculator.StepScore> steps = List.of(
                new ScoreCalculator.StepScore(true, false, 1),
                new ScoreCalculator.StepScore(false, true, 9));
        assertEquals(new BigDecimal("10.00"), ScoreCalculator.score(steps, MAX));
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
    void repeatingDecimal_roundsHalfUp() {
        List<ScoreCalculator.StepScore> steps = List.of(
                new ScoreCalculator.StepScore(true, false, 1),
                new ScoreCalculator.StepScore(false, false, 1),
                new ScoreCalculator.StepScore(false, false, 1));
        assertEquals(new BigDecimal("3.33"), ScoreCalculator.score(steps, MAX));
    }
}
