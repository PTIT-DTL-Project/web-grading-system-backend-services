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
import vn.edu.ptit.web_grading_system.executor_service.service.db.DbDialectRegistry;
import vn.edu.ptit.web_grading_system.executor_service.service.db.MysqlDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.db.PostgresDialect;
import vn.edu.ptit.web_grading_system.executor_service.service.step.HttpStepExecutor;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepExecutor;
import vn.edu.ptit.web_grading_system.executor_service.service.step.StepRegistry;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import tools.jackson.databind.JsonNode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import vn.edu.ptit.web_grading_system.executor_service.Constant;

import feign.FeignException;

class GradingOrchestratorTest {

    @TempDir
    Path tempDir;

    record Fixture(GradingOrchestrator orchestrator, GradingJob job, AtomicInteger executions,
                   GradingStepResultRepository stepRepo, ResultServiceClient resultClient,
                   SubmissionStatusClient submissionClient, PortAllocator ports,
                   ArtifactService artifacts, Path workDir,
                   java.util.concurrent.atomic.AtomicReference<Map<String, Object>> capturedVars) {
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
        Mockito.when(ports.claimDbPort()).thenReturn(23457);
        DockerComposeRunner runner = Mockito.mock(DockerComposeRunner.class);
        Mockito.when(runner.boot(Mockito.any(), Mockito.anyLong())).thenAnswer(inv -> {
            DockerComposePatcher.EffectiveCompose effective = inv.getArgument(0);
            return new DockerComposeRunner.RunningCompose(
                    Mockito.mock(ComposeContainer.class), "localhost", 23456);
        });

        AtomicInteger executions = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<Map<String, Object>> capturedVars = new java.util.concurrent.atomic.AtomicReference<>();
        StepExecutor stub = new StepExecutor() {
            @Override
            public String type() {
                return "HTTP_REQUEST";
            }

            @Override
            public GradingStepResult execute(HttpStepExecutor.StepContext ctx) {
                executions.incrementAndGet();
                capturedVars.set(ctx.variableContext().snapshot());
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
        StepExecutor dbStub = new StepExecutor() {
            @Override
            public String type() {
                return "DB_QUERY";
            }

            @Override
            public GradingStepResult execute(HttpStepExecutor.StepContext ctx) {
                executions.incrementAndGet();
                capturedVars.set(ctx.variableContext().snapshot());
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
                new ExecutorProperties.Reaper(30, 300000, 3),
                new ExecutorProperties.Maven(null));
        GradingOrchestrator orchestrator = new GradingOrchestrator(jobRepo, stepRepo, logRepo,
                course, submission, result, artifacts, ports, runner,
                new StepRegistry(List.of(stub, dbStub)), new ObjectMapper(), props,
                Mockito.mock(SagaTracker.class),
                new DbDialectRegistry(List.of(new PostgresDialect(), new MysqlDialect())));
        return new Fixture(orchestrator, job, executions, stepRepo, result, submission,
                ports, artifacts, workDir, capturedVars);
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

    @Test
    void resultPosts_retriesTransientFailures() {
        InternalPlanDto one = plan(0, List.of(step(null, 0, false)));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Mockito.when(f.resultClient().create(Mockito.any()))
                .thenThrow(new RuntimeException("boom"))
                .thenThrow(new RuntimeException("boom"))
                .thenReturn(Map.of("id", UUID.randomUUID()));
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t3");

        assertEquals(GradingJobStatus.DONE, job.getStatus());
        Mockito.verify(f.resultClient(), Mockito.times(3)).create(Mockito.any());
    }

    @Test
    void resultPosts_givesUpAfterThreeAttempts() {
        InternalPlanDto one = plan(0, List.of(step(null, 0, false)));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Mockito.when(f.resultClient().create(Mockito.any()))
                .thenThrow(new RuntimeException("down"));
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t4");

        assertEquals(GradingJobStatus.DONE, job.getStatus());
        Mockito.verify(f.resultClient(), Mockito.times(3)).create(Mockito.any());
    }

    @Test
    void resultPosts_skipsRetryOnValidationError() {
        InternalPlanDto one = plan(0, List.of(step(null, 0, false)));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        FeignException badRequest = Mockito.mock(FeignException.class);
        Mockito.when(badRequest.status()).thenReturn(400);
        Mockito.when(badRequest.getMessage()).thenReturn("400 Bad Request");
        Mockito.when(f.resultClient().create(Mockito.any())).thenThrow(badRequest);
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t5");

        assertEquals(GradingJobStatus.DONE, job.getStatus());
        Mockito.verify(f.resultClient(), Mockito.times(1)).create(Mockito.any());
    }

    // ─── autoInjectExtracts tests ───

    private GradingOrchestrator rawOrchestrator() {
        return new GradingOrchestrator(null, null, null, null, null, null, null,
                null, null, null, new ObjectMapper(), null, null, null);
    }

    private List<InternalStepDto> invokeAutoInject(Object orchestrator,
            List<InternalStepDto> steps) throws Exception {
        Method method = GradingOrchestrator.class.getDeclaredMethod(
                "autoInjectExtracts", List.class);
        method.setAccessible(true);
        return (List<InternalStepDto>) method.invoke(orchestrator, steps);
    }

    @Test
    void autoInjectExtracts_injectsVariableIntoPreviousStep() throws Exception {
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("initial")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/classes\"}")
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("fetch-course")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/courses/${courseId}\"}")
                .build();
        InternalStepDto step3 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(3).name("submit-grade")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/grades\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step1, step2, step3));

        // Step 1 (index 0) should get extract for courseId because Step 2 references it
        JsonNode step1Config = new ObjectMapper().readTree(result.get(0).getConfig());
        assertTrue(step1Config.has("extract"), "Step 1 should have extract entries");
        JsonNode extracts = step1Config.get("extract");
        assertEquals(1, extracts.size(), "Should have one extract entry");
        assertEquals("courseId", extracts.get(0).path("name").asString());
        assertEquals("response_body", extracts.get(0).path("from").asString());
        assertEquals("$.courseId", extracts.get(0).path("expression").asString());

        // Step 2 and Step 3 configs should be unchanged
        assertEquals("GET", new ObjectMapper().readTree(result.get(1).getConfig())
                .path("method").asString());
        assertEquals("POST", new ObjectMapper().readTree(result.get(2).getConfig())
                .path("method").asString());
    }

