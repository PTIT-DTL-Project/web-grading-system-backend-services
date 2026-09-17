package vn.edu.ptit.web_grading_system.executor_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import vn.edu.ptit.web_grading_system.executor_service.Constant;
import vn.edu.ptit.web_grading_system.executor_service.client.CourseInternalClient;
import vn.edu.ptit.web_grading_system.executor_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.executor_service.client.SubmissionStatusClient;
import vn.edu.ptit.web_grading_system.executor_service.config.ExecutorProperties;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.AssignmentGradingConfigDto;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.InternalPlanDto;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.InternalStepDto;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingLog;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingLogLevel;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStepStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingLogRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingStepResultRepository;
import vn.edu.ptit.web_grading_system.executor_service.service.step.HttpStepExecutor;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepExecutor;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepRegistry;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs one grading job end to end: fetch config, download artifact, boot the
 * student app, execute steps, score, report. Single-job gate — one DinD daemon
 * and tight pod resources mean parallel compose builds would contend.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GradingOrchestrator
{
    private static final BigDecimal MAX_SCORE = BigDecimal.TEN;

    private final GradingJobRepository gradingJobRepository;
    private final GradingStepResultRepository stepResultRepository;
    private final GradingLogRepository gradingLogRepository;
    private final CourseInternalClient courseInternalClient;
    private final SubmissionStatusClient submissionStatusClient;
    private final ResultServiceClient resultServiceClient;
    private final ArtifactService artifactService;
    private final PortAllocator portAllocator;
    private final DockerComposeRunner composeRunner;
    private final StepRegistry stepRegistry;
    private final ObjectMapper objectMapper;
    private final ExecutorProperties executorProperties;
    private final SagaTracker sagaTracker;

    @Async("gradingTaskExecutor")
    public void gradeAsync(UUID jobId, UUID submissionId, UUID assignmentId, UUID studentId,
            UUID planId, String rustfsPath, String traceId)
    {
        try
        {
            grade(jobId, submissionId, assignmentId, studentId, planId, rustfsPath, traceId);
        }
        catch (Exception e)
        {
            log.warn("Grading failed before start: job={}", jobId, e);
        }
    }

    void grade(UUID jobId, UUID submissionId, UUID assignmentId, UUID studentId,
            UUID planId, String rustfsPath, String traceId)
    {
        GradingJob job = gradingJobRepository.findById(jobId).orElse(null);
        if (job == null)
        {
            log.warn("Grading job not found: id={} (traceId={})", jobId, traceId);
            return;
        }
        patchSubmission(submissionId, Constant.GradingLog.STEP_GRADING);
        job.setStatus(GradingJobStatus.FETCHING);
        job.setStartedAt(OffsetDateTime.now());
        gradingJobRepository.save(job);
        writeLog(job.getId(), submissionId, GradingLogLevel.INFO, Constant.Message.FETCHING_PREFIX + "Fetching grading config and artifact");
        UUID sagaId = sagaTracker.begin(job.getId());

        AssignmentGradingConfigDto config;
        List<InternalPlanDto> plans;
        UUID fetchRow = sagaTracker.step(sagaId, Constant.Saga.FETCH_CONFIG, planId, null);
        try
        {
            config = courseInternalClient.gradingConfig(assignmentId);
            plans = new ArrayList<>(courseInternalClient.plans(assignmentId));
            sagaTracker.finishStep(fetchRow, SagaStepStatus.DONE, null);
        }
        catch (Exception e)
        {
            sagaTracker.finishStep(fetchRow, SagaStepStatus.FAILED, safeMessage(e));
            fail(job, submissionId, assignmentId, studentId, planId, sagaId, 1,
                    Constant.Message.FAILED_FETCH_CONFIG + safeMessage(e));
            return;
        }
        if (planId != null)
        {
            plans = new ArrayList<>(plans.stream()
                    .filter(p -> planId.equals(p.getId())).toList());
        }
        plans.sort(Comparator.comparing(InternalPlanDto::getSequenceOrder,
                Comparator.nullsLast(Integer::compareTo)));
        if (plans.isEmpty())
        {
            fail(job, submissionId, assignmentId, studentId, planId, sagaId, 1,
                    "Assignment has no test plans");
            return;
        }

        job.setStatus(GradingJobStatus.BUILDING);
        gradingJobRepository.save(job);
        writeLog(job.getId(), submissionId, GradingLogLevel.INFO, Constant.Message.BUILDING_PREFIX + "Downloading submission and booting app");
        Path workDir;
        UUID downloadRow = sagaTracker.step(sagaId, Constant.Saga.DOWNLOAD_ARTIFACT, planId, null);
        try
        {
            workDir = artifactService.fetchWorkDir(submissionId, rustfsPath);
            sagaTracker.finishStep(downloadRow, SagaStepStatus.DONE, null);
        }
        catch (Exception e)
        {
            sagaTracker.finishStep(downloadRow, SagaStepStatus.FAILED, safeMessage(e));
            fail(job, submissionId, assignmentId, studentId, planId, sagaId, 1,
                    Constant.Message.FAILED_DOWNLOAD_SUBMISSION + safeMessage(e));
            return;
        }
        int port = portAllocator.claim();
        long executionTimeoutMs = nz(config.getExecutionTimeoutMs(),
                executorProperties.container().maxExecutionTimeMs());
        long startupTimeoutMs = nz(config.getStartupTimeoutMs(),
                executorProperties.container().startupTimeoutMs());
        UUID bootRow = sagaTracker.step(sagaId, Constant.Saga.BOOT_COMPOSE, planId, null);
        boolean booted = false;
        try
        {
            DockerComposePatcher.EffectiveCompose effective = DockerComposePatcher.writeEffectiveCompose(
                    workDir, config.getGradingStrategy(), config.getDockerComposeTemplate(),
                    port, nz(config.getDockerComposePort(), 8080),
                    config.getMaxCpu(), config.getMaxMemoryMb());
            try (DockerComposeRunner.RunningCompose running =
                    composeRunner.boot(effective, startupTimeoutMs))
            {
                booted = true;
                sagaTracker.finishStep(bootRow, SagaStepStatus.DONE, null);
                UUID runRow = sagaTracker.step(sagaId, Constant.Saga.RUN_STEPS, planId, null);
                try
                {
                    runSteps(job, submissionId, assignmentId, studentId, planId, sagaId, plans,
                            running.port(), executionTimeoutMs);
                    sagaTracker.finishStep(runRow, SagaStepStatus.DONE, null);
                }
                catch (Exception inner)
                {
                    sagaTracker.finishStep(runRow, SagaStepStatus.FAILED,
                            safeMessage(inner));
                    throw inner;
                }
            }
        }
        catch (Exception e)
        {
            if (!booted)
            {
                sagaTracker.finishStep(bootRow, SagaStepStatus.FAILED, safeMessage(e));
            }
            fail(job, submissionId, assignmentId, studentId, planId, sagaId, 1,
                    Constant.Message.FAILED_GRADING_INFRA + safeMessage(e));
        }
        finally
        {
            portAllocator.release(port);
            if (workDir != null)
            {
                cleanupWorkDir(workDir);
            }
        }
    }

    private void runSteps(GradingJob job, UUID submissionId, UUID assignmentId, UUID studentId,
            UUID planId, UUID sagaId, List<InternalPlanDto> plans, int appPort, long executionTimeoutMs)
    {
        job.setStatus(GradingJobStatus.RUNNING);
        gradingJobRepository.save(job);
        writeLog(job.getId(), submissionId, GradingLogLevel.INFO, Constant.Message.RUNNING_PREFIX + plans.size() + Constant.Message.PLAN_SUFFIX);
        VariableContext vars = new VariableContext();
        vars.put(Constant.VariableContext.APP_PORT, appPort);
        vars.put(Constant.VariableContext.SUBMISSION_ID, submissionId.toString());
        vars.put(Constant.VariableContext.ASSIGNMENT_ID, assignmentId.toString());
        vars.put(Constant.VariableContext.STUDENT_ID, studentId.toString());
        long deadline = System.currentTimeMillis() + executionTimeoutMs;
        List<ScoreCalculator.StepScore> outcomes = new ArrayList<>();
        List<ResultServiceClient.StepResultItem> items = new ArrayList<>();
        boolean timedOut = false;
        outer:
        for (InternalPlanDto plan : plans)
        {
            List<InternalStepDto> steps = plan.getSteps() == null
                    ? List.of()
                    : plan.getSteps().stream()
                            .sorted(Comparator.comparing(InternalStepDto::getStepOrder,
                                    Comparator.nullsLast(Integer::compareTo)))
                            .toList();
            steps = autoInjectExtracts(steps);
            boolean planStopped = false;
            for (InternalStepDto step : steps)
            {
                if (System.currentTimeMillis() > deadline)
                {
                    timedOut = true;
                    break outer;
                }
                if (planStopped)
                {
                    skipStep(job, plan, step, sagaId, outcomes, items);
                    continue;
                }
                UUID stepRow = sagaTracker.step(sagaId, Constant.Saga.STEP_PREFIX + step.getName(),
                        plan.getId(), step.getId());
                GradingStepResult result = runStep(job, plan, step, vars);
                boolean passed = result.getStatus() == StepResultStatus.PASSED;
                sagaTracker.finishStep(stepRow,
                        passed ? SagaStepStatus.DONE : SagaStepStatus.FAILED,
                        passed ? null : result.getErrorMessage());
                outcomes.add(new ScoreCalculator.StepScore(
                        passed, false, nz(step.getWeight(), 1)));
                items.add(stepItem(plan, step, result, passed, result.getErrorMessage()));
                if ((result.getStatus() == StepResultStatus.FAILED
                        || result.getStatus() == StepResultStatus.ERROR)
                        && Boolean.TRUE.equals(step.getRequired()))
                {
                    planStopped = true;
                    writeLog(job.getId(), submissionId, GradingLogLevel.WARN,
                            Constant.Message.REQUIRED_STEP_FAILED + step.getName());
                }
            }
        }
        if (timedOut)
        {
            fail(job, submissionId, assignmentId, studentId, planId, sagaId,
                    weightOf(plans, planId), Constant.Message.EXCEEDED_TIMEOUT);
            return;
        }
        UUID scoreRow = sagaTracker.step(sagaId, Constant.Saga.SCORE_REPORT, planId, null);
        BigDecimal score = ScoreCalculator.score(outcomes, MAX_SCORE);
        String summary = buildSummary(outcomes, items, score);
        job.setStatus(GradingJobStatus.DONE);
        job.setCompletedAt(OffsetDateTime.now());
        gradingJobRepository.save(job);
        writeLog(job.getId(), submissionId, GradingLogLevel.INFO, summary);
        log.info("Grading done: job={} {}", job.getId(), summary);
        postResult(job, submissionId, assignmentId, studentId, planId, plans, score, summary, items,
                GradingJobStatus.DONE);
        patchSubmission(submissionId, Constant.GradingLog.STEP_DONE);
        sagaTracker.finishStep(scoreRow, SagaStepStatus.DONE, null);
        sagaTracker.finish(sagaId, SagaStatus.DONE);
    }

    private void skipStep(GradingJob job, InternalPlanDto plan, InternalStepDto step,
            UUID sagaId, List<ScoreCalculator.StepScore> outcomes,
            List<ResultServiceClient.StepResultItem> items)
    {
        UUID skipRow = sagaTracker.step(sagaId, Constant.Saga.STEP_PREFIX + step.getName(),
                plan.getId(), step.getId());
        sagaTracker.finishStep(skipRow, SagaStepStatus.SKIPPED,
                Constant.Message.SKIPPED_REQUIRED_FAILED);
        persist(job, plan, step, StepResultStatus.SKIPPED, Constant.Message.SKIPPED_REQUIRED_FAILED);
        outcomes.add(new ScoreCalculator.StepScore(false, true, nz(step.getWeight(), 1)));
        items.add(stepItem(plan, step, null, false, Constant.Message.SKIPPED_REQUIRED_FAILED));
    }

    private GradingStepResult runStep(GradingJob job, InternalPlanDto plan, InternalStepDto step,
            VariableContext vars)
    {
        JsonNode config;
        try
        {
            config = objectMapper.readTree(step.getConfig() == null ? "{}" : step.getConfig());
        }
        catch (Exception e)
        {
            return persist(job, plan, step, StepResultStatus.ERROR,
                    Constant.Message.INVALID_STEP_CONFIG + safeMessage(e));
        }
        StepExecutor executor;
        try
        {
            executor = stepRegistry.of(step.getStepType());
        }
        catch (IllegalArgumentException unknown)
        {
            return persist(job, plan, step, StepResultStatus.FAILED,
                    Constant.Message.UNKNOWN_STEP_TYPE + step.getStepType());
        }
        try
        {
            GradingStepResult result = executor.execute(new HttpStepExecutor.StepContext(
                    job.getId(), plan.getId(), step.getId(), step.getStepOrder(),
                    step.getName(), config, vars, step.getTimeoutMs()));
            return stepResultRepository.save(result);
        }
        catch (Exception e)
        {
            return persist(job, plan, step, StepResultStatus.ERROR, safeMessage(e));
        }
    }

    private List<InternalStepDto> autoInjectExtracts(List<InternalStepDto> steps)
    {
        Pattern varPattern = Pattern.compile("\\$\\{([^}]+)}");
        List<InternalStepDto> result = new ArrayList<>(steps);
        for (int i = result.size() - 1; i >= 1; i--)
        {
            try
            {
                JsonNode config = objectMapper.readTree(result.get(i).getConfig() == null ? "{}" : result.get(i).getConfig());
                Set<String> neededVars = new HashSet<>();
                collectVarRefs(config, varPattern, neededVars);
                if (!neededVars.isEmpty())
                {
                    InternalStepDto prevStep = result.get(i - 1);
                    JsonNode prevConfig = objectMapper.readTree(prevStep.getConfig() == null ? "{}" : prevStep.getConfig());
                    ObjectNode enhanced = (ObjectNode) prevConfig;
                    ArrayNode extractArray = enhanced.withArray(Constant.HttpStep.EXTRACT);
                    for (String varName : neededVars)
                    {
                        boolean exists = false;
                        if (enhanced.has(Constant.HttpStep.EXTRACT) && enhanced.get(Constant.HttpStep.EXTRACT).isArray())
                        {
                            for (JsonNode ex : enhanced.get(Constant.HttpStep.EXTRACT))
                            {
                                if (ex.path(Constant.HttpStep.NAME).asString().equals(varName))
                                {
                                    exists = true;
                                    break;
                                }
                            }
                        }
                        if (!exists)
                        {
                            ObjectNode entry = objectMapper.createObjectNode();
                            entry.put(Constant.HttpStep.NAME, varName);
                            entry.put(Constant.HttpStep.FROM, Constant.HttpStep.FROM_DEFAULT);
                            entry.put(Constant.HttpStep.EXPRESSION, "$." + varName);
                            extractArray.add(entry);
                        }
                    }
                    result.set(i - 1, new InternalStepDto(
                            prevStep.getId(), prevStep.getStepOrder(), prevStep.getName(),
                            prevStep.getStepType(), enhanced.toString(),
                            prevStep.getExpectedResult(), prevStep.getWeight(),
                            prevStep.getTimeoutMs(), prevStep.getRequired()));
                }
            }
            catch (Exception e)
            {
                // skip on parse error
            }
        }
        return result;
    }

    private void collectVarRefs(JsonNode config, Pattern varPattern, Set<String> vars)
    {
        String jsonStr = config.toString();
        Matcher m = varPattern.matcher(jsonStr);
        while (m.find())
        {
            vars.add(m.group(1));
        }
    }

    private GradingStepResult persist(GradingJob job, InternalPlanDto plan, InternalStepDto step,
            StepResultStatus status, String errorMessage)
    {
        OffsetDateTime now = OffsetDateTime.now();
        return stepResultRepository.save(GradingStepResult.builder()
                .jobId(job.getId())
                .planId(plan.getId())
                .stepId(step.getId())
                .stepOrder(step.getStepOrder())
                .stepName(step.getName())
                .stepType(step.getStepType())
                .status(status)
                .errorMessage(errorMessage)
                .startedAt(now)
                .completedAt(now)
                .build());
    }

    private void fail(GradingJob job, UUID submissionId, UUID assignmentId, UUID studentId,
            UUID planId, UUID sagaId, int planWeight, String reason)
    {
        job.setStatus(GradingJobStatus.FAILED);
        job.setErrorMessage(reason);
        job.setCompletedAt(OffsetDateTime.now());
        gradingJobRepository.save(job);
        writeLog(job.getId(), submissionId, GradingLogLevel.ERROR, reason);
        log.warn("Grading failed: job={} reason={}", job.getId(), reason);
        sagaTracker.finish(sagaId, SagaStatus.FAILED);
        postResult(job, submissionId, assignmentId, studentId, planId, List.of(),
                BigDecimal.ZERO.setScale(2), reason, List.of(), GradingJobStatus.FAILED);
        patchSubmission(submissionId, Constant.GradingLog.STEP_FAILED);
    }

    private void postResult(GradingJob job, UUID submissionId, UUID assignmentId, UUID studentId,
            UUID planId, List<InternalPlanDto> plans, BigDecimal score, String summary,
            List<ResultServiceClient.StepResultItem> items, GradingJobStatus status)
    {
        ResultServiceClient.CreateResultRequest request = ResultServiceClient.CreateResultRequest.builder()
                .submissionId(submissionId)
                .assignmentId(assignmentId)
                .studentId(studentId)
                .planId(planId)
                .planWeight(weightOf(plans, planId))
                .score(score)
                .maxScore(MAX_SCORE)
                .status(status.name())
                .summaryLog(summary)
                .stepResults(items)
                .build();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                resultServiceClient.create(request);
                return;
            } catch (Exception e) {
                if (attempt == 3) {
                    log.warn("Result report failed after 3 attempts (job already {}): {}", status,
                            safeMessage(e));
                } else {
                    log.warn("Result report attempt {} failed for job={}, retrying", attempt, job.getId());
                }
            }
        }
    }

    private void patchSubmission(UUID submissionId, String status)
    {
        try
        {
            submissionStatusClient.updateStatus(submissionId,
                    new SubmissionStatusClient.UpdateStatusRequest(status));
        }
        catch (Exception e)
        {
            log.warn("Submission status update failed ({}): {}", status,
                    safeMessage(e));
        }
    }

    private void writeLog(UUID jobId, UUID submissionId, GradingLogLevel level, String message)
    {
        try
        {
            gradingLogRepository.save(GradingLog.builder()
                    .jobId(jobId)
                    .submissionId(submissionId)
                    .step(Constant.GradingLog.STEP_GRADING)
                    .level(level)
                    .message(message)
                    .build());
        }
        catch (Exception e)
        {
            log.warn("Grading log write failed: {}", safeMessage(e));
        }
    }

    private ResultServiceClient.StepResultItem stepItem(InternalPlanDto plan, InternalStepDto step,
            GradingStepResult result, boolean passed, String errorMessage)
    {
        return ResultServiceClient.StepResultItem.builder()
                .planId(plan.getId())
                .stepId(step.getId())
                .stepOrder(step.getStepOrder())
                .stepName(step.getName())
                .stepType(step.getStepType())
                .passed(passed)
                .weight(nz(step.getWeight(), 1))
                .score(passed ? new BigDecimal(nz(step.getWeight(), 1)).setScale(2) : BigDecimal.ZERO.setScale(2))
                .actualValue(passed && result != null ? (result.getResponseBody() == null ? null
                        : result.getResponseBody().length() <= 20000
                        ? result.getResponseBody() : result.getResponseBody().substring(0, 20000)) : null)
                .expectedValue(step.getExpectedResult())
                .errorMessage(errorMessage)
                .durationMs(result != null ? result.getDurationMs() : null)
                .build();
    }

    private String buildSummary(List<ScoreCalculator.StepScore> outcomes,
            List<ResultServiceClient.StepResultItem> items, BigDecimal score)
    {
        long ran = outcomes.stream().filter(o -> !o.skipped()).count();
        long passed = outcomes.stream().filter(o -> !o.skipped() && o.passed()).count();
        long pct = ran == 0 ? 0 : Math.round(passed * 100.0 / ran);
        StringBuilder summary = new StringBuilder("Passed " + passed + "/" + ran
                + " steps (" + pct + "%), Score: " + score + "/" + MAX_SCORE);
        List<String> failed = items.stream()
                .filter(i -> Boolean.FALSE.equals(i.getPassed()))
                .map(i -> i.getStepName() + " — "
                        + (i.getErrorMessage() != null ? i.getErrorMessage() : "assertion failed"))
                .toList();
        if (!failed.isEmpty())
        {
            summary.append(". Failed: ").append(String.join("; ", failed));
        }
        return summary.toString();
    }

    private int weightOf(List<InternalPlanDto> plans, UUID planId)
    {
        if (planId == null)
        {
            return 1;
        }
        return plans.stream()
                .filter(p -> planId.equals(p.getId()))
                .map(p -> nz(p.getWeight(), 1))
                .findFirst()
                .orElse(1);
    }

    private static long nz(Integer value, long fallback)
    {
        return value != null ? value : fallback;
    }

    private static int nz(Integer value, int fallback)
    {
        return value != null ? value : fallback;
    }

    private static String safeMessage(Exception e)
    {
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }

    private static void cleanupWorkDir(Path workDir)
    {
        try (java.util.stream.Stream<Path> paths = Files.walk(workDir))
        {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> { try { Files.deleteIfExists(path); } catch (Exception e) { log.warn("Temp cleanup failed for {}: {}", path, safeMessage(e)); } });
        }
        catch (Exception e)
        {
            log.warn("Temp cleanup failed for {}: {}", workDir, safeMessage(e));
        }
    }
}
