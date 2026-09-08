package vn.edu.ptit.web_grading_system.course_service.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentExistsResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentGradingConfigDto;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.InternalPlanDto;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.InternalStepDto;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreatePlanRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateStepRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdatePlanRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateStepRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.PlanResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StepResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entities.TestPlan;
import vn.edu.ptit.web_grading_system.course_service.entities.TestStep;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.TestPlanRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.TestStepRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestPlanService {

    private final AssignmentRepository assignmentRepository;
    private final TestPlanRepository testPlanRepository;
    private final TestStepRepository testStepRepository;
    private final tools.jackson.databind.ObjectMapper objectMapper;

    // ---------- ownership / scoped lookups ----------

    private Assignment requireOwnedAssignment(UUID assignmentId, UUID ownerId) {
        return assignmentRepository.findByIdAndOwnerId(assignmentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
    }

    private TestPlan requirePlan(UUID planId, UUID assignmentId) {
        return testPlanRepository.findByIdAndAssignmentId(planId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planId));
    }

    private TestStep requireStep(UUID stepId, UUID planId) {
        return testStepRepository.findByIdAndPlanId(stepId, planId)
                .orElseThrow(() -> new ResourceNotFoundException("Step not found: " + stepId));
    }

    // ---------- plans (lecturer) ----------

    @Transactional
    public PlanResponse createPlan(UUID assignmentId, UUID ownerId, CreatePlanRequest request) {
        requireOwnedAssignment(assignmentId, ownerId);
        String name = request.getName().trim();
        if (testPlanRepository.existsByAssignmentIdAndSequenceOrder(assignmentId, request.getSequenceOrder())) {
            throw new BadRequestException(
                    "A plan with sequence_order " + request.getSequenceOrder() + " already exists in this assignment");
        }
        TestPlan plan = testPlanRepository.save(TestPlan.builder()
                .assignmentId(assignmentId)
                .name(name)
                .description(request.getDescription())
                .sequenceOrder(request.getSequenceOrder())
                .weight(request.getWeight() != null ? request.getWeight() : 1)
                .build());
        log.info("Test plan created: id={}, name={}, assignment={}", plan.getId(), name, assignmentId);
        return toPlanResponse(plan, List.of());
    }

    public List<PlanResponse> listPlans(UUID assignmentId, UUID ownerId) {
        requireOwnedAssignment(assignmentId, ownerId);
        List<TestPlan> plans = testPlanRepository.findAllByAssignmentIdOrderBySequenceOrderAsc(assignmentId);
        if (plans.isEmpty()) {
            return List.of();
        }
        List<TestStep> allSteps = testStepRepository
                .findAllByPlanIdInOrderByStepOrderAsc(plans.stream().map(TestPlan::getId).toList());
        return plans.stream()
                .map(plan -> toPlanResponse(plan,
                        allSteps.stream().filter(s -> s.getPlanId().equals(plan.getId())).toList()))
                .toList();
    }

    @Transactional
    public PlanResponse updatePlan(UUID assignmentId, UUID ownerId, UUID planId, UpdatePlanRequest request) {
        requireOwnedAssignment(assignmentId, ownerId);
        TestPlan plan = requirePlan(planId, assignmentId);
        if (request.getSequenceOrder() != null
                && !request.getSequenceOrder().equals(plan.getSequenceOrder())
                && testPlanRepository.existsByAssignmentIdAndSequenceOrder(assignmentId, request.getSequenceOrder())) {
            throw new BadRequestException(
                    "A plan with sequence_order " + request.getSequenceOrder() + " already exists in this assignment");
        }
        plan.setName(request.getName().trim());
        if (request.getDescription() != null) {
            plan.setDescription(request.getDescription());
        }
        if (request.getSequenceOrder() != null) {
            plan.setSequenceOrder(request.getSequenceOrder());
        }
        if (request.getWeight() != null) {
            plan.setWeight(request.getWeight());
        }
        log.info("Test plan updated: id={}, name={}", planId, plan.getName());
        return toPlanResponse(plan, testStepRepository.findAllByPlanIdOrderByStepOrderAsc(planId));
    }

    @Transactional
    public void deletePlan(UUID assignmentId, UUID ownerId, UUID planId) {
        requireOwnedAssignment(assignmentId, ownerId);
        TestPlan plan = requirePlan(planId, assignmentId);
        OffsetDateTime now = OffsetDateTime.now();
        for (TestStep step : testStepRepository.findAllByPlanIdOrderByStepOrderAsc(planId)) {
            step.setDeletedAt(now);
        }
        testStepRepository.saveAll(testStepRepository.findAllByPlanIdOrderByStepOrderAsc(planId));
        plan.setDeletedAt(now);
        testPlanRepository.save(plan);
        log.info("Test plan soft-deleted with its steps: id={}, assignment={}", planId, assignmentId);
    }

    // ---------- steps (lecturer) ----------

    @Transactional
    public StepResponse createStep(UUID assignmentId, UUID ownerId, UUID planId, CreateStepRequest request) {
        requireOwnedAssignment(assignmentId, ownerId);
        TestPlan plan = requirePlan(planId, assignmentId);

        String configJson;
        try {
            configJson = StepConfigValidator.validateAndSerialize(
                    request.getStepType(), request.getConfig());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        String expectedJson = serializeIfPresent(request.getExpectedResult());

        Integer order = request.getStepOrder();
        if (testStepRepository.existsByPlanIdAndStepOrder(planId, order)) {
            throw new BadRequestException(
                    "step_order " + order + " already exists in plan '" + plan.getName() + "'");
        }
        TestStep step = testStepRepository.save(TestStep.builder()
                .planId(planId)
                .stepOrder(order)
                .name(request.getName().trim())
                .description(request.getDescription())
                .stepType(request.getStepType())
                .config(configJson)
                .expectedResult(expectedJson)
                .weight(request.getWeight() != null ? request.getWeight() : 1)
                .timeoutMs(request.getTimeoutMs())
                .required(request.getRequired() != null ? request.getRequired() : true)
                .build());
        log.info("Test step created: id={}, type={}, order={}, plan={}",
                step.getId(), step.getStepType(), order, planId);
        return toStepResponse(step);
    }

    @Transactional
    public StepResponse updateStep(UUID assignmentId, UUID ownerId, UUID planId, UUID stepId,
                                   UpdateStepRequest request) {
        requireOwnedAssignment(assignmentId, ownerId);
        requirePlan(planId, assignmentId);
        TestStep step = requireStep(stepId, planId);

        boolean typeChanged = request.getStepType() != null
                && request.getStepType() != step.getStepType();
        if (typeChanged && request.getConfig() == null) {
            throw new BadRequestException(
                    "config is required when changing stepType to " + request.getStepType());
        }
        var effectiveType = typeChanged ? request.getStepType() : step.getStepType();
        String configJson;
        try {
            JsonNode effectiveConfig = request.getConfig() != null
                    ? request.getConfig()
                    : objectMapper.readTree(step.getConfig());
            configJson = StepConfigValidator.validateAndSerialize(effectiveType, effectiveConfig);
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException(
                    "Invalid step config: " + rootMessage(e));
        }

        if (request.getStepOrder() != null && !request.getStepOrder().equals(step.getStepOrder())) {
            Integer order = request.getStepOrder();
            if (testStepRepository.existsByPlanIdAndStepOrder(planId, order)) {
                throw new BadRequestException(
                        "step_order " + order + " already exists in this plan — free the slot first "
                                + "(update the other step's order, then retry)");
            }
            step.setStepOrder(order);
        }

        step.setName(request.getName().trim());
        if (request.getDescription() != null) {
            step.setDescription(request.getDescription());
        }
        if (typeChanged) {
            step.setStepType(effectiveType);
        }
        step.setConfig(configJson);
        if (request.getExpectedResult() != null) {
            step.setExpectedResult(serializeIfPresent(request.getExpectedResult()));
        }
        if (request.getWeight() != null) {
            step.setWeight(request.getWeight());
        }
        if (request.getTimeoutMs() != null) {
            step.setTimeoutMs(request.getTimeoutMs());
        }
        if (request.getRequired() != null) {
            step.setRequired(request.getRequired());
        }
        log.info("Test step updated: id={}, order={}, type={}", stepId, step.getStepOrder(), step.getStepType());
        return toStepResponse(step);
    }

    @Transactional
    public void deleteStep(UUID assignmentId, UUID ownerId, UUID planId, UUID stepId) {
        requireOwnedAssignment(assignmentId, ownerId);
        requirePlan(planId, assignmentId);
        TestStep step = requireStep(stepId, planId);
        step.setDeletedAt(OffsetDateTime.now());
        testStepRepository.save(step);
        log.info("Test step soft-deleted: id={}, plan={}", stepId, planId);
    }

    // ---------- internal (executor / submission) ----------

    public AssignmentGradingConfigDto internalGradingConfig(UUID assignmentId) {
        Assignment a = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
        return AssignmentGradingConfigDto.builder()
                .id(a.getId())
                .classId(a.getClassId())
                .gradingStrategy(a.getGradingStrategy().name())
                .dockerComposeTemplate(a.getDockerComposeTemplate())
                .dockerComposePort(a.getDockerComposePort())
                .startupTimeoutMs(a.getStartupTimeoutMs())
                .executionTimeoutMs(a.getExecutionTimeoutMs())
                .maxMemoryMb(a.getMaxMemoryMb())
                .maxCpu(a.getMaxCpu())
                .build();
    }

    public List<InternalPlanDto> internalPlans(UUID assignmentId) {
        assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
        List<TestPlan> plans = testPlanRepository.findAllByAssignmentIdOrderBySequenceOrderAsc(assignmentId);
        if (plans.isEmpty()) {
            return List.of();
        }
        List<TestStep> steps = testStepRepository
                .findAllByPlanIdInOrderByStepOrderAsc(plans.stream().map(TestPlan::getId).toList());
        return plans.stream()
                .map(plan -> {
                    List<InternalStepDto> stepDtos = steps.stream()
                            .filter(s -> s.getPlanId().equals(plan.getId()))
                            .map(s -> InternalStepDto.builder()
                                    .id(s.getId())
                                    .stepOrder(s.getStepOrder())
                                    .name(s.getName())
                                    .stepType(s.getStepType().name())
                                    .config(s.getConfig())
                                    .expectedResult(s.getExpectedResult())
                                    .weight(s.getWeight())
                                    .timeoutMs(s.getTimeoutMs())
                                    .required(s.getRequired())
                                    .build())
                            .toList();
                    return InternalPlanDto.builder()
                            .id(plan.getId())
                            .name(plan.getName())
                            .sequenceOrder(plan.getSequenceOrder())
                            .weight(plan.getWeight())
                            .steps(stepDtos)
                            .build();
                })
                .toList();
    }

    public AssignmentExistsResponse internalExists(UUID assignmentId) {
        boolean exists = assignmentRepository.existsByIdAndPublished(assignmentId, true);
        return new AssignmentExistsResponse(exists);
    }

    // ---------- helpers ----------

    private String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }

    private String serializeIfPresent(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to serialize JSON field: " + e.getMessage());
        }
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Stored config/expected JSON failed to parse: {}", e.toString());
            return null;
        }
    }

    private StepResponse toStepResponse(TestStep step) {
        return StepResponse.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .name(step.getName())
                .description(step.getDescription())
                .type(step.getStepType().name())
                .config(parse(step.getConfig()))
                .expectedResult(parse(step.getExpectedResult()))
                .weight(step.getWeight())
                .timeoutMs(step.getTimeoutMs())
                .required(step.getRequired())
                .build();
    }

    private PlanResponse toPlanResponse(TestPlan plan, List<TestStep> steps) {
        return PlanResponse.builder()
                .id(plan.getId())
                .name(plan.getName())
                .description(plan.getDescription())
                .sequenceOrder(plan.getSequenceOrder())
                .weight(plan.getWeight())
                .steps(steps.stream().map(this::toStepResponse).toList())
                .build();
    }
}