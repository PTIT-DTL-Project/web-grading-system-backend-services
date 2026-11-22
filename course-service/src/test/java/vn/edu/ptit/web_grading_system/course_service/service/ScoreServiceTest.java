package vn.edu.ptit.web_grading_system.course_service.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.edu.ptit.web_grading_system.course_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentScoresResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.TranscriptEntryResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStatus;
import vn.edu.ptit.web_grading_system.course_service.entity.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entity.AssignmentPurpose;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.entity.ScoreComponent;
import vn.edu.ptit.web_grading_system.course_service.entity.ScoreComponentType;
import vn.edu.ptit.web_grading_system.course_service.entity.StudentScore;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.ScoreComponentMapper;
import vn.edu.ptit.web_grading_system.course_service.repository.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.ClassStudentRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.CourseClassRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.ScoreComponentRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.StudentScoreRepository;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ScoreServiceTest {

    private static final String CODE = "B22DCCN001";
    private static final java.util.UUID OWNER = java.util.UUID.randomUUID();
    private static final java.util.UUID ATTENDANCE_ID = java.util.UUID.randomUUID();
    private static final java.util.UUID EXERCISE_ID = java.util.UUID.randomUUID();
    private static final java.util.UUID FINAL_EXAM_ID = java.util.UUID.randomUUID();
    private static final java.util.UUID STUDENT_USER_ID = java.util.UUID.randomUUID();

    private final CourseClassRepository classRepo = Mockito.mock(CourseClassRepository.class);
    private final ScoreComponentMapper componentMapper = Mockito.mock(ScoreComponentMapper.class);
    private final ScoreComponentRepository componentRepo = Mockito.mock(ScoreComponentRepository.class);
    private final StudentScoreRepository scoreRepo = Mockito.mock(StudentScoreRepository.class);
    private final ClassStudentRepository studentRepo = Mockito.mock(ClassStudentRepository.class);
    private final AssignmentRepository assignmentRepo = Mockito.mock(AssignmentRepository.class);
    private final ResultServiceClient resultServiceClient = Mockito.mock(ResultServiceClient.class);
    private final Tracer tracer = Mockito.mock(Tracer.class);
    private final Span span = Mockito.mock(Span.class);
    private final Tracer.SpanInScope spanInScope = Mockito.mock(Tracer.SpanInScope.class);

    {
        when(tracer.nextSpan()).thenReturn(span);
        when(span.name(any(String.class))).thenReturn(span);
        when(span.start()).thenReturn(span);
        when(tracer.withSpan(any(Span.class))).thenReturn(spanInScope);
    }

    private final ScoreService service = new ScoreService(
            classRepo, componentMapper, componentRepo, scoreRepo, studentRepo,
            assignmentRepo, resultServiceClient, tracer);

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

    // Review: 2026-10-09 — student "my scores" tab (FE StudentScoresTab).
    @Test
    void getMyScores_resolvesCodeFromIdentity() {
        UUID classId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        stubOwnedClassWithComponents(classId, UUID.randomUUID());
        stubManualScores(classId);
        Mockito.when(studentRepo.findAllByStudentUserId(studentId))
                .thenReturn(List.of(ClassStudent.builder().classId(classId).studentCode(CODE).build()));

        StudentScoresResponse res = service.getMyScores(classId, studentId);

        assertEquals(CODE, res.getStudentCode());
        assertEquals(new BigDecimal("8.50"), res.getTotal());
        assertEquals("A", res.getLetterGrade());
    }

    @Test
    void getMyScores_notEnrolled_is404() {
        UUID classId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID otherClass = UUID.randomUUID();
        Mockito.when(studentRepo.findAllByStudentUserId(studentId))
                .thenReturn(List.of(ClassStudent.builder().classId(otherClass).studentCode(CODE).build()));

        assertThrows(ResourceNotFoundException.class, () -> service.getMyScores(classId, studentId));
        Mockito.verify(componentRepo, Mockito.never()).findAllByClassId(Mockito.any());
    }

    @Test
    void getMyScores_unlinkedUser_is404() {
        UUID classId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        Mockito.when(studentRepo.findAllByStudentUserId(studentId)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.getMyScores(classId, studentId));
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

    // Review: 2026-11-22, user request — a student who never submitted still has an
    // exercise score of 0, so a lecturer who already entered the other components must
    // still see a total instead of a blank cell.
    private void stubComponentsIncludingExercise(UUID classId) {
        CourseClass cls = CourseClass.builder().ownerId(OWNER).status(ClassStatus.ACTIVE).build();
        Mockito.when(classRepo.findByIdAndOwnerId(classId, OWNER)).thenReturn(Optional.of(cls));
        Mockito.when(componentRepo.findAllByClassId(classId)).thenReturn(List.of(
                ScoreComponent.builder().id(ATTENDANCE_ID).type(ScoreComponentType.ATTENDANCE)
                        .weight(new BigDecimal("0.3")).build(),
                ScoreComponent.builder().id(EXERCISE_ID).type(ScoreComponentType.EXERCISE)
                        .weight(new BigDecimal("0.2")).build(),
                ScoreComponent.builder().id(FINAL_EXAM_ID).type(ScoreComponentType.FINAL_EXAM)
                        .weight(new BigDecimal("0.5")).build()));
        Mockito.when(scoreRepo.findAllByClassIdAndStudentCode(classId, CODE)).thenReturn(List.of(
                StudentScore.builder().componentId(ATTENDANCE_ID).score(new BigDecimal("8.00")).build(),
                StudentScore.builder().componentId(FINAL_EXAM_ID).score(new BigDecimal("7.00")).build()));
    }

    @Test
    void getStudentScores_unlinkedRosterRow_countsExerciseAsZero() {
        UUID classId = UUID.randomUUID();
        stubComponentsIncludingExercise(classId);
        // imported from CSV without the UUID column: never linked, so never submitted
        Mockito.when(studentRepo.findByClassIdAndStudentCode(classId, CODE))
                .thenReturn(Optional.of(ClassStudent.builder().studentCode(CODE).build()));

        StudentScoresResponse res = service.getStudentScores(classId, OWNER, CODE);

        assertEquals(new BigDecimal("5.90"), res.getTotal()); // 8*0.3 + 0*0.2 + 7*0.5
        assertEquals("F", res.getLetterGrade());              // the 0 exercise still fails the course
        assertEquals(BigDecimal.ZERO, res.getGpa());
        Mockito.verifyNoInteractions(resultServiceClient);
    }

    @Test
    void getStudentScores_resultServiceDown_leavesTotalNull() {
        UUID classId = UUID.randomUUID();
        stubComponentsIncludingExercise(classId);
        UUID studentUserId = UUID.randomUUID();
        Mockito.when(studentRepo.findByClassIdAndStudentCode(classId, CODE))
                .thenReturn(Optional.of(ClassStudent.builder().studentCode(CODE).studentUserId(studentUserId).build()));
        Mockito.when(assignmentRepo.findAllByClassIdAndPurposeAndPublished(classId, AssignmentPurpose.GRADED, true))
                .thenReturn(List.of(Assignment.builder().id(UUID.randomUUID()).build()));
        Mockito.when(resultServiceClient.weighted(Mockito.any())).thenThrow(new RuntimeException("result-service down"));

        StudentScoresResponse res = service.getStudentScores(classId, OWNER, CODE);

        // an outage must not fabricate a 0 exercise score: leave the total incomplete
        assertNull(res.getTotal());
        assertNull(res.getLetterGrade());
    }

    @Test
    void getStudentScores_noPublishedAssignment_leavesTotalNull() {
        UUID classId = UUID.randomUUID();
        stubComponentsIncludingExercise(classId);
        Mockito.when(studentRepo.findByClassIdAndStudentCode(classId, CODE))
                .thenReturn(Optional.of(ClassStudent.builder().studentCode(CODE)
                        .studentUserId(UUID.randomUUID()).build()));
        Mockito.when(assignmentRepo.findAllByClassIdAndPurposeAndPublished(classId, AssignmentPurpose.GRADED, true))
                .thenReturn(List.of());

        StudentScoresResponse res = service.getStudentScores(classId, OWNER, CODE);

        assertNull(res.getTotal());
        Mockito.verifyNoInteractions(resultServiceClient);
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

    // Review: 2026-11-22 — transcript must agree with the single-student read: result-service
    // reports a null average for a stepless FAILED FULL run, and a present-but-null map value
    // made Map.getOrDefault return null, leaving the transcript total blank while
    // GET .../students/{code}/scores showed 5.90. Both now normalise to 0.
    @Test
    void transcript_nullBatchValue_countsExerciseAsZeroLikeSingleRead() {
        UUID classId = UUID.randomUUID();
        stubComponentsIncludingExercise(classId);
        ClassStudent row = ClassStudent.builder()
                .studentCode(CODE).studentName("Nguyen Van An").studentUserId(STUDENT_USER_ID).build();
        Mockito.when(studentRepo.findByClassIdFiltered(Mockito.eq(classId), Mockito.any(), Mockito.any()))
                .thenReturn(new PageImpl<>(List.of(row)));
        Mockito.when(scoreRepo.findAllByClassId(classId)).thenReturn(List.of(
                StudentScore.builder().studentCode(CODE).componentId(ATTENDANCE_ID)
                        .score(new BigDecimal("8.00")).build(),
                StudentScore.builder().studentCode(CODE).componentId(FINAL_EXAM_ID)
                        .score(new BigDecimal("7.00")).build()));
        Mockito.when(assignmentRepo.findAllByClassIdAndPurposeAndPublished(classId, AssignmentPurpose.GRADED, true))
                .thenReturn(List.of(Assignment.builder().id(UUID.randomUUID()).build()));
        Map<UUID, BigDecimal> batch = new HashMap<>();
        batch.put(STUDENT_USER_ID, null); // failed stepless FULL run -> "missing"
        Mockito.when(resultServiceClient.weightedBatch(Mockito.any())).thenReturn(batch);

        Page<TranscriptEntryResponse> page = service.transcript(classId, OWNER, null, PageRequest.of(0, 20));

        TranscriptEntryResponse entry = page.getContent().get(0);
        assertEquals(new BigDecimal("5.90"), entry.getTotal()); // 8*0.3 + 0*0.2 + 7*0.5
        assertEquals("F", entry.getLetterGrade());
        assertEquals(BigDecimal.ZERO, entry.getGpa());
    }

    @Test
    void transcript_batchUnusable_leavesTotalNull() {
        UUID classId = UUID.randomUUID();
        stubComponentsIncludingExercise(classId);
        ClassStudent row = ClassStudent.builder()
                .studentCode(CODE).studentName("Nguyen Van An").studentUserId(STUDENT_USER_ID).build();
        Mockito.when(studentRepo.findByClassIdFiltered(Mockito.eq(classId), Mockito.any(), Mockito.any()))
                .thenReturn(new PageImpl<>(List.of(row)));
        Mockito.when(scoreRepo.findAllByClassId(classId)).thenReturn(List.of(
                StudentScore.builder().studentCode(CODE).componentId(ATTENDANCE_ID)
                        .score(new BigDecimal("8.00")).build()));
        Mockito.when(assignmentRepo.findAllByClassIdAndPurposeAndPublished(classId, AssignmentPurpose.GRADED, true))
                .thenReturn(List.of(Assignment.builder().id(UUID.randomUUID()).build()));
        Mockito.when(resultServiceClient.weightedBatch(Mockito.any()))
                .thenThrow(new RuntimeException("result-service down"));

        Page<TranscriptEntryResponse> page = service.transcript(classId, OWNER, null, PageRequest.of(0, 20));

        assertNull(page.getContent().get(0).getTotal());
        assertNull(page.getContent().get(0).getLetterGrade());
    }
}
