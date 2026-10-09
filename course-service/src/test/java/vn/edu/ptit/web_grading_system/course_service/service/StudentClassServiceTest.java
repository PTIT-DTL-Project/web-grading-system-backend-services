package vn.edu.ptit.web_grading_system.course_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentClassResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.entity.CourseClass;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.mapper.ClassMapper;
import vn.edu.ptit.web_grading_system.course_service.repository.ClassStudentRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.CourseClassRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Review: 2026-10-08 — student class list + detail (FE StudentClassesPage).
class StudentClassServiceTest {

    private final ClassStudentRepository studentRepo = Mockito.mock(ClassStudentRepository.class);
    private final CourseClassRepository classRepo = Mockito.mock(CourseClassRepository.class);
    private final ClassMapper mapper = Mockito.mock(ClassMapper.class);
    private final StudentIdentityService linker = Mockito.mock(StudentIdentityService.class);
    private final StudentClassService service = new StudentClassService(studentRepo, classRepo, mapper, linker);
    private final String email = "student@ptit.edu.vn";

    private static ClassStudent enrollment(UUID classId) {
        ClassStudent row = new ClassStudent();
        row.setClassId(classId);
        return row;
    }

    @Test
    void listEnrolledClasses_returnsEmptyWithoutQueryingClasses_whenNotEnrolled() {
        UUID studentId = UUID.randomUUID();
        Mockito.when(studentRepo.findAllByStudentUserId(studentId)).thenReturn(List.of());

        Page<StudentClassResponse> result = service.listEnrolledClasses(
                studentId, email, null, null, PageRequest.of(0, 20));

        assertTrue(result.isEmpty());
        Mockito.verify(classRepo, Mockito.never()).findEnrolled(
                Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void listEnrolledClasses_scopesToEnrollments() {
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        Mockito.when(studentRepo.findAllByStudentUserId(studentId))
                .thenReturn(List.of(enrollment(classId)));

        CourseClass klass = new CourseClass();
        StudentClassResponse response = StudentClassResponse.builder().id(classId).build();
        Mockito.when(classRepo.findEnrolled(
                        Mockito.eq(List.of(classId)), Mockito.any(), Mockito.isNull(), Mockito.any()))
                .thenReturn(new PageImpl<>(List.of(klass)));
        Mockito.when(mapper.toStudentResponse(klass)).thenReturn(response);

        Page<StudentClassResponse> result = service.listEnrolledClasses(
                studentId, email, "name:PTIT", null, PageRequest.of(0, 20));

        assertEquals(1, result.getTotalElements());
        assertEquals(response, result.getContent().get(0));
        Mockito.verify(linker).linkStudent(studentId, email);
    }

    @Test
    void getEnrolledClass_throws404_whenNotEnrolled() {
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        Mockito.when(studentRepo.findAllByStudentUserId(studentId)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getEnrolledClass(studentId, email, classId));
        Mockito.verify(classRepo, Mockito.never()).findById(Mockito.any());
    }

    @Test
    void getEnrolledClass_returnsClass_whenEnrolled() {
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        Mockito.when(studentRepo.findAllByStudentUserId(studentId))
                .thenReturn(List.of(enrollment(classId)));

        CourseClass klass = new CourseClass();
        StudentClassResponse response = StudentClassResponse.builder().id(classId).build();
        Mockito.when(classRepo.findById(classId)).thenReturn(Optional.of(klass));
        Mockito.when(mapper.toStudentResponse(klass)).thenReturn(response);

        assertEquals(response, service.getEnrolledClass(studentId, email, classId));
    }

    // Review: 2026-10-09 — student class roster tab (FE StudentRosterTab).
    @Test
    void listRoster_returnsClassmatesWithoutContactDetails() {
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        ClassStudent me = new ClassStudent();
        me.setClassId(classId);
        me.setStudentCode("S001");
        Mockito.when(studentRepo.findAllByStudentUserId(studentId)).thenReturn(List.of(me));
        Mockito.when(classRepo.findById(classId)).thenReturn(Optional.of(new CourseClass()));
        // getEnrolledClass maps through the mocked mapper: an unstubbed mock
        // returns null, and Optional.map(null) becomes empty -> 404.
        Mockito.when(mapper.toStudentResponse(Mockito.any()))
                .thenReturn(StudentClassResponse.builder().build());
        ClassStudent mate = new ClassStudent();
        mate.setClassId(classId);
        mate.setStudentCode("S002");
        mate.setStudentName("Hai");
        mate.setEmail("hai@ptit.edu.vn");
        Mockito.when(studentRepo.findAllByClassId(Mockito.eq(classId), Mockito.any()))
                .thenReturn(new PageImpl<>(List.of(me, mate)));

        var result = service.listRoster(studentId, email, classId, PageRequest.of(0, 20));

        assertEquals(2, result.getTotalElements());
        assertEquals("S002", result.getContent().get(1).getStudentCode());
        assertEquals("Hai", result.getContent().get(1).getStudentName());
    }

    @Test
    void listRoster_notEnrolled_is404() {
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        Mockito.when(studentRepo.findAllByStudentUserId(studentId)).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.listRoster(studentId, email, classId, PageRequest.of(0, 20)));
        Mockito.verify(studentRepo, Mockito.never()).findAllByClassId(Mockito.any(), Mockito.any());
    }

    @Test
    void getEnrolledClass_throws404_whenClassMissing() {
        UUID studentId = UUID.randomUUID();
        UUID classId = UUID.randomUUID();
        Mockito.when(studentRepo.findAllByStudentUserId(studentId))
                .thenReturn(List.of(enrollment(classId)));
        Mockito.when(classRepo.findById(classId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.getEnrolledClass(studentId, email, classId));
    }
}
