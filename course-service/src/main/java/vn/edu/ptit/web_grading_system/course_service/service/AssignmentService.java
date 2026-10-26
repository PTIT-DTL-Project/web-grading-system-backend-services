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
import vn.edu.ptit.web_grading_system.course_service.entity.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entity.AssignmentPurpose;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.entity.GradingStrategy;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.Constant;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.AssignmentMapper;
import vn.edu.ptit.web_grading_system.course_service.repository.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.CourseClassRepository;

import java.time.OffsetDateTime;
import java.util.List;
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
        AssignmentPurpose purpose = request.getPurpose() != null ? request.getPurpose() : AssignmentPurpose.GRADED;
        UUID classId = request.getClassId();
        String topic = StringUtils.hasText(request.getTopic()) ? request.getTopic().trim() : null;

        // Review: 2026-10-26, practice-exercises feature
        // GRADED exercises belong to a class the lecturer owns; PRACTICE exercises are class-independent
        // (classId must be NULL) so that, once published, they are visible to every student regardless of
        // enrollment. Enforcing this at creation keeps the class/practice boundary immutable.
        if (purpose == AssignmentPurpose.GRADED) {
            if (classId == null) {
                throw new BadRequestException("classId is required for GRADED assignments");
            }
            requireOwnedClass(ownerId, classId);
        } else if (classId != null) {
            throw new BadRequestException("classId must be null for PRACTICE assignments");
        }

        String title = request.getTitle().trim();
        // Review: 2026-10-26, practice-exercises feature
        // Title uniqueness is owner-scoped across BOTH purposes (GRADED and PRACTICE), not per-class, so a
        // lecturer cannot reuse the same title for a graded exercise and a practice exercise.
        if (assignmentRepository.existsByOwnerIdAndTitleIgnoreCase(ownerId, title)) {
            log.warn("Duplicate assignment creation rejected: owner={}, title={}, purpose={}",
                    ownerId, title, purpose);
            throw new BadRequestException(
                    "Assignment '%s' already exists".formatted(title));
        }
        validateStrategy(request.getGradingStrategy(), request.getDockerComposeTemplate());

        Assignment assignment = assignmentRepository.save(Assignment.builder()
                .classId(classId)
                .ownerId(ownerId)
                .title(title)
                .description(request.getDescription())
                .purpose(purpose)
                .topic(topic)
                .gradingStrategy(request.getGradingStrategy())
                .dockerComposeTemplate(request.getDockerComposeTemplate())
                .dockerComposePort(request.getDockerComposePort())
                .startupTimeoutMs(request.getStartupTimeoutMs())
                .executionTimeoutMs(request.getExecutionTimeoutMs())
                .maxMemoryMb(request.getMaxMemoryMb())
                .maxCpu(request.getMaxCpu())
                .published(false)
                .build());
        log.info("Assignment created: id={}, title={}, class={}, purpose={}", assignment.getId(), title, classId, purpose);
        return assignmentMapper.toResponse(assignment);
    }

    public Page<AssignmentResponse> listMine(UUID ownerId, UUID classId, AssignmentPurpose purpose,
                                             String topic, Boolean published, String search, Pageable pageable) {
        String q = StringUtils.hasText(search) ? search.trim() : null;
        String t = StringUtils.hasText(topic) ? topic.trim() : null;
        return assignmentRepository.findMine(ownerId, classId, purpose, t, published, q, pageable)
                .map(assignmentMapper::toResponse);
    }

    public List<String> listTopics(UUID ownerId, AssignmentPurpose purpose) {
        return assignmentRepository.findDistinctTopics(ownerId, purpose);
    }

    public AssignmentResponse getById(UUID id, UUID ownerId) {
        return assignmentMapper.toResponse(requireOwnedAssignment(id, ownerId));
    }

    @Transactional
    public AssignmentResponse update(UUID id, UUID ownerId, UpdateAssignmentRequest request) {
        Assignment assignment = requireOwnedAssignment(id, ownerId);
        // Review: 2026-10-26, practice-exercises feature
        // classId is immutable after creation: moving an assignment between classes (or into/out of
        // practice) would break the class/practice boundary and orphan scores/results. The effective
        // class is always the stored one.
        if (request.getClassId() != null && !request.getClassId().equals(assignment.getClassId())) {
            throw new BadRequestException("class_id cannot be changed after creation");
        }
        validateStrategy(
                request.getGradingStrategy() != null ? request.getGradingStrategy() : assignment.getGradingStrategy(),
                request.getDockerComposeTemplate() != null
                        ? request.getDockerComposeTemplate()
                        : assignment.getDockerComposeTemplate());

        String title = request.getTitle().trim();
        // class_id cannot change, so effective class is always the stored one.
        if (assignmentRepository.existsByOwnerIdAndTitleIgnoreCaseAndIdNot(ownerId, title, id)) {
            throw new BadRequestException(
                    "Assignment '%s' already exists".formatted(title));
        }
        GradingStrategy strategy = request.getGradingStrategy() != null
                ? request.getGradingStrategy()
                : assignment.getGradingStrategy();

        assignment.setTitle(title);
        // Review: 2026-10-26, practice-exercises feature
        // topic is mandatory for every assignment (GRADED and PRACTICE). Legacy rows were backfilled to
        // 'Chung' in V7 migration so every row has a non-null topic; updates must keep it non-blank.
        assignment.setTopic(StringUtils.hasText(request.getTopic()) ? request.getTopic().trim() : null);
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