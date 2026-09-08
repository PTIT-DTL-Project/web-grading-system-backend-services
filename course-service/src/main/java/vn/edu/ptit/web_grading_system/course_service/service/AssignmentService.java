package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateAssignmentRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateAssignmentRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.AssignmentResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entities.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.entities.GradingStrategy;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.AssignmentMapper;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.CourseClassRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final CourseClassRepository courseClassRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentMapper assignmentMapper;

    private CourseClass requireOwnedClass(UUID ownerId, UUID classId) {
        return courseClassRepository.findByIdAndOwnerId(classId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Class not found: " + classId));
    }

    private Assignment requireOwnedAssignment(UUID id, UUID ownerId) {
        return assignmentRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + id));
    }

    private void validateStrategy(GradingStrategy strategy, String template) {
        if (strategy == GradingStrategy.LECTURER_DOCKER_COMPOSE
                && !StringUtils.hasText(template)) {
            throw new BadRequestException(
                    "dockerComposeTemplate is required for LECTURER_DOCKER_COMPOSE strategy");
        }
    }

    @Transactional
    public AssignmentResponse create(UUID ownerId, CreateAssignmentRequest request) {
        requireOwnedClass(ownerId, request.getClassId());
        String title = request.getTitle().trim();
        if (assignmentRepository.existsByOwnerIdAndClassIdAndTitle(ownerId, request.getClassId(), title)) {
            log.warn("Duplicate assignment creation rejected: owner={}, class={}, title={}",
                    ownerId, request.getClassId(), title);
            throw new BadRequestException(
                    "Assignment '%s' already exists in this class".formatted(title));
        }
        validateStrategy(request.getGradingStrategy(), request.getDockerComposeTemplate());

        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .classId(request.getClassId())
                .ownerId(ownerId)
                .title(title)
                .description(request.getDescription())
                .gradingStrategy(request.getGradingStrategy())
                .dockerComposeTemplate(request.getDockerComposeTemplate())
                .dockerComposePort(request.getDockerComposePort())
                .startupTimeoutMs(request.getStartupTimeoutMs())
                .executionTimeoutMs(request.getExecutionTimeoutMs())
                .maxMemoryMb(request.getMaxMemoryMb())
                .maxCpu(request.getMaxCpu())
                .published(false)
                .build());
        log.info("Assignment created: id={}, title={}, class={}", assignment.getId(), title, request.getClassId());
        return assignmentMapper.toResponse(assignment);
    }

    public Page<AssignmentResponse> listMine(UUID ownerId, UUID classId, Boolean published,
                                             String search, Pageable pageable) {
        String q = StringUtils.hasText(search) ? search.trim() : null;
        return assignmentRepository.findMine(ownerId, classId, published, q, pageable)
                .map(assignmentMapper::toResponse);
    }

    public AssignmentResponse getById(UUID id, UUID ownerId) {
        return assignmentMapper.toResponse(requireOwnedAssignment(id, ownerId));
    }

    @Transactional
    public AssignmentResponse update(UUID id, UUID ownerId, UpdateAssignmentRequest request) {
        Assignment assignment = requireOwnedAssignment(id, ownerId);
        if (request.getClassId() != null && !request.getClassId().equals(assignment.getClassId())) {
            throw new BadRequestException("class_id cannot be changed after creation");
        }
        validateStrategy(
                request.getGradingStrategy() != null ? request.getGradingStrategy() : assignment.getGradingStrategy(),
                request.getDockerComposeTemplate() != null
                        ? request.getDockerComposeTemplate()
                        : assignment.getDockerComposeTemplate());

        String title = request.getTitle().trim();
        UUID effectiveClassId = request.getClassId() != null ? request.getClassId() : assignment.getClassId();
        if (assignmentRepository.existsByOwnerIdAndClassIdAndTitleAndIdNot(ownerId, effectiveClassId, title, id)) {
            throw new BadRequestException(
                    "Assignment '%s' already exists in this class".formatted(title));
        }
        GradingStrategy strategy = request.getGradingStrategy() != null
                ? request.getGradingStrategy()
                : assignment.getGradingStrategy();

        assignment.setTitle(title);
        assignment.setDescription(request.getDescription() != null ? request.getDescription() : assignment.getDescription());
        if (request.getClassId() != null) {
            assignment.setClassId(request.getClassId());
        }
        assignment.setGradingStrategy(strategy);
        assignment.setDockerComposeTemplate(request.getDockerComposeTemplate() != null
                ? request.getDockerComposeTemplate()
                : assignment.getDockerComposeTemplate());
        if (request.getDockerComposePort() != null) {
            assignment.setDockerComposePort(request.getDockerComposePort());
        }
        if (request.getStartupTimeoutMs() != null) {
            assignment.setStartupTimeoutMs(request.getStartupTimeoutMs());
        }
        if (request.getExecutionTimeoutMs() != null) {
            assignment.setExecutionTimeoutMs(request.getExecutionTimeoutMs());
        }
        if (request.getMaxMemoryMb() != null) {
            assignment.setMaxMemoryMb(request.getMaxMemoryMb());
        }
        if (request.getMaxCpu() != null) {
            assignment.setMaxCpu(request.getMaxCpu());
        }
        log.info("Assignment updated: id={}, title={}", id, title);
        return assignmentMapper.toResponse(assignmentRepository.save(assignment));
    }

    @Transactional
    public void delete(UUID id, UUID ownerId) {
        Assignment assignment = requireOwnedAssignment(id, ownerId);
        assignment.setDeletedAt(OffsetDateTime.now());
        assignmentRepository.save(assignment);
        log.info("Assignment soft-deleted: id={}, title={}", id, assignment.getTitle());
    }

    @Transactional
    public AssignmentResponse publish(UUID id, UUID ownerId) {
        Assignment assignment = requireOwnedAssignment(id, ownerId);
        if (!Boolean.TRUE.equals(assignment.getPublished())) {
            assignment.setPublished(true);
            assignment = assignmentRepository.save(assignment);
            log.info("Assignment published: id={}, title={}", id, assignment.getTitle());
        } else {
            log.info("Assignment already published: id={}", id);
        }
        return assignmentMapper.toResponse(assignment);
    }
}