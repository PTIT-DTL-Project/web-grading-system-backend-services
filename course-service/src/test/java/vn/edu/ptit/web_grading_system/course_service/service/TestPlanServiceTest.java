package vn.edu.ptit.web_grading_system.course_service.service;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.course_service.dto.internal.AssignmentExistsResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreatePlanRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateStepRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdatePlanRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.request.UpdateStepRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.PlanResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entities.StepType;
import vn.edu.ptit.web_grading_system.course_service.entities.TestPlan;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.TestPlanRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.TestStepRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestPlanServiceTest {

    private static final UUID OWNER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private static final UUID PLAN_ID = UUID.randomUUID();

    private final AssignmentRepository assignmentRepo = Mockito.mock(AssignmentRepository.class);
    private final TestPlanRepository planRepo = Mockito.mock(TestPlanRepository.class);
    private final TestStepRepository stepRepo = Mockito.mock(TestStepRepository.class);
    private final TestPlanService service =
            new TestPlanService(assignmentRepo, planRepo, stepRepo, new ObjectMapper());

    private void stubOwnedAssignment() {
        Mockito.when(assignmentRepo.findByIdAndOwnerId(ASSIGNMENT_ID, OWNER))
                .thenReturn(Optional.of(Assignment.builder().build()));
        // internal exists path shares findById; give it a published row too
        Mockito.when(assignmentRepo.existsByIdAndPublished(ASSIGNMENT_ID, true)).thenReturn(true);
    }

    private TestPlan plan(int seq) {
        return TestPlan.builder().id(PLAN_ID).name("CRUD Book API").sequenceOrder(seq).build();
    }

    @Test
    void createPlan_trimsName_andDefaultsWeight() {
        stubOwnedAssignment();
        Mockito.when(planRepo.existsByAssignmentIdAndSequenceOrder(ASSIGNMENT_ID, 1)).thenReturn(false);
        Mockito.when(planRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        CreatePlanRequest req = new CreatePlanRequest();
        req.setName("  CRUD Book API  ");
        req.setSequenceOrder(1);

        PlanResponse res = service.createPlan(ASSIGNMENT_ID, OWNER, req);
        assertEquals("CRUD Book API", res.getName());
        assertEquals(1, res.getWeight());
        assertTrue(res.getSteps().isEmpty());
    }

    @Test
    void createPlan_duplicateSequenceOrder_throwsDescriptive400() {
        stubOwnedAssignment();
        Mockito.when(planRepo.existsByAssignmentIdAndSequenceOrder(ASSIGNMENT_ID, 1)).thenReturn(true);

        CreatePlanRequest req = new CreatePlanRequest();
        req.setName("dup");
        req.setSequenceOrder(1);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.createPlan(ASSIGNMENT_ID, OWNER, req));
        assertEquals("A plan with sequence_order 1 already exists in this assignment", e.getMessage());
    }

    @Test
    void plansOfOtherOwners_areInvisible404() {
        Mockito.when(assignmentRepo.findByIdAndOwnerId(ASSIGNMENT_ID, OWNER))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.listPlans(ASSIGNMENT_ID, OWNER));
    }

    @Test
    void updatePlan_sequenceConflict_throws400() {
        stubOwnedAssignment();
        Mockito.when(planRepo.findByIdAndAssignmentId(PLAN_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(plan(1)));
        Mockito.when(planRepo.existsByAssignmentIdAndSequenceOrder(ASSIGNMENT_ID, 2)).thenReturn(true);

        UpdatePlanRequest req = new UpdatePlanRequest();
        req.setName("renamed");
        req.setSequenceOrder(2);

        assertThrows(BadRequestException.class,
                () -> service.updatePlan(ASSIGNMENT_ID, OWNER, PLAN_ID, req));
    }

    @Test
    void createStep_stepOrderConflict_namesTheConflict() {
        stubOwnedAssignment();
        Mockito.when(planRepo.findByIdAndAssignmentId(PLAN_ID, ASSIGNMENT_ID))
                .thenReturn(Optional.of(plan(1)));
        Mockito.when(stepRepo.existsByPlanIdAndStepOrder(PLAN_ID, 3)).thenReturn(true);
        Mockito.when(stepRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        var json = new ObjectMapper().createObjectNode();
        json.put("method", "GET");
        json.put("path", "/api/v1/books");
        CreateStepRequest req = new CreateStepRequest();
        req.setStepOrder(3);
        req.setName("dup");
        req.setStepType(StepType.HTTP_REQUEST);
        req.setConfig(json);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.createStep(ASSIGNMENT_ID, OWNER, PLAN_ID, req));
        assertTrue(e.getMessage().contains("step_order 3 already exists"));
    }

    @Test
    void createStep_invalidConfig_propagatesValidationMessage() {
        stubOwnedAssignment();
        Mockito.when(planRepo.findByIdAndAssignmentId(PLAN_ID, ASSIGNMENT_ID)).thenReturn(Optional.of(plan(1)));

        var json = new ObjectMapper().createObjectNode();
        json.put("method", "TELEPORT");
        json.put("path", "/x");
        CreateStepRequest req = new CreateStepRequest();
        req.setStepOrder(9);
        req.setName("bad");
        req.setStepType(StepType.HTTP_REQUEST);
        req.setConfig(json);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.createStep(ASSIGNMENT_ID, OWNER, PLAN_ID, req));
        assertTrue(e.getMessage().contains("unknown method 'TELEPORT'"));
    }

    @Test
    void updateStep_changingTypeWithoutConfig_throws400() {
        stubOwnedAssignment();
        Mockito.when(planRepo.findByIdAndAssignmentId(PLAN_ID, ASSIGNMENT_ID)).thenReturn(Optional.of(plan(1)));
        Mockito.when(stepRepo.findByIdAndPlanId(Mockito.any(), Mockito.eq(PLAN_ID)))
                .thenReturn(Optional.of(vn.edu.ptit.web_grading_system.course_service.entities.TestStep.builder()
                        .planId(PLAN_ID).stepOrder(1).name("s")
                        .stepType(StepType.HTTP_REQUEST)
                        .config("{\"method\":\"GET\",\"path\":\"/x\"}")
                        .build()));

        UpdateStepRequest req = new UpdateStepRequest();
        req.setName("s");
        req.setStepType(StepType.DB_QUERY); // type changed but no config provided

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.updateStep(ASSIGNMENT_ID, OWNER, PLAN_ID,
                        UUID.randomUUID(), req));
        assertTrue(e.getMessage().contains("config is required when changing stepType"));
    }

    @Test
    void stepsAreScopedToTheirPlan_crossPlanStep_is404() {
        stubOwnedAssignment();
        Mockito.when(planRepo.findByIdAndAssignmentId(PLAN_ID, ASSIGNMENT_ID)).thenReturn(Optional.of(plan(1)));
        Mockito.when(stepRepo.findByIdAndPlanId(UUID.randomUUID(), PLAN_ID)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.deleteStep(ASSIGNMENT_ID, OWNER, PLAN_ID, UUID.randomUUID()));
    }

    @Test
    void internal_exists_reflectsPublishedFlag() {
        stubOwnedAssignment();
        AssignmentExistsResponse res = service.internalExists(ASSIGNMENT_ID);
        assertTrue(res.exists());
    }

    @Test
    void createStep_carriesDescriptionIntoResponse() {
        stubOwnedAssignment();
        Mockito.when(planRepo.findByIdAndAssignmentId(PLAN_ID, ASSIGNMENT_ID)).thenReturn(Optional.of(plan(1)));
        Mockito.when(stepRepo.existsByPlanIdAndStepOrder(PLAN_ID, 1)).thenReturn(false);
        Mockito.when(stepRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        var json = new ObjectMapper().createObjectNode();
        json.put("method", "GET");
        json.put("path", "/x");
        CreateStepRequest req = new CreateStepRequest();
        req.setStepOrder(1);
        req.setName("s");
        req.setStepType(StepType.HTTP_REQUEST);
        req.setConfig(json);
        req.setDescription("Remember to expose port 8080");

        var stepRes = service.createStep(ASSIGNMENT_ID, OWNER, PLAN_ID, req);
        assertEquals("Remember to expose port 8080", stepRes.getDescription());
    }

    @Test
    void updateStep_nullDescription_keepsExisting() {
        stubOwnedAssignment();
        Mockito.when(planRepo.findByIdAndAssignmentId(PLAN_ID, ASSIGNMENT_ID)).thenReturn(Optional.of(plan(1)));
        Mockito.when(stepRepo.findByIdAndPlanId(Mockito.any(), Mockito.eq(PLAN_ID)))
                .thenReturn(Optional.of(vn.edu.ptit.web_grading_system.course_service.entities.TestStep.builder()
                        .planId(PLAN_ID).stepOrder(1).name("s").description("old note")
                        .stepType(StepType.HTTP_REQUEST)
                        .config("{\"method\":\"GET\",\"path\":\"/x\"}")
                        .build()));
        Mockito.when(stepRepo.save(Mockito.any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateStepRequest req = new UpdateStepRequest();
        req.setName("s");
        req.setDescription(null); // lecturer leaves note unchanged

        var stepRes = service.updateStep(ASSIGNMENT_ID, OWNER, PLAN_ID, UUID.randomUUID(), req);
        assertEquals("old note", stepRes.getDescription());
    }
}