package vn.edu.ptit.web_grading_system.executor_service.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Weight-weighted step scoring. SKIPPED steps count on neither side; an empty
 * run scores zero; infra failures are forced to zero by the caller.
 */
public final class ScoreCalculator {

    private ScoreCalculator() {
    }

    public record StepScore(boolean passed, boolean skipped, int weight) {
    }

    public static BigDecimal score(List<StepScore> steps, BigDecimal maxScore) {
        int ranWeight = 0;
        int passedWeight = 0;
        for (StepScore step : steps) {
            if (step.skipped()) {
                continue;
            }
            ranWeight += step.weight();
            if (step.passed()) {
                passedWeight += step.weight();
            }
        }
        if (ranWeight == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return maxScore.multiply(BigDecimal.valueOf(passedWeight))
                .divide(BigDecimal.valueOf(ranWeight), 2, RoundingMode.HALF_UP);
    }
}
