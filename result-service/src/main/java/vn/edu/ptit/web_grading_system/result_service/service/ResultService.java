package vn.edu.ptit.web_grading_system.result_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.result_service.dto.request.CreateResultRequest;
import vn.edu.ptit.web_grading_system.result_service.dto.response.AssignmentResultGroupResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.ResultResponse;
import vn.edu.ptit.web_grading_system.result_service.dto.response.StepResultResponse;
import vn.edu.ptit.web_grading_system.result_service.entity.Result;
import vn.edu.ptit.web_grading_system.result_service.entity.ResultStatus;
import vn.edu.ptit.web_grading_system.result_service.entity.StepType;
import vn.edu.ptit.web_grading_system.result_service.entity.StepResult;
import vn.edu.ptit.web_grading_system.result_service.repository.ResultRepository;
import vn.edu.ptit.web_grading_system.result_service.repository.StepResultRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResultService {

    private final ResultRepository resultRepository;
    private final StepResultRepository stepResultRepository;

    /**
     * Assignment exercise score = weight-weighted average of per-plan scores
     * (weighted by test_plans.weight, carried into results.plan_weight).
     * Only plans with a latest result contribute; unsubmitted plans are ignored
     * (live partial score). Null when the student has no results.
     */
    public BigDecimal weightedScoreByPlan(List<UUID> assignmentIds, UUID studentId) {
        return weightedAverage(
                resultRepository.findByAssignmentIdInAndStudentIdAndLatestTrue(assignmentIds, studentId));
    }

    /**
     * Single source of the weight-weighted formula: {@link #weightedScoreByPlan} and
     * {@link #getByAssignment} both call it, so the per-student exercise score in the
     * transcript and the one in the lecturer's class-wide view can never drift apart.
     * Null when there is nothing to average.
     */
    private static BigDecimal weightedAverage(List<Result> results) {
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

    /**
     * Latest rows of one assignment grouped per student, for the lecturer's grading view
     * (course-service owns the "may this lecturer see this assignment" decision and calls
     * this only after its own owner check).
     *
     * @param studentUserId restricts to one student; null returns the whole class
     * @param includeSteps  false keeps the class-wide read on ONE query — a class of N
     *                      students × M plans would otherwise be N×M round trips. Steps
     *                      are then batched into a single query when they are requested.
     */
    @Transactional(readOnly = true)
    public List<AssignmentResultGroupResponse> getByAssignment(
            UUID assignmentId, UUID studentUserId, boolean includeSteps) {
        List<Result> rows = resultRepository.findByAssignmentIdAndLatestTrue(assignmentId);
        if (studentUserId != null) {
            rows = rows.stream()
                    .filter(r -> studentUserId.equals(r.getStudentId()))
                    .toList();
        }
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<Result>> byStudent = new LinkedHashMap<>();
        for (Result row : rows) {
            byStudent.computeIfAbsent(row.getStudentId(), key -> new ArrayList<>()).add(row);
        }
        Map<UUID, List<StepResult>> stepsByResult = includeSteps
                ? stepResultRepository.findByResultIdIn(rows.stream().map(Result::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(StepResult::getResultId))
                : Map.of();

        List<AssignmentResultGroupResponse> groups = new ArrayList<>();
        byStudent.forEach((studentId, plans) -> groups.add(AssignmentResultGroupResponse.builder()
                .studentUserId(studentId)
                .exerciseScore(weightedAverage(plans))
                .results(plans.stream()
                        .map(result -> toResponse(result,
                                includeSteps
                                        ? stepsByResult.getOrDefault(result.getId(), List.of())
                                        : null))
                        .toList())
                .build()));
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
                .weight(step.getWeight())
                .score(step.getScore())
                .actualValue(step.getActualValue())
                .expectedValue(step.getExpectedValue())
                .errorMessage(step.getErrorMessage())
                .durationMs(step.getDurationMs())
                .build();
    }

    /**
     * Persists one grading report from executor-service. Demotes the previous
     * latest row for (student, assignment, plan) so reads keep working, then
     * inserts the new result plus its step rows. Returns the new result id.
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
        List<Result> previous = request.getPlanId() == null
                ? resultRepository.findByStudentIdAndAssignmentIdAndPlanIdIsNullAndLatestTrue(
                        request.getStudentId(), request.getAssignmentId())
                : resultRepository.findByStudentIdAndAssignmentIdAndPlanIdAndLatestTrue(
                        request.getStudentId(), request.getAssignmentId(), request.getPlanId());
        for (Result old : previous) {
            old.setLatest(false);
        }
        resultRepository.saveAll(previous);

        Result result = resultRepository.save(Result.builder()
                .submissionId(request.getSubmissionId())
                .assignmentId(request.getAssignmentId())
                .studentId(request.getStudentId())
                .planId(request.getPlanId())
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
                        .weight(item.getWeight() != null ? item.getWeight() : 1)
                        .score(item.getScore() != null ? item.getScore() : BigDecimal.ZERO)
                        .actualValue(item.getActualValue())
                        .expectedValue(item.getExpectedValue())
                        .errorMessage(item.getErrorMessage())
                        .durationMs(item.getDurationMs())
                        .build());
            }
        }
        return result.getId();
    }
}