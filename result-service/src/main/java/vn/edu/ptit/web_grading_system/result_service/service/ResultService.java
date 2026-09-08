package vn.edu.ptit.web_grading_system.result_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.edu.ptit.web_grading_system.result_service.entities.Result;
import vn.edu.ptit.web_grading_system.result_service.repositories.ResultRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResultService {

    private final ResultRepository resultRepository;

    /**
     * Average of (score / max_score * 10) over the student's latest results,
     * one per assignment. Null when the student has no results.
     */
    public BigDecimal averageBand10(List<UUID> assignmentIds, UUID studentId) {
        List<Result> results = resultRepository.findByAssignmentIdInAndStudentIdAndLatestTrue(assignmentIds, studentId);
        if (results.isEmpty()) {
            return null;
        }
        BigDecimal sum = BigDecimal.ZERO;
        for (Result r : results) {
            sum = sum.add(r.getScore().multiply(BigDecimal.TEN).divide(r.getMaxScore(), 4, RoundingMode.HALF_UP));
        }
        return sum.divide(BigDecimal.valueOf(results.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Assignment exercise score = weight-weighted average of per-plan scores
     * (weighted by test_plans.weight, carried into results.plan_weight).
     * Only plans with a latest result contribute; unsubmitted plans are ignored
     * (live partial score). Null when the student has no results.
     */
    public BigDecimal weightedScoreByPlan(List<UUID> assignmentIds, UUID studentId) {
        List<Result> results = resultRepository.findByAssignmentIdInAndStudentIdAndLatestTrue(assignmentIds, studentId);
        if (results.isEmpty()) {
            return null;
        }
        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (Result r : results) {
            int weight = r.getPlanWeight() != null ? r.getPlanWeight() : 1;
            BigDecimal weightBd = BigDecimal.valueOf(weight);
            BigDecimal normalized = r.getScore().multiply(BigDecimal.TEN)
                    .divide(r.getMaxScore(), 4, RoundingMode.HALF_UP);
            weightedSum = weightedSum.add(normalized.multiply(weightBd));
            weightTotal = weightTotal.add(weightBd);
        }
        if (weightTotal.signum() == 0) {
            return null;
        }
        return weightedSum.divide(weightTotal, 2, RoundingMode.HALF_UP);
    }
}