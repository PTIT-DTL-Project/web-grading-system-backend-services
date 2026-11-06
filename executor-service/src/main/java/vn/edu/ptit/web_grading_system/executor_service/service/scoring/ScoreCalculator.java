package vn.edu.ptit.web_grading_system.executor_service.service.scoring;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Weight-weighted step scoring. SKIPPED steps (cascaded after a required-step
 * failure) count their weight in the denominator but contribute 0 to passed —
 * a skipped step drags the score down. This aligns with result-service
 * {@code planSubtotals()} aggregation. An empty run scores zero; infra failures
 * are forced to zero by the caller.
 * Review: 2026-11-06 — changed from "skipped excluded from both sides" to
 * "skipped counts in denominator, 0 passed" per product requirement (submission
 * score must reflect skipped steps).
 */
public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    public record StepScore(boolean passed, boolean skipped, int weight) {
    }

    public static BigDecimal score(List<StepScore> steps, BigDecimal maxScore) {
        int totalWeight = 0;
        int passedWeight = 0;
        for (StepScore step : steps) {
            totalWeight += step.weight();
            if (!step.skipped() && step.passed()) {
                passedWeight += step.weight();
            }
        }
        if (totalWeight == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return maxScore.multiply(BigDecimal.valueOf(passedWeight))
                .divide(BigDecimal.valueOf(totalWeight), 2, RoundingMode.HALF_UP);
    }
}
