package vn.edu.ptit.web_grading_system.course_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateAssignmentRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateAssignmentRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.AssignmentResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entities.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.entities.DockerImage;
import vn.edu.ptit.web_grading_system.course_service.entities.GradingStrategy;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.AssignmentMapper;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentDockerImageRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.CourseClassRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.DockerImageRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssignmentServiceTest {

    private static final UUID OWNER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID CLASS_ID = UUID.randomUUID();
    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();

    private final CourseClassRepository classRepo = Mockito.mock(CourseClassRepository.class);
    private final AssignmentRepository assignmentRepo = Mockito.mock(AssignmentRepository.class);
    private final AssignmentMapper mapper = Mockito.mock(AssignmentMapper.class);
    private final AssignmentDockerImageRepository imageLinkRepo = Mockito.mock(AssignmentDockerImageRepository.class);
    private final DockerImageRepository dockerImageRepo = Mockito.mock(DockerImageRepository.class);
    private final AssignmentService service = new AssignmentService(classRepo, assignmentRepo, mapper, imageLinkRepo, dockerImageRepo);

    private CreateAssignmentRequest createRequest(GradingStrategy strategy) {
        CreateAssignmentRequest req = new CreateAssignmentRequest();
        req.setTitle("  Lab 01 Docker  ");
        req.setClassId(CLASS_ID);
        req.setGradingStrategy(strategy);
        if (strategy == GradingStrategy.LECTURER_DOCKER_COMPOSE) {
            req.setDockerComposeTemplate("services:{}");
        }
        return req;
    }

    private void stubOwnedClass() {
        Mockito.when(classRepo.findByIdAndOwnerId(CLASS_ID, OWNER))
                .thenReturn(Optional.of(CourseClass.builder().build()));
        Mockito.when(assignmentRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubOwnedAssignment(boolean published) {
        Mockito.when(assignmentRepo.findByIdAndOwnerId(ASSIGNMENT_ID, OWNER))
                .thenReturn(Optional.of(Assignment.builder()
                        .id(ASSIGNMENT_ID)
                        .ownerId(OWNER)
                        .classId(CLASS_ID)
                        .title("Lab 01")
                        .gradingStrategy(GradingStrategy.STUDENT_DOCKER_COMPOSE)
                        .published(published)
                        .build()));
    }

    @Test
    void create_trimsTitle_andPersistsUnpublished() {
        stubOwnedClass();
        Mockito.when(assignmentRepo.existsByOwnerIdAndClassIdAndTitle(OWNER, CLASS_ID, "Lab 01 Docker"))
                .thenReturn(false);
        Mockito.when(mapper.toResponse(Mockito.any())).thenReturn(AssignmentResponse.builder().build());

        service.create(OWNER, createRequest(GradingStrategy.STUDENT_DOCKER_COMPOSE));

        Mockito.verify(assignmentRepo).save(Mockito.argThat(a ->
                "Lab 01 Docker".equals(a.getTitle())
                        && Boolean.FALSE.equals(a.getPublished())
                        && OWNER.equals(a.getOwnerId())));
    }

    @Test
    void create_duplicateTitleInSameClass_throwsDescriptive400() {
        stubOwnedClass();
        Mockito.when(assignmentRepo.existsByOwnerIdAndClassIdAndTitle(OWNER, CLASS_ID, "Lab 01 Docker"))
                .thenReturn(true);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.create(OWNER, createRequest(GradingStrategy.STUDENT_DOCKER_COMPOSE)));
        assertEquals("Assignment 'Lab 01 Docker' already exists in this class", e.getMessage());
        Mockito.verify(assignmentRepo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void create_classNotOwned_throws404() {
        Mockito.when(classRepo.findByIdAndOwnerId(Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.create(OWNER, createRequest(GradingStrategy.STUDENT_DOCKER_COMPOSE)));
    }

    @Test
    void create_lecturerStrategyRequiresTemplate() {
        stubOwnedClass();
        Mockito.when(assignmentRepo.existsByOwnerIdAndClassIdAndTitle(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(false);

        CreateAssignmentRequest req = createRequest(GradingStrategy.LECTURER_DOCKER_COMPOSE);
        req.setDockerComposeTemplate(null); // missing template must be rejected

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.create(OWNER, req));
        assertTrue(e.getMessage().contains("dockerComposeTemplate is required"));
    }

    @Test
    void update_rejectsMovingClass() {
        stubOwnedAssignment(false);
        UpdateAssignmentRequest req = UpdateAssignmentRequest.builder()
                .title("New title")
                .classId(UUID.randomUUID()) // different class
                .build();

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.update(ASSIGNMENT_ID, OWNER, req));
        assertEquals("class_id cannot be changed after creation", e.getMessage());
    }

    @Test
    void update_duplicateTitleExcludingSelf_throws400() {
        stubOwnedAssignment(false);
        Mockito.when(assignmentRepo.existsByOwnerIdAndClassIdAndTitleAndIdNot(OWNER, CLASS_ID, "Dup", ASSIGNMENT_ID))
                .thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> service.update(ASSIGNMENT_ID, OWNER,
                        UpdateAssignmentRequest.builder().title("Dup").build()));
    }

    @Test
    void update_switchingToLecturerStrategy_requiresTemplate() {
        stubOwnedAssignment(false); // stored STUDENT_DOCKER_COMPOSE, no template
        UpdateAssignmentRequest req = UpdateAssignmentRequest.builder()
                .title("Lab 01")
                .gradingStrategy(GradingStrategy.LECTURER_DOCKER_COMPOSE)
                .build();

        assertThrows(BadRequestException.class,
                () -> service.update(ASSIGNMENT_ID, OWNER, req));
    }

    @Test
    void publish_isIdempotent_secondCallDoesNotSave() {
        stubOwnedAssignment(true); // already published
        Mockito.when(mapper.toResponse(Mockito.any())).thenReturn(AssignmentResponse.builder().build());

        assertNotNull(service.publish(ASSIGNMENT_ID, OWNER));
        Mockito.verify(assignmentRepo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void delete_softDeletes_neverHardDeletes() {
        stubOwnedAssignment(false);

        service.delete(ASSIGNMENT_ID, OWNER);

        Mockito.verify(assignmentRepo).save(Mockito.argThat(a -> a.getDeletedAt() != null));
        Mockito.verify(assignmentRepo, Mockito.never()).delete(Mockito.any());
    }

    // Review: 2026-09-27, Pullfrog PR #N — sync must de-duplicate and never
    // misreport an unknown id as "unknown or soft-deleted" (IN-clause dedup).
    @Test
    void syncAssignmentImages_unknownId_throws400() {
        stubOwnedAssignment(true);
        UUID unknown = UUID.randomUUID();
        Mockito.when(dockerImageRepo.findAllByIdIn(List.of(unknown)))
                .thenReturn(List.of());
        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.syncAssignmentImages(ASSIGNMENT_ID, OWNER, List.of(unknown)));
        assertTrue(e.getMessage().contains("unknown or soft-deleted"));
    }

    @Test
    void syncAssignmentImages_duplicateCollapsed_insertsOnce() {
        stubOwnedAssignment(true);
        UUID img = UUID.randomUUID();
        Mockito.when(dockerImageRepo.findAllByIdIn(List.of(img)))
                .thenReturn(List.of(DockerImage.builder().id(img).ownerId(OWNER).build()));
        service.syncAssignmentImages(ASSIGNMENT_ID, OWNER, List.of(img, img));
        Mockito.verify(imageLinkRepo, Mockito.times(1)).save(any());
    }

    @Test
    void syncAssignmentImages_emptyClearsLinks() {
        stubOwnedAssignment(true);
        service.syncAssignmentImages(ASSIGNMENT_ID, OWNER, List.of());
        Mockito.verify(imageLinkRepo).softDeleteByAssignmentId(
                eq(ASSIGNMENT_ID), any(java.time.OffsetDateTime.class));
    }

    // Review: 2026-09-27, Pullfrog PR #20 — sync must refuse to attach a
    // lecturer's image to another lecturer's assignment.
    @Test
    void syncAssignmentImages_foreignImage_throws404() {
        stubOwnedAssignment(true);
        UUID foreign = UUID.randomUUID();
        Mockito.when(dockerImageRepo.findAllByIdIn(List.of(foreign)))
                .thenReturn(List.of(DockerImage.builder()
                        .id(foreign).name("foreign").imageUrl("foreign:1")
                        .ownerId(UUID.randomUUID()).build()));
        assertThrows(ResourceNotFoundException.class,
                () -> service.syncAssignmentImages(ASSIGNMENT_ID, OWNER, List.of(foreign)));
    }

    // Review: 2026-09-27, Pullfrog PR #20 — deleting an assignment must also
    // remove its image links so the images remain deletable.
    @Test
    void delete_softDeletesLinks() {
        stubOwnedAssignment(false);
        service.delete(ASSIGNMENT_ID, OWNER);
        Mockito.verify(imageLinkRepo).softDeleteByAssignmentId(
                eq(ASSIGNMENT_ID), any(java.time.OffsetDateTime.class));
    }
}