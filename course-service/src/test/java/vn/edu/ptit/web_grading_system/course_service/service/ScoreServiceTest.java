package vn.edu.ptit.web_grading_system.course_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.course_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentScoresResponse;
import vn.edu.ptit.web_grading_system.course_service.entities.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.entities.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.entities.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.entities.ScoreComponent;
import vn.edu.ptit.web_grading_system.course_service.entities.ScoreComponentType;
import vn.edu.ptit.web_grading_system.course_service.entities.StudentScore;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.ScoreComponentMapper;
import vn.edu.ptit.web_grading_system.course_service.repositories.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.ClassStudentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.CourseClassRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.ScoreComponentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.StudentScoreRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreServiceTest {

    private static final String CODE = "B22DCCN001";

    private final CourseClassRepository classRepo = Mockito.mock(CourseClassRepository.class);
    private final ScoreComponentMapper componentMapper = Mockito.mock(ScoreComponentMapper.class);
    private final ScoreComponentRepository componentRepo = Mockito.mock(ScoreComponentRepository.class);
    private final StudentScoreRepository scoreRepo = Mockito.mock(StudentScoreRepository.class);
    private final ClassStudentRepository studentRepo = Mockito.mock(ClassStudentRepository.class);
    private final AssignmentRepository assignmentRepo = Mockito.mock(AssignmentRepository.class);
    private final ScoreService service = new ScoreService(
            classRepo, componentMapper, componentRepo, scoreRepo, studentRepo,
            assignmentRepo, Mockito.mock(ResultServiceClient.class));

    private void stubOwnedClassWithComponents(UUID classId, UUID ownerId) {
        CourseClass cls = CourseClass.builder().ownerId(ownerId).status(ClassStatus.ACTIVE).build();
        Mockito.when(classRepo.findByIdAndOwnerId(classId, ownerId)).thenReturn(Optional.of(cls));

        ScoreComponent attendance = ScoreComponent.builder()
                .id(UUID.randomUUID()).type(ScoreComponentType.ATTENDANCE)
                .weight(new BigDecimal("0.5")).build();
        ScoreComponent finalExam = ScoreComponent.builder()
                .id(UUID.randomUUID()).type(ScoreComponentType.FINAL_EXAM)
                .weight(new BigDecimal("0.5")).build();
        Mockito.when(componentRepo.findAllByClassId(classId)).thenReturn(List.of(attendance, finalExam));

        Mockito.when(scoreRepo.findAllByClassIdAndStudentCode(Mockito.eq(classId), Mockito.any()))
                .thenReturn(List.of());

        ClassStudent rosterEntry = ClassStudent.builder().studentCode(CODE).build();
        Mockito.when(studentRepo.findByClassIdAndStudentCode(classId, CODE))
                .thenReturn(Optional.of(rosterEntry));
        Mockito.when(studentRepo.findByClassIdAndStudentCode(Mockito.eq(classId), Mockito.argThat(c -> !CODE.equals(c))))
                .thenReturn(Optional.empty());
    }

    private void stubManualScores(UUID classId) {
        // resolved via the same findAllByClassId list built above; scores matched by component id order
        List<ScoreComponent> comps = componentRepo.findAllByClassId(classId);
        StudentScore s1 = StudentScore.builder()
                .componentId(comps.get(0).getId())
                .score(new BigDecimal("9.00"))
                .build();
        StudentScore s2 = StudentScore.builder()
                .componentId(comps.get(1).getId())
                .score(new BigDecimal("8.00"))
                .build();
        Mockito.when(scoreRepo.findAllByClassIdAndStudentCode(classId, CODE)).thenReturn(List.of(s1, s2));
    }

    @Test
    void getStudentScores_passingTotal_mapsLetterGradeAndGpa() {
        UUID classId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedClassWithComponents(classId, ownerId);
        stubManualScores(classId);

        StudentScoresResponse res = service.getStudentScores(classId, ownerId, CODE);

        assertEquals(new BigDecimal("8.50"), res.getTotal());
        assertEquals("A", res.getLetterGrade());
        assertEquals(new BigDecimal("3.7"), res.getGpa());
    }

    @Test
    void getStudentScores_zeroSubScore_forcesF_despitePassingTotal() {
        UUID classId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedClassWithComponents(classId, ownerId);
        List<ScoreComponent> comps = componentRepo.findAllByClassId(classId);
        StudentScore perfect = StudentScore.builder()
                .componentId(comps.get(0).getId()).score(new BigDecimal("10.00")).build();
        StudentScore zero = StudentScore.builder()
                .componentId(comps.get(1).getId()).score(BigDecimal.ZERO).build();
        Mockito.when(scoreRepo.findAllByClassIdAndStudentCode(classId, CODE))
                .thenReturn(List.of(perfect, zero));

        StudentScoresResponse res = service.getStudentScores(classId, ownerId, CODE);

        assertEquals(new BigDecimal("5.00"), res.getTotal()); // >= 4 but sub-score 0
        assertEquals("F", res.getLetterGrade());
        assertEquals(BigDecimal.ZERO, res.getGpa());
    }

    @Test
    void getStudentScores_incomplete_returnsNullGrade() {
        UUID classId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedClassWithComponents(classId, ownerId);

        StudentScoresResponse res = service.getStudentScores(classId, ownerId, CODE);

        assertNull(res.getTotal());
        assertNull(res.getLetterGrade());
        assertNull(res.getGpa());
    }

    @Test
    void getStudentScores_throws404_whenStudentNotInRoster() {
        UUID classId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedClassWithComponents(classId, ownerId);

        ResourceNotFoundException e = assertThrows(ResourceNotFoundException.class,
                () -> service.getStudentScores(classId, ownerId, "B22DCCN502"));
        assertEquals("Student 'B22DCCN502' not found in class " + classId, e.getMessage());
        Mockito.verify(scoreRepo, Mockito.never())
                .findAllByClassIdAndStudentCode(Mockito.any(), Mockito.any());
    }

    @Test
    void getStudentScores_trimsPaddedStudentCode_andRespondsWithTrimmed() {
        UUID classId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        stubOwnedClassWithComponents(classId, ownerId);

        StudentScoresResponse res = service.getStudentScores(classId, ownerId, " " + CODE + " ");
        assertEquals(CODE, res.getStudentCode());
        Mockito.verify(studentRepo, Mockito.atLeastOnce()).findByClassIdAndStudentCode(classId, CODE);
    }
}