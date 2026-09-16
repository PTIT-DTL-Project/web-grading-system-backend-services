package vn.edu.ptit.web_grading_system.executor_service.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.testcontainers.containers.ComposeContainer;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.executor_service.client.CourseInternalClient;
import vn.edu.ptit.web_grading_system.executor_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.executor_service.client.SubmissionStatusClient;
import vn.edu.ptit.web_grading_system.executor_service.config.ExecutorProperties;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.AssignmentGradingConfigDto;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.InternalPlanDto;
import vn.edu.ptit.web_grading_system.executor_service.dto.internal.InternalStepDto;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;
import vn.edu.ptit.web_grading_system.executor_service.entities.StepResultStatus;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingJobRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingLogRepository;
import vn.edu.ptit.web_grading_system.executor_service.repositories.GradingStepResultRepository;
import vn.edu.ptit.web_grading_system.executor_service.service.step.HttpStepExecutor;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepExecutor;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepRegistry;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GradingOrchestratorTest {

    @TempDir
    Path tempDir;

    record Fixture(GradingOrchestrator orchestrator, GradingJob job, AtomicInteger executions,
                   GradingStepResultRepository stepRepo, ResultServiceClient resultClient,
                   SubmissionStatusClient submissionClient) {
    }

    private Fixture fixture(StepResultStatus stubStatus, List<InternalPlanDto> plans) throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID submissionId = UUID.randomUUID();
        GradingJob job = GradingJob.builder()
                .submissionId(submissionId)
                .assignmentId(UUID.randomUUID())
                .studentId(UUID.randomUUID())
                .status(GradingJobStatus.PENDING)
                .build();
        job.setId(jobId);

        GradingJobRepository jobRepo = Mockito.mock(GradingJobRepository.class);
        Mockito.when(jobRepo.findById(jobId)).thenReturn(Optional.of(job));
        Mockito.when(jobRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        GradingStepResultRepository stepRepo = Mockito.mock(GradingStepResultRepository.class);
        Mockito.when(stepRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
        GradingLogRepository logRepo = Mockito.mock(GradingLogRepository.class);

        CourseInternalClient course = Mockito.mock(CourseInternalClient.class);
        Mockito.when(course.gradingConfig(Mockito.any())).thenReturn(AssignmentGradingConfigDto.builder()
                .gradingStrategy("STUDENT_DOCKER_COMPOSE")
                .dockerComposePort(8080)
                .startupTimeoutMs(1000)
                .executionTimeoutMs(60000)
                .maxCpu(0.5)
                .maxMemoryMb(256)
                .build());
        Mockito.when(course.plans(Mockito.any())).thenReturn(plans);
        SubmissionStatusClient submission = Mockito.mock(SubmissionStatusClient.class);
        ResultServiceClient result = Mockito.mock(ResultServiceClient.class);
        Mockito.when(result.create(Mockito.any())).thenReturn(Map.of("id", UUID.randomUUID()));

        Path workDir = tempDir.resolve(submissionId.toString());
        Files.createDirectories(workDir);
        Files.writeString(workDir.resolve("docker-compose.yml"),
                "services:\n  app:\n    image: demo:latest\n    ports:\n      - \"8080:8080\"\n");
        ArtifactService artifacts = Mockito.mock(ArtifactService.class);
        Mockito.when(artifacts.fetchWorkDir(Mockito.eq(submissionId), Mockito.any()))
                .thenReturn(workDir);
        PortAllocator ports = Mockito.mock(PortAllocator.class);
        Mockito.when(ports.claim()).thenReturn(23456);
        DockerComposeRunner runner = Mockito.mock(DockerComposeRunner.class);
        Mockito.when(runner.boot(Mockito.any(), Mockito.anyLong())).thenAnswer(inv -> {
            DockerComposePatcher.EffectiveCompose effective = inv.getArgument(0);
            return new DockerComposeRunner.RunningCompose(
                    Mockito.mock(ComposeContainer.class), "localhost", 23456);
        });

        AtomicInteger executions = new AtomicInteger();
        StepExecutor stub = new StepExecutor() {
            @Override
            public String type() {
                return "HTTP_REQUEST";
            }

            @Override
            public GradingStepResult execute(HttpStepExecutor.StepContext ctx) {
                executions.incrementAndGet();
                OffsetDateTime now = OffsetDateTime.now();
                return GradingStepResult.builder()
                        .jobId(ctx.jobId())
                        .planId(ctx.planId())
                        .stepId(ctx.stepId())
                        .stepOrder(ctx.stepOrder())
                        .stepName(ctx.stepName())
                        .stepType(type())
                        .status(stubStatus)
                        .errorMessage(stubStatus == StepResultStatus.PASSED ? null : "boom")
                        .startedAt(now)
                        .completedAt(now)
                        .build();
            }
        };
        ExecutorProperties props = new ExecutorProperties(
                tempDir.toString(), new ExecutorProperties.Container(1000, 60000),
                new ExecutorProperties.Reaper(30, 300000, 3));
        GradingOrchestrator orchestrator = new GradingOrchestrator(jobRepo, stepRepo, logRepo,
                course, submission, result, artifacts, ports, runner,
                new StepRegistry(List.of(stub)), new ObjectMapper(), props,
                Mockito.mock(SagaTracker.class));
        return new Fixture(orchestrator, job, executions, stepRepo, result, submission);
    }

    private static InternalStepDto step(UUID planId, int order, boolean required) {
        return InternalStepDto.builder()
                .id(UUID.randomUUID())
                .stepOrder(order)
                .name("s" + order)
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/\"}")
                .weight(1)
                .required(required)
                .build();
    }

    private static InternalPlanDto plan(int order, List<InternalStepDto> steps) {
        return InternalPlanDto.builder()
                .id(UUID.randomUUID())
                .name("p" + order)
                .sequenceOrder(order)
                .weight(1)
                .steps(steps)
                .build();
    }

    @Test
    void requiredFailure_stopsPlanAndSkipsRest() {
        InternalPlanDto one = plan(0, List.of(step(null, 0, true), step(null, 1, false)));
        Fixture f;
        try {
            f = fixture(StepResultStatus.FAILED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t1");

        assertEquals(GradingJobStatus.DONE, job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertEquals(1, f.executions().get());
        ArgumentCaptor<GradingStepResult> saved =
                ArgumentCaptor.forClass(GradingStepResult.class);
        Mockito.verify(f.stepRepo(), Mockito.times(2)).save(saved.capture());
        assertEquals(StepResultStatus.FAILED, saved.getAllValues().get(0).getStatus());
        assertEquals(StepResultStatus.SKIPPED, saved.getAllValues().get(1).getStatus());
        Mockito.verify(f.resultClient()).create(Mockito.argThat(req ->
                req.getScore().compareTo(new java.math.BigDecimal("0.00")) == 0
                        && req.getSummaryLog().contains("Passed 0/1 steps")));
        Mockito.verify(f.submissionClient(), Mockito.times(2))
                .updateStatus(Mockito.eq(job.getSubmissionId()), Mockito.any());
    }

    @Test
    void planIdFilter_runsOnlyRequestedPlan() {
        InternalPlanDto a = plan(0, List.of(step(null, 0, false)));
        InternalPlanDto b = plan(1, List.of(step(null, 0, false)));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(a, b));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), b.getId(), "submissions/x.zip", "t2");

        assertEquals(GradingJobStatus.DONE, job.getStatus());
        assertEquals(1, f.executions().get());
        Mockito.verify(f.stepRepo(), Mockito.times(1)).save(Mockito.argThat(r ->
                r.getPlanId().equals(b.getId())));
        Mockito.verify(f.resultClient()).create(Mockito.argThat(req ->
                req.getPlanId().equals(b.getId())
                        && req.getScore().compareTo(new java.math.BigDecimal("10.00")) == 0));
    }
}
