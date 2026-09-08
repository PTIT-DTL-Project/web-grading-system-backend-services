package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import vn.edu.ptit.web_grading_system.course_service.dto.response.AssignmentResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.PlanResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StepResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entities.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.entities.StepType;
import vn.edu.ptit.web_grading_system.course_service.entities.TestPlan;
import vn.edu.ptit.web_grading_system.course_service.entities.TestStep;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.AssignmentMapper;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.ClassStudentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.TestPlanRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.TestStepRepository;

import java.util.List;
import java.util.UUID;

/**
 * Student-facing read path. Students see only published assignments of classes they
 * are enrolled in (class_students.student_user_id). Steps are sanitized: grading
 * plumbing (DELAY/EXTRACT) is dropped and credential/expected fields
 * (connection, extract, expected) are stripped from config before returning.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentAssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final ClassStudentRepository classStudentRepository;
    private final TestPlanRepository testPlanRepository;
    private final TestStepRepository testStepRepository;
    private final AssignmentMapper assignmentMapper;
    private final ObjectMapper objectMapper;

    private List<UUID> enrolledClassIds(UUID studentId) {
        return classStudentRepository.findAllByStudentUserId(studentId).stream()
                .map(ClassStudent::getClassId)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<AssignmentResponse> listAssignments(UUID studentId, UUID classId,
                                                    String search, Pageable pageable) {
        List<UUID> classIds = enrolledClassIds(studentId);
        if (classIds.isEmpty()) {
            return Page.empty(pageable);
        }
        return assignmentRepository
                .findPublishedForStudent(classIds, classId, search, pageable)
                .map(assignmentMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public AssignmentResponse getAssignment(UUID studentId, UUID assignmentId) {
        requireVisible(studentId, assignmentId);
        return assignmentMapper.toResponse(assignmentRepository.getReferenceById(assignmentId));
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans(UUID studentId, UUID assignmentId) {
        requireVisible(studentId, assignmentId);
        List<TestPlan> plans = testPlanRepository
                .findAllByAssignmentIdOrderBySequenceOrderAsc(assignmentId);
        List<TestStep> steps = testStepRepository
                .findAllByPlanIdInOrderByStepOrderAsc(plans.stream().map(TestPlan::getId).toList());
        return plans.stream()
                .map(plan -> PlanResponse.builder()
                        .id(plan.getId())
                        .name(plan.getName())
                        .description(plan.getDescription())
                        .sequenceOrder(plan.getSequenceOrder())
                        .weight(plan.getWeight())
                        .steps(steps.stream()
                                .filter(s -> s.getPlanId().equals(plan.getId()))
                                .filter(s -> s.getStepType() != StepType.DELAY
                                        && s.getStepType() != StepType.EXTRACT)
                                .map(this::toSanitizedStep)
                                .toList())
                        .build())
                .toList();
    }

    private StepResponse toSanitizedStep(TestStep step) {
        return StepResponse.builder()
                .id(step.getId())
                .stepOrder(step.getStepOrder())
                .name(step.getName())
                .description(step.getDescription())
                .type(step.getStepType().name())
                .config(sanitizeConfig(step.getConfig()))
                .weight(step.getWeight())
                .timeoutMs(step.getTimeoutMs())
                .required(step.getRequired())
                .build();
    }

    private JsonNode sanitizeConfig(String configJson) {
        JsonNode node = parse(configJson);
        if (node == null || !node.isObject()) {
            return node;
        }
        ObjectNode copy = (ObjectNode) node.deepCopy();
        copy.remove(List.of("connection", "extract", "expected"));
        return copy;
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.warn("Stored step config failed to parse: {}", e.toString());
            return null;
        }
    }

    private Assignment requireVisible(UUID studentId, UUID assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
        if (!Boolean.TRUE.equals(assignment.getPublished())
                || !enrolledClassIds(studentId).contains(assignment.getClassId())) {
            throw new ResourceNotFoundException("Assignment not found: " + assignmentId);
        }
        return assignment;
    }
}
