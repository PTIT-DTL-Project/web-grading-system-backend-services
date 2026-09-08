package vn.edu.ptit.web_grading_system.course_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import tools.jackson.databind.ObjectMapper;
import vn.edu.ptit.web_grading_system.course_service.dto.response.PlanResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entities.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.entities.StepType;
import vn.edu.ptit.web_grading_system.course_service.entities.TestPlan;
import vn.edu.ptit.web_grading_system.course_service.entities.TestStep;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.ClassStudentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.TestPlanRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.TestStepRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentAssignmentServiceTest {

    private final UUID STUDENT = UUID.randomUUID();
    private final UUID CLASS_ID = UUID.randomUUID();
    private final UUID ASSIGNMENT_ID = UUID.randomUUID();
    private final UUID PLAN_ID = UUID.randomUUID();

    private final AssignmentRepository assignmentRepo = Mockito.mock(AssignmentRepository.class);
    private final ClassStudentRepository classStudentRepo = Mockito.mock(ClassStudentRepository.class);
    private final TestPlanRepository planRepo = Mockito.mock(TestPlanRepository.class);
    private final TestStepRepository stepRepo = Mockito.mock(TestStepRepository.class);
    private final StudentAssignmentService service = new StudentAssignmentService(
            assignmentRepo, classStudentRepo, planRepo, stepRepo,
            Mockito.mock(vn.edu.ptit.web_grading_system.course_service.mapper.AssignmentMapper.class),
            new ObjectMapper());

    private void stubVisible() {
        Mockito.when(classStudentRepo.findAllByStudentUserId(STUDENT))
                .thenReturn(List.of(ClassStudent.builder().classId(CLASS_ID).build()));
        Mockito.when(assignmentRepo.findById(ASSIGNMENT_ID))
                .thenReturn(Optional.of(Assignment.builder()
                        .classId(CLASS_ID).published(true).build()));
    }

    @Test
    void listPlans_sanitizesStudentView() {
        stubVisible();
        Mockito.when(planRepo.findAllByAssignmentIdOrderBySequenceOrderAsc(ASSIGNMENT_ID))
                .thenReturn(List.of(TestPlan.builder().id(PLAN_ID).name("Basic").sequenceOrder(1).build()));
        TestStep http = TestStep.builder().id(UUID.randomUUID()).planId(PLAN_ID).stepOrder(1).name("hit api")
                .description("Call the endpoint").stepType(StepType.HTTP_REQUEST)
                .config("{\"method\":\"GET\",\"path\":\"/x\",\"connection\":{\"password\":\"secret\"},\"extract\":[{\"name\":\"id\"}]}")
                .build();
        TestStep delay = TestStep.builder().id(UUID.randomUUID()).planId(PLAN_ID).stepOrder(2).name("wait")
                .stepType(StepType.DELAY).config("{\"duration_ms\":1000}").build();
        TestStep db = TestStep.builder().id(UUID.randomUUID()).planId(PLAN_ID).stepOrder(3).name("check db")
                .stepType(StepType.DB_QUERY)
                .config("{\"query\":\"select 1\",\"connection\":{\"password\":\"x\"},\"expected\":{\"row_count\":1}}")
                .build();
        Mockito.when(stepRepo.findAllByPlanIdInOrderByStepOrderAsc(List.of(PLAN_ID)))
                .thenReturn(List.of(http, delay, db));

        List<PlanResponse> plans = service.listPlans(STUDENT, ASSIGNMENT_ID);

        assertEquals(1, plans.size());
        assertEquals(2, plans.get(0).getSteps().size()); // DELAY dropped
        assertEquals("Call the endpoint", plans.get(0).getSteps().get(0).getDescription());
        var cfg = plans.get(0).getSteps().get(0).getConfig();
        assertNotNull(cfg);
        assertFalse(cfg.has("connection"), "credentials must be stripped");
        assertFalse(cfg.has("extract"), "extract must be stripped");
        assertFalse(cfg.has("expected"), "expected must be stripped");
    }

    @Test
    void hiddenAssignment_is404() {
        Mockito.when(classStudentRepo.findAllByStudentUserId(STUDENT))
                .thenReturn(List.of(ClassStudent.builder().classId(CLASS_ID).build()));
        Mockito.when(assignmentRepo.findById(ASSIGNMENT_ID))
                .thenReturn(Optional.of(Assignment.builder().classId(CLASS_ID).published(false).build()));

        assertThrows(ResourceNotFoundException.class, () -> service.listPlans(STUDENT, ASSIGNMENT_ID));
    }

    @Test
    void notEnrolled_is404() {
        Mockito.when(classStudentRepo.findAllByStudentUserId(STUDENT))
                .thenReturn(List.of()); // student in no class

        assertThrows(ResourceNotFoundException.class, () -> service.listPlans(STUDENT, ASSIGNMENT_ID));
    }
}
