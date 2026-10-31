package vn.edu.ptit.web_grading_system.result_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.result_service.client.CourseServicePlansClient;
import vn.edu.ptit.web_grading_system.result_service.dto.request.CreateResultRequest;
import vn.edu.ptit.web_grading_system.result_service.dto.response.AssignmentResultGroupResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.StepResultResponse;
import vn.edu.ptit.web_grading_system.result_service.entity.Result;
import vn.edu.ptit.web_grading_system.result_service.entity.ResultScope;
import vn.edu.ptit.web_grading_system.result_service.entity.ResultStatus;
import vn.edu.ptit.web_grading_system.result_service.entity.StepType;
import vn.edu.ptit.web_grading_system.result_service.entity.StepResult;
import vn.edu.ptit.web_grading_system.result_service.repository.ResultRepository;
import vn.edu.ptit.web_grading_system.result_service.repository.StepResultRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResultService {

    private final ResultRepository resultRepository;
    private final StepResultRepository stepResultRepository;
    private final CourseServicePlansClient plansClient;

    /** Plan-weight cache: plans change rarely, transcripts read often. */
    private final ConcurrentHashMap<UUID, CachedPlans> planWeightCache = new ConcurrentHashMap<>();
    private static final long PLAN_WEIGHT_TTL_MS = 5 * 60 * 1000;

    private record CachedPlans(Map<UUID, Integer> weights, long atMillis) {
    }

    /** One plan scope inside one assignment (planId null = whole-run scope only). */
    private record PlanKey(UUID assignmentId, UUID planId) {
    }

    /**
     * Assignment exercise score = weight-weighted average of per-plan BEST
     * scores (max over every attempt, not the latest): a student who scored 10
     * then resubmitted a broken zip for 0 keeps the 10. Unsubmitted plans now
     * count as 0 and still add their weight to the denominator, so 4/5
     * submitted @10 with equal weights yields 8.00 instead of 10.00. Null when
     * the student has no results.
     *
     * <p>Review: 2026-10-10, max-per-plan policy (was latest-wins).
     * Review: 2026-10-31, unsubmitted plans score 0 and still count weight.
     */
    public BigDecimal weightedScoreByPlan(List<UUID> assignmentIds, UUID studentId) {
        List<Result> rows =
                resultRepository.findByAssignmentIdInAndStudentId(assignmentIds, studentId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<UUID, List<StepResult>> stepsByResult = stepsByResultIds(rows);
        PlanBests perPlan = perPlanBests(rows, stepsByResult);
        Map<UUID, Map<UUID, Integer>> allPlanWeights =
                coursePlanWeights(new HashSet<>(assignmentIds));
        return maxPerPlanAverage(
                perPlan, allPlanWeights, new HashSet<>(assignmentIds));
    }

    /**
     * Batch exercise scores for multiple students across multiple assignments.
     * Single DB query instead of N individual calls — used by lecturer transcript.
     * Null when a student has no scorable results.
     */
    public Map<UUID, BigDecimal> weightedScoreByPlanBatch(
            List<UUID> assignmentIds, List<UUID> studentIds) {
        if (assignmentIds == null || assignmentIds.isEmpty()
                || studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        List<Result> rows =
                resultRepository.findByAssignmentIdInAndStudentIdIn(assignmentIds, studentIds);
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<StepResult>> stepsByResult = stepsByResultIds(rows);
        Map<UUID, Map<UUID, Integer>> allPlanWeights =
                coursePlanWeights(new HashSet<>(assignmentIds));
        Map<UUID, List<Result>> byStudent = new LinkedHashMap<>();
        for (Result row : rows) {
            byStudent.computeIfAbsent(row.getStudentId(), key -> new ArrayList<>()).add(row);
        }
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        Set<UUID> assignmentIdSet = new HashSet<>(assignmentIds);
        byStudent.forEach((studentId, attempts) -> {
            PlanBests perPlan = perPlanBests(attempts, stepsByResult);
            out.put(studentId, maxPerPlanAverage(perPlan, allPlanWeights, assignmentIdSet));
        });
        return out;
    }

    /**
     * Single source of the weight-weighted formula: {@link #weightedScoreByPlan} and
     * {@link #getByAssignment} both call it, so the per-student exercise score in the
     * transcript and the one in the lecturer's class-wide view use the **same formula**.
     * Null when there is nothing to average.
     *
     * <p>Unsubmitted plans (present in {@code allPlanWeights} but absent from {@code best})
     * count as 0 and still add their weight to the denominator — this is the behavior
     * change from 2026-10-31. When course-service is unreachable, {@code allPlanWeights}
     * is empty and only plans with results are scored (old behavior, carried weights).
     *
     * <p>Attempt-status rule (explicit, 2026-10-10): a FAILED attempt is an infra
     * failure, not a score — a PLAN-scoped one still contributes its stored 0
     * (today's transcript behavior), while a FULL-scoped one is always stepless
     * (executor's fail path posts no items) and contributes nothing. A student
     * whose only attempt is a failed FULL run therefore reads missing, not 0.00.
     * A DONE FULL row without attributable steps is likewise unscorable.
     *
     * @param perPlan        per-plan bests computed from result rows
     * @param allPlanWeights all plan weights per assignment from course-service
     * @param assignmentIds  all assignment IDs in scope (used to enumerate all plan keys)
     */
    private BigDecimal maxPerPlanAverage(
            PlanBests perPlan,
            Map<UUID, Map<UUID, Integer>> allPlanWeights,
            Set<UUID> assignmentIds) {
        Map<PlanKey, PlanBest> best = perPlan.best();
        Map<PlanKey, Integer> carried = perPlan.carried();

        // Không có attempt scorable nào (ví dụ sinh viên chỉ nộp 1 lần FAILED FULL
        // mà executor không post step) → missing, không tính weight dù course-service
        // có trả plan gì. Giữ quy tắc: failed stepless FULL run chỉ có attempt này
        // đọc là missing, không bị ép thành 0.00. Review: 2026-10-31.
        if (best.isEmpty()) {
            return null;
        }

        // Enumerate every plan that should contribute: all plans known to course-service,
        // plus any plan that has a result (fallback when course-service is down).
        Set<PlanKey> allPlanKeys = new LinkedHashSet<>();
        for (UUID assignmentId : assignmentIds) {
            Map<UUID, Integer> weights = allPlanWeights.get(assignmentId);
            if (weights != null) {
                for (UUID planId : weights.keySet()) {
                    allPlanKeys.add(new PlanKey(assignmentId, planId));
                }
            }
        }
        for (PlanKey key : best.keySet()) {
            allPlanKeys.add(key);
        }

        if (allPlanKeys.isEmpty()) {
            return null;
        }

        BigDecimal weightedSum = BigDecimal.ZERO;
        BigDecimal weightTotal = BigDecimal.ZERO;
        for (PlanKey key : allPlanKeys) {
            int weight = allPlanWeights.getOrDefault(key.assignmentId(), Map.of())
                    .getOrDefault(key.planId(), carried.getOrDefault(key, 1));
            BigDecimal weightBd = BigDecimal.valueOf(weight);
            // Unsubmitted plan (no result row) scores 0 but still counts its weight.
            BigDecimal score = best.containsKey(key) ? best.get(key).score() : BigDecimal.ZERO;
            weightedSum = weightedSum.add(score.multiply(weightBd));
            weightTotal = weightTotal.add(weightBd);
        }
        if (weightTotal.signum() == 0) {
            return null;
        }
        return weightedSum.divide(weightTotal, 2, RoundingMode.HALF_UP);
    }

    /** One plan's best score in one assignment, with the attempt row that earned it. */
    private record PlanBest(BigDecimal score, Result row) {
    }

    /** Per-plan bests plus the plan weight carried on the rows (used when course-service is down). */
    private record PlanBests(Map<PlanKey, PlanBest> best, Map<PlanKey, Integer> carried) {
    }

    /**
     * Per-plan bests of one assignment, each paired with the attempt that earned it — the
     * single walk over the FULL/PLAN decomposition. The score formula and the lecturer's
     * displayed rows both read it, so the number and the evidence under it cannot drift.
     *
     * <p>{@code carried} holds the plan weight stored on the row, used only when the real
     * weights are unavailable (see {@link #coursePlanWeights}).
     */
    private PlanBests perPlanBests(List<Result> rows, Map<UUID, List<StepResult>> stepsByResult) {
        Map<PlanKey, PlanBest> best = new LinkedHashMap<>();
        Map<PlanKey, Integer> carried = new LinkedHashMap<>();
        for (Result r : rows) {
            if (r.getScope() == ResultScope.PLAN && r.getPlanId() != null) {
                BigDecimal n = normalized(r.getScore(), r.getMaxScore());
                if (n != null) {
                    PlanKey key = new PlanKey(r.getAssignmentId(), r.getPlanId());
                    best.merge(key, new PlanBest(n, r), ResultService::higher);
                    carried.putIfAbsent(key, r.getPlanWeight() != null ? r.getPlanWeight() : 1);
                }
            } else {
                // FULL (or legacy) row: decompose into per-plan subtotals from
                // its step rows — the row's single score covers every plan.
                for (Map.Entry<UUID, BigDecimal> e : planSubtotals(
                        stepsByResult.getOrDefault(r.getId(), List.of())).entrySet()) {
                    best.merge(new PlanKey(r.getAssignmentId(), e.getKey()),
                            new PlanBest(e.getValue(), r), ResultService::higher);
                }
            }
        }
        return new PlanBests(best, carried);
    }

    /** Keeps the higher-scoring attempt; the earlier one wins a tie. */
    private static PlanBest higher(PlanBest a, PlanBest b) {
        return b.score().compareTo(a.score()) > 0 ? b : a;
    }

    /**
     * The attempt rows the exercise score is made of: for each plan, the row whose score was
     * counted. Latest state is deliberately NOT what the lecturer's grading view shows — a
     * student who scored 10 then resubmitted a broken zip for 5 must not read the 5 next to
     * their 10. Falls back to the latest rows when nothing is scorable (e.g. only a stepless
     * failed FULL run) so the detail never goes blank.
     *
     * <p>Review: 2026-10-17 — reported as "score 10 but detail shows 5"; the view was
     * showing the latest attempt while the score came from the best one.
     */
    private List<Result> gradedRows(List<Result> attempts, Map<UUID, List<StepResult>> stepsByResult) {
        Set<UUID> graded = perPlanBests(attempts, stepsByResult).best().values().stream()
                .map(b -> b.row().getId())
                .collect(Collectors.toSet());
        List<Result> rows = attempts.stream().filter(r -> graded.contains(r.getId())).toList();
        if (!rows.isEmpty()) {
            return rows;
        }
        return attempts.stream().filter(r -> Boolean.TRUE.equals(r.getLatest())).toList();
    }

    /** Score normalized to the 0–10 scale; null when unscorable (never throws). */
    private static BigDecimal normalized(BigDecimal score, BigDecimal maxScore) {
        if (score == null || maxScore == null || maxScore.signum() == 0) {
            return null;
        }
        return score.multiply(BigDecimal.TEN).divide(maxScore, 4, RoundingMode.HALF_UP);
    }

    /**
     * Per-plan subtotals of one run from its step rows, mirroring the
     * executor's {@code ScoreCalculator} exactly (skipped steps excluded from
     * both sides, same rounding) — a FULL row's steps are the only per-plan
     * evidence it leaves. Steps without a planId cannot be attributed and are
     * ignored. Plans with no ran weight contribute nothing.
     */
    private static Map<UUID, BigDecimal> planSubtotals(List<StepResult> steps) {
        Map<UUID, Integer> ran = new LinkedHashMap<>();
        Map<UUID, Integer> passed = new LinkedHashMap<>();
        for (StepResult s : steps) {
            if (s.getPlanId() == null || Boolean.TRUE.equals(s.getSkipped())) {
                continue;
            }
            int w = s.getWeight() != null ? s.getWeight() : 1;
            ran.merge(s.getPlanId(), w, Integer::sum);
            if (Boolean.TRUE.equals(s.getPassed())) {
                passed.merge(s.getPlanId(), w, Integer::sum);
            }
        }
        Map<UUID, BigDecimal> out = new LinkedHashMap<>();
        for (Map.Entry<UUID, Integer> e : ran.entrySet()) {
            if (e.getValue() != null && e.getValue() != 0) {
                out.put(e.getKey(), BigDecimal.TEN.multiply(BigDecimal.valueOf(
                                passed.getOrDefault(e.getKey(), 0)))
                        .divide(BigDecimal.valueOf(e.getValue()), 4, RoundingMode.HALF_UP));
            }
        }
        return out;
    }

    private Map<UUID, List<StepResult>> stepsByResultIds(List<Result> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        return stepResultRepository.findByResultIdIn(rows.stream().map(Result::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(StepResult::getResultId));
    }

    /**
     * Real plan weights from course-service, cached 5 minutes. Any failure
     * degrades to carried weights (never fails a score read because another
     * service is down) — failures are not cached so recovery is immediate.
     */
    private Map<UUID, Map<UUID, Integer>> coursePlanWeights(Set<UUID> assignmentIds) {
        Map<UUID, Map<UUID, Integer>> out = new LinkedHashMap<>();
        long now = System.currentTimeMillis();
        for (UUID assignmentId : assignmentIds) {
            CachedPlans cached = planWeightCache.get(assignmentId);
            if (cached != null && now - cached.atMillis() < PLAN_WEIGHT_TTL_MS) {
                out.put(assignmentId, cached.weights());
                continue;
            }
            try {
                Map<UUID, Integer> weights = new LinkedHashMap<>();
                List<CourseServicePlansClient.PlanWeight> plans = plansClient.plans(assignmentId);
                if (plans != null) {
                    for (CourseServicePlansClient.PlanWeight p : plans) {
                        if (p.id() != null) {
                            weights.put(p.id(), p.weight() != null ? p.weight() : 1);
                        }
                    }
                }
                planWeightCache.put(assignmentId, new CachedPlans(weights, now));
                out.put(assignmentId, weights);
            } catch (Exception e) {
                log.warn("Plan weights unavailable for assignment={}, falling back to carried weights",
                        assignmentId);
            }
        }
        return out;
    }

    /**
     * All-attempt rows of one assignment grouped per student, for the lecturer's
     * grading view (course-service owns the "may this lecturer see this assignment"
     * decision and calls this only after its own owner check). The displayed rows are
     * the attempts the exercise score is made of — the best attempt per plan, not the
     * latest one — so the detail under a student's score always shows that score's
     * evidence (see {@link #gradedRows} and {@link #weightedScoreByPlan}).
     *
     * @param studentUserId restricts to one student; null returns the whole class
     * @param includeSteps  false keeps step rows out of the display payload —
     *                      scoring still reads them in ONE batched query.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResultGroupResponse> getByAssignment(
            UUID assignmentId, UUID studentUserId, boolean includeSteps) {
        List<Result> rows = resultRepository.findByAssignmentId(assignmentId);
        if (studentUserId != null) {
            rows = rows.stream()
                    .filter(r -> studentUserId.equals(r.getStudentId()))
                    .toList();
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<StepResult>> stepsByResult = stepsByResultIds(rows);
        Map<UUID, Map<UUID, Integer>> allPlanWeights = coursePlanWeights(Set.of(assignmentId));
        Map<UUID, List<Result>> byStudent = new LinkedHashMap<>();
        for (Result row : rows) {
            byStudent.computeIfAbsent(row.getStudentId(), key -> new ArrayList<>()).add(row);
        }

        List<AssignmentResultGroupResponse> groups = new ArrayList<>();
        byStudent.forEach((studentId, attempts) -> {
            PlanBests perPlan = perPlanBests(attempts, stepsByResult);
            groups.add(AssignmentResultGroupResponse.builder()
                    .studentUserId(studentId)
                    .exerciseScore(maxPerPlanAverage(perPlan, allPlanWeights, Set.of(assignmentId)))
                    .results(gradedRows(attempts, stepsByResult).stream()
                            .map(result -> toResponse(result,
                                    includeSteps
                                            ? stepsByResult.getOrDefault(result.getId(), List.of())
                                            : null))
                            .toList())
                    .build());
        });
        return groups;
    }

    /**
     * All result rows for one submission (one per graded plan), each with its
     * step rows. Empty when the submission hasn't been graded yet — callers
     * poll this endpoint.
     */
    @Transactional(readOnly = true)
    public List<ResultResponse> getBySubmissionId(UUID submissionId) {
        return resultRepository.findBySubmissionId(submissionId).stream()
                .map(result -> toResponse(result,
                        stepResultRepository.findByResultId(result.getId())))
                .toList();
    }

    /** {@code steps == null} means "not requested", not "no steps" — see {@code includeSteps}. */
    private static ResultResponse toResponse(Result result, List<StepResult> steps) {
        return ResultResponse.builder()
                .id(result.getId())
                .submissionId(result.getSubmissionId())
                .assignmentId(result.getAssignmentId())
                .studentId(result.getStudentId())
                .planId(result.getPlanId())
                .planWeight(result.getPlanWeight())
                .scope(result.getScope() == null ? null : result.getScope().name())
                .score(result.getScore())
                .maxScore(result.getMaxScore())
                .status(result.getStatus() == null ? null : result.getStatus().name())
                .summaryLog(result.getSummaryLog())
                .latest(result.getLatest())
                .startedAt(result.getStartedAt())
                .completedAt(result.getCompletedAt())
                .steps(steps == null ? null
                        : steps.stream().map(ResultService::toStepResponse).toList())
                .build();
    }

    private static StepResultResponse toStepResponse(StepResult step) {
        return StepResultResponse.builder()
                .id(step.getId())
                .planId(step.getPlanId())
                .stepId(step.getStepId())
                .stepOrder(step.getStepOrder())
                .stepName(step.getStepName())
                .stepType(step.getStepType() == null ? null : step.getStepType().name())
                .passed(step.getPassed())
                .skipped(step.getSkipped())
                .weight(step.getWeight())
                .score(step.getScore())
                .actualValue(step.getActualValue())
                .expectedValue(step.getExpectedValue())
                .errorMessage(step.getErrorMessage())
                .durationMs(step.getDurationMs())
                .assertionDetails(step.getAssertionDetails())
                .extractedVars(step.getExtractedVars())
                .requestUrl(step.getRequestUrl())
                .requestBody(step.getRequestBody())
                .responseStatus(step.getResponseStatus())
                .dbQueryCount(step.getDbQueryCount())
                .dbQueryLogs(step.getDbQueryLogs())
                .stackTrace(step.getStackTrace())
                .peakMemoryMb(step.getPeakMemoryMb())
                .build();
    }

    /**
     * Persists one grading report from executor-service. Demotes the previous
     * latest row(s) so reads keep working, then inserts the new result plus
     * its step rows. Returns the new result id.
     *
     * <p>A whole-assignment ({@code FULL}) run supersedes every per-plan
     * latest row of the same (student, assignment) — its single row already
     * aggregates all plans, so keeping an older per-plan latest would
     * double-count it in the average (2026-10-10: exercise 5.00 for a 10).
     * A single-plan run demotes only its own plan scope, leaving sibling
     * plans untouched.
     */
    @Transactional
    public UUID createResult(CreateResultRequest request) {
        if (request.getSubmissionId() == null || request.getAssignmentId() == null
                || request.getStudentId() == null || request.getScore() == null) {
            throw new IllegalArgumentException(
                    "submissionId, assignmentId, studentId and score are required");
        }
        ResultStatus status;
        try {
            status = request.getStatus() == null
                    ? ResultStatus.DONE : ResultStatus.valueOf(request.getStatus());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown result status: " + request.getStatus());
        }
        ResultScope scope = request.getPlanId() == null ? ResultScope.FULL : ResultScope.PLAN;
        List<Result> previous = scope == ResultScope.FULL
                ? resultRepository.findByStudentIdAndAssignmentIdAndLatestTrue(
                        request.getStudentId(), request.getAssignmentId())
                : resultRepository.findByStudentIdAndAssignmentIdAndPlanIdAndLatestTrue(
                        request.getStudentId(), request.getAssignmentId(), request.getPlanId());
        for (Result old : previous) {
            old.setLatest(false);
        }
        // Flush the demotion BEFORE inserting: Hibernate flushes entity INSERTs
        // before UPDATEs within one transaction, so without this the new
        // latest=true row hits idx_results_latest while the old one is still
        // true → 409 on every same-scope regrade (executor then drops the
        // report silently as a 4xx). Review: 2026-10-12, Postman capture found it.
        resultRepository.saveAllAndFlush(previous);

        Result result = resultRepository.save(Result.builder()
                .submissionId(request.getSubmissionId())
                .assignmentId(request.getAssignmentId())
                .studentId(request.getStudentId())
                .planId(request.getPlanId())
                .scope(scope)
                .planWeight(request.getPlanWeight())
                .score(request.getScore())
                .maxScore(request.getMaxScore() != null
                        ? request.getMaxScore() : new BigDecimal("10.00"))
                .status(status)
                .summaryLog(request.getSummaryLog())
                .latest(true)
                .completedAt(java.time.OffsetDateTime.now())
                .build());
        if (request.getStepResults() != null) {
            for (CreateResultRequest.StepResultItem item : request.getStepResults()) {
                StepType stepType;
                try {
                    stepType = item.getStepType() == null
                            ? StepType.HTTP_REQUEST : StepType.valueOf(item.getStepType());
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Unknown step type: " + item.getStepType());
                }
                stepResultRepository.save(StepResult.builder()
                        .resultId(result.getId())
                        .planId(item.getPlanId())
                        .stepId(item.getStepId())
                        .stepOrder(item.getStepOrder())
                        .stepName(item.getStepName())
                        .stepType(stepType)
                        .passed(Boolean.TRUE.equals(item.getPassed()))
                        .skipped(Boolean.TRUE.equals(item.getSkipped()))
                        .weight(item.getWeight() != null ? item.getWeight() : 1)
                        .score(item.getScore() != null ? item.getScore() : BigDecimal.ZERO)
                        .actualValue(item.getActualValue())
                        .expectedValue(item.getExpectedValue())
                        .errorMessage(item.getErrorMessage())
                        .durationMs(item.getDurationMs())
                        // Review: 2026-10-11, phase A2 — the 5 evidence fields
                        // arrive already redacted: the executor's stepItem is
                        // the trust boundary (separate Maven projects, so the
                        // redactor class itself cannot be shared — see A4 doc).
                        .assertionDetails(item.getAssertionDetails())
                        .extractedVars(item.getExtractedVars())
                        .requestUrl(item.getRequestUrl())
                        .requestBody(item.getRequestBody())
                        .responseStatus(item.getResponseStatus())
                        .dbQueryCount(item.getDbQueryCount())
                        .dbQueryLogs(item.getDbQueryLogs())
                        .stackTrace(item.getStackTrace())
                        .peakMemoryMb(item.getPeakMemoryMb())
                        .build());
            }
        }
        return result.getId();
    }
}