    @Test
    void autoInjectExtracts_noDuplicateWhenAlreadyPresent() throws Exception {
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("initial")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/classes\","
                        + "\"extract\":[{\"name\":\"courseId\",\"from\":\"response_body\","
                        + "\"expression\":\"$.courseId\"}]}")
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("fetch-course")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/courses/${courseId}\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step1, step2));

        JsonNode extracts = new ObjectMapper().readTree(result.get(0).getConfig())
                .get("extract");
        assertEquals(1, extracts.size(),
                "Should NOT create duplicate extract entries");
        assertEquals("courseId", extracts.get(0).path("name").asString());
    }

    @Test
    void autoInjectExtracts_singleStepUnchanged() throws Exception {
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("initial")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/classes\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step1));

        assertEquals(1, result.size());
        assertEquals("{\"method\":\"POST\",\"path\":\"/api/classes\"}",
                result.get(0).getConfig());
    }

    @Test
    void autoInjectExtracts_emptyListReturnsEmpty() throws Exception {
        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(), List.of());
        assertTrue(result.isEmpty());
    }

    @Test
    void autoInjectExtracts_multipleVariablesInjected() throws Exception {
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("initial")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/classes\"}")
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("multi-ref")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\","
                        + "\"path\":\"/api/grades?course=${courseId}&class=${classId}\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step1, step2));

        JsonNode extracts = new ObjectMapper().readTree(result.get(0).getConfig())
                .get("extract");
        assertEquals(2, extracts.size(), "Should have 2 extract entries");
        Set<String> names = new HashSet<>();
        for (JsonNode entry : extracts) {
            names.add(entry.path("name").asString());
        }
        assertTrue(names.contains("courseId"), "Should extract courseId");
        assertTrue(names.contains("classId"), "Should extract classId");
        for (JsonNode entry : extracts) {
            assertEquals("response_body", entry.path("from").asString());
            assertTrue(entry.path("expression").asString().startsWith("$."),
                    "Expression should start with $.");
        }
    }

    @Test
    void autoInjectExtracts_malformedConfigSkippedGracefully() throws Exception {
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("broken")
                .stepType("HTTP_REQUEST")
                .config("THIS IS NOT JSON{{{")
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("grade")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/grades/${courseId}\"}")
                .build();

        assertDoesNotThrow(() -> {
            List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                    List.of(step1, step2));
            assertEquals(2, result.size(), "Should return same number of steps");
            assertEquals("THIS IS NOT JSON{{{", result.get(0).getConfig(),
                    "Malformed config should be preserved unchanged");
        });
    }

    @Test
    void autoInjectExtracts_nullConfigTreatedAsEmpty() throws Exception {
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("null-step")
                .stepType("HTTP_REQUEST")
                .config(null)
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("grade")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/grades/${courseId}\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step1, step2));

        JsonNode step1Config = new ObjectMapper().readTree(result.get(0).getConfig());
        assertTrue(step1Config.has("extract"),
                "Null config should be treated as empty object");
        assertEquals("courseId", step1Config.get("extract").get(0).path("name").asString());
    }

    @Test
    void autoInjectExtracts_findsVariableFromNonAdjacentStep() throws Exception {
        // Step 0: explicit extract book1Id → Step 3 refs ${book1Id} → should NOT inject into Step 2
        InternalStepDto step0 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("create-book-1")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\","
                        + "\"extract\":[{\"name\":\"book1Id\",\"from\":\"response_body\",\"expression\":\"$.id\"}]}")
                .build();
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("create-book-2")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\","
                        + "\"extract\":[{\"name\":\"book2Id\",\"from\":\"response_body\",\"expression\":\"$.id\"}]}")
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(3).name("create-book-3")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\","
                        + "\"extract\":[{\"name\":\"book3Id\",\"from\":\"response_body\",\"expression\":\"$.id\"}]}")
                .build();
        InternalStepDto step3 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(4).name("get-book-1")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/books/${book1Id}\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step0, step1, step2, step3));

        // Step 0 already has extract → no injection should happen
        JsonNode step0Config = new ObjectMapper().readTree(result.get(0).getConfig());
        assertTrue(step0Config.has("extract"));
        assertEquals(1, step0Config.get("extract").size(), "Step 0 should have exactly 1 extract");
        assertEquals("book1Id", step0Config.get("extract").get(0).path("name").asString());

        // Step 2 should still have ONLY its original extract (book3Id), NOT book1Id
        JsonNode step2Config = new ObjectMapper().readTree(result.get(2).getConfig());
        assertTrue(step2Config.has("extract"), "Step 2 should keep its original extract");
        assertEquals(1, step2Config.get("extract").size(), "Step 2 should not get book1Id inject");
        assertEquals("book3Id", step2Config.get("extract").get(0).path("name").asString());
    }

    @Test
    void autoInjectExtracts_multipleVariablesFromDifferentSources() throws Exception {
        // 3 POSTs with explicit extracts + GET refs → no injection needed
        InternalStepDto step0 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("create-1")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\","
                        + "\"extract\":[{\"name\":\"book1Id\",\"from\":\"response_body\",\"expression\":\"$.id\"}]}")
                .build();
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("create-2")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\","
                        + "\"extract\":[{\"name\":\"book2Id\",\"from\":\"response_body\",\"expression\":\"$.id\"}]}")
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(3).name("create-3")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\","
                        + "\"extract\":[{\"name\":\"book3Id\",\"from\":\"response_body\",\"expression\":\"$.id\"}]}")
                .build();
        InternalStepDto step3 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(4).name("get-list")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/books?ids=${book1Id},${book2Id},${book3Id}\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step0, step1, step2, step3));

        // No step should get new inject (all sources explicit)
        for (int i = 0; i < result.size(); i++) {
            JsonNode config = new ObjectMapper().readTree(result.get(i).getConfig());
            if (i < 3) {
                // Steps 0-2 have explicit extracts → should stay at 1
                assertTrue(config.has("extract"), "Step " + i + " should keep extract");
                assertEquals(1, config.get("extract").size(),
                        "Step " + i + " should not get duplicate extracts");
            } else {
                // Step 3 has no extract → should NOT gain one (all vars mapped)
                assertFalse(config.has("extract"),
                        "Step " + i + " should not get any extract");
            }
        }
    }

    @Test
    void autoInjectExtracts_fallbackToPreviousStep() throws Exception {
        // No explicit extract → fallback to i-1 (old behavior)
        InternalStepDto step0 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("create")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\"}")
                .build();
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("get")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/books/${bookId}\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step0, step1));

        JsonNode step0Config = new ObjectMapper().readTree(result.get(0).getConfig());
        assertTrue(step0Config.has("extract"), "Should fallback to inject in step 0");
        assertEquals("bookId", step0Config.get("extract").get(0).path("name").asString());
    }

    @Test
    void autoInjectExtracts_doesNotDuplicateMappedVariable() throws Exception {
        // Step 0 has explicit extract → Step 1 refs it → no duplicate inject into Step 0
        InternalStepDto step0 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("create")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\","
                        + "\"extract\":[{\"name\":\"bookId\",\"from\":\"response_body\",\"expression\":\"$.id\"}]}")
                .build();
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("get")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/books/${bookId}\"}")
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(3).name("get2")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/books/${bookId}\"}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step0, step1, step2));

        JsonNode step0Config = new ObjectMapper().readTree(result.get(0).getConfig());
        assertEquals(1, step0Config.get("extract").size(),
                "Should NOT duplicate extract when already mapped");
    }

    @Test
    void autoInjectExtracts_doesNotSkipWhenProducerRunsAfter() throws Exception {
        /*
         * S0: POST → no extract (creates token)
         * S1: GET /api/books/${token} → needs token (runs BEFORE S2)
         * S2: POST → extract token (explicit, for later steps)
         *
         * Old code: map has token→2, skips injection at S1 → token="" → FAIL
         * New code: src=2, 2 < 1 = false → inject into S0 → works
         * Review: 2026-09-20, Pullfrog PR #16.
         */
        InternalStepDto step0 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(1).name("create")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\"}")
                .build();
        InternalStepDto step1 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(2).name("get")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"GET\",\"path\":\"/api/books/${token}\"}")
                .build();
        InternalStepDto step2 = InternalStepDto.builder()
                .id(UUID.randomUUID()).stepOrder(3).name("extract")
                .stepType("HTTP_REQUEST")
                .config("{\"method\":\"POST\",\"path\":\"/api/books\","
                        + "\"extract\":[{\"name\":\"token\",\"from\":\"response_body\",\"expression\":\"$.id\"}]}")
                .build();

        List<InternalStepDto> result = invokeAutoInject(rawOrchestrator(),
                List.of(step0, step1, step2));

        JsonNode step0Config = new ObjectMapper().readTree(result.get(0).getConfig());
        boolean hasExtract = step0Config.has("extract");
        assertTrue(hasExtract, "S0 should get extract (producer S2 runs AFTER S1)");
        assertEquals("token", step0Config.get("extract").get(0).path("name").asString());
    }

    // ─── DB port allocation tests ───

    private static InternalStepDto dbStep(int order) {
        return InternalStepDto.builder()
                .id(UUID.randomUUID())
                .stepOrder(order)
                .name("db" + order)
                .stepType("DB_QUERY")
                .config("{\"connection\":{\"db_service\":\"db\",\"database\":\"appdb\","
                        + "\"username\":\"postgres\",\"password\":\"postgres\"},"
                        + "\"query\":\"SELECT 1\"}")
                .weight(1)
                .required(false)
                .build();
    }

    @Test
    void dbSteps_allocateAndReleaseDbPort() {
        InternalPlanDto one = plan(0, List.of(step(null, 0, false), dbStep(1)));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t-db1");

        assertEquals(GradingJobStatus.DONE, job.getStatus());
        // Plan contains a DB step → host port claimed, released in finally, and
        // exposed to step executors as db_port in VariableContext.
        Mockito.verify(f.ports()).claimDbPort();
        Mockito.verify(f.ports()).release(23457);
        assertNotNull(f.capturedVars().get());
        assertEquals(23457, f.capturedVars().get().get(Constant.VariableContext.DB_PORT));
    }

    @Test
    void noDbSteps_noDbPortAllocated() {
        InternalPlanDto one = plan(0, List.of(step(null, 0, false)));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t-db2");

        assertEquals(GradingJobStatus.DONE, job.getStatus());
        Mockito.verify(f.ports(), Mockito.never()).claimDbPort();
        assertNotNull(f.capturedVars().get());
        assertNull(f.capturedVars().get().get(Constant.VariableContext.DB_PORT));
    }

    @Test
    void dbPortExposedInVariableContext_forAllSteps() {
        // HTTP step first, DB step second — db_port must already be in context
        // when the FIRST step runs (allocated in grade(), before runSteps).
        InternalPlanDto one = plan(0, List.of(step(null, 0, false), dbStep(1)));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t-db3");

        assertEquals(GradingJobStatus.DONE, job.getStatus());
        assertEquals(2, f.executions().get());
        assertEquals(23457, f.capturedVars().get().get(Constant.VariableContext.DB_PORT));
    }

    // ─── DB requirements scan (multi-DBMS) ───

    private static InternalStepDto dbStepWithConfig(String configJson) {
        return InternalStepDto.builder()
                .id(UUID.randomUUID())
                .stepOrder(1)
                .name("db-engine")
                .stepType("DB_QUERY")
                .config(configJson)
                .weight(1)
                .required(false)
                .build();
    }

    private static GradingOrchestrator.DbRequirements invokeScan(List<InternalPlanDto> plans)
            throws Exception {
        GradingOrchestrator orch = new GradingOrchestrator(null, null, null, null, null, null,
                null, null, null, null, new ObjectMapper(), null, null,
                new DbDialectRegistry(List.of(new PostgresDialect(), new MysqlDialect())));
        Method method = GradingOrchestrator.class.getDeclaredMethod(
                "scanDbRequirements", List.class);
        method.setAccessible(true);
        return (GradingOrchestrator.DbRequirements) method.invoke(orch, plans);
    }

    @Test
    void scanDbRequirements_mysqlWithoutPort_resolvesMysqlDefaultPort() throws Exception {
        // db_type present, db_port omitted → dialect owns the default (3306),
        // replacing the old hardcoded 5432.
        InternalStepDto s = dbStepWithConfig("{\"connection\":{\"db_service\":\"db\","
                + "\"db_type\":\"mysql\",\"database\":\"appdb\","
                + "\"username\":\"root\",\"password\":\"root\"},\"query\":\"SELECT 1\"}");

        GradingOrchestrator.DbRequirements req = invokeScan(List.of(plan(0, List.of(s))));

        assertTrue(req.present());
        assertEquals("mysql", req.dbType());
        assertEquals(3306, req.dbContainerPort());
        assertEquals("db", req.dbService());
        assertNull(req.parseError());
    }

    @Test
    void scanDbRequirements_noDbType_resolvesPostgresDefaultPort() throws Exception {
        // Legacy config without db_type → default engine (postgres), 5432.
        GradingOrchestrator.DbRequirements req =
                invokeScan(List.of(plan(0, List.of(dbStep(1)))));

        assertTrue(req.present());
        assertNull(req.dbType());
        assertEquals(5432, req.dbContainerPort());
        assertEquals("db", req.dbService());
    }

    @Test
    void scanDbRequirements_explicitPortWinsOverDialectDefault() throws Exception {
        InternalStepDto s = dbStepWithConfig("{\"connection\":{\"db_service\":\"db\","
                + "\"db_type\":\"mariadb\",\"db_port\":4406,\"database\":\"appdb\","
                + "\"username\":\"root\",\"password\":\"root\"},\"query\":\"SELECT 1\"}");

        GradingOrchestrator.DbRequirements req = invokeScan(List.of(plan(0, List.of(s))));

        assertEquals(4406, req.dbContainerPort());
        assertEquals("mariadb", req.dbType());
    }

    @Test
    void scanDbRequirements_noDbSteps_returnsAbsent() throws Exception {
        InternalPlanDto one = plan(0, List.of(step(null, 0, false)));

        assertFalse(invokeScan(List.of(one)).present());
    }

    @Test
    void scanDbRequirements_unknownDbType_throws() throws Exception {
        InternalStepDto s = dbStepWithConfig("{\"connection\":{\"db_service\":\"db\","
                + "\"db_type\":\"oracle\",\"database\":\"appdb\","
                + "\"username\":\"root\",\"password\":\"root\"},\"query\":\"SELECT 1\"}");

        var e = assertThrows(InvocationTargetException.class,
                () -> invokeScan(List.of(plan(0, List.of(s)))));
        assertInstanceOf(IllegalArgumentException.class, e.getCause());
        assertTrue(e.getCause().getMessage().contains("oracle"));
    }

    @Test
    void unknownDbType_failsJobBeforeAnyPortClaim() {
        // Defense-in-depth: validator rejects unknown db_type at save
        // time, but legacy rows must fail the job cleanly — with no
        // port claimed AND no artifact unzipped (scan now runs before
        // the download). Review: 2026-09-26, Pullfrog PR #17 (F1).
        InternalStepDto s = dbStepWithConfig("{\"connection\":{\"db_service\":\"db\","
                + "\"db_type\":\"oracle\",\"database\":\"appdb\","
                + "\"username\":\"root\",\"password\":\"root\"},\"query\":\"SELECT 1\"}");
        InternalPlanDto one = plan(0, List.of(step(null, 0, false), s));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t-db-oracle");

        assertEquals(GradingJobStatus.FAILED, job.getStatus());
        Mockito.verify(f.ports(), Mockito.never()).claim();
        Mockito.verify(f.ports(), Mockito.never()).claimDbPort();
        Mockito.verify(f.artifacts(), Mockito.never()).fetchWorkDir(Mockito.any(), Mockito.any());
        Mockito.verify(f.stepRepo(), Mockito.never()).save(Mockito.any());
    }

    @Test
    void claimDbPortThrows_appPortReleasedAndWorkDirCleaned() {
        // If claimDbPort() throws (port exhaustion), the allocated
        // appPort and the unzipped workDir must still be released/deleted
        // on every exit path — no leak even when the outer try hasn't
        // started yet... (it has: claims are inside the single try now).
        InternalStepDto s = dbStepWithConfig("{\"connection\":{\"db_service\":\"db\","
                + "\"db_type\":\"mysql\",\"database\":\"appdb\","
                + "\"username\":\"root\",\"password\":\"root\"},\"query\":\"SELECT 1\"}");
        InternalPlanDto one = plan(0, List.of(step(null, 0, false), s));
        Fixture f;
        try {
            f = fixture(StepResultStatus.PASSED, List.of(one));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        Mockito.when(f.ports().claimDbPort()).thenThrow(new RuntimeException("boom"));
        GradingJob job = f.job();

        f.orchestrator().grade(job.getId(), job.getSubmissionId(), job.getAssignmentId(),
                job.getStudentId(), null, "submissions/x.zip", "t-db-throw");

        assertEquals(GradingJobStatus.FAILED, job.getStatus());
        // appPort was claimed before claimDbPort threw
        Mockito.verify(f.ports()).claim();
        Mockito.verify(f.ports()).release(23456);
        Mockito.verify(f.stepRepo(), Mockito.never()).save(Mockito.any());
        assert java.nio.file.Files.notExists(f.workDir());
    }
}
