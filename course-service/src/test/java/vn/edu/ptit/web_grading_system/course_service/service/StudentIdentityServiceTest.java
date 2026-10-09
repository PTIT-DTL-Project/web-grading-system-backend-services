package vn.edu.ptit.web_grading_system.course_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.course_service.repository.ClassStudentRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Review: 2026-10-08 — student identity linking (auto-bind from CSV email).
class StudentIdentityServiceTest {

    private final ClassStudentRepository studentRepo = Mockito.mock(ClassStudentRepository.class);
    private final StudentIdentityService service = new StudentIdentityService(studentRepo);
    private final UUID studentId = UUID.randomUUID();

    @Test
    void linkStudent_skipsRepository_whenEmailBlank() {
        assertEquals(0, service.linkStudent(studentId, null));
        assertEquals(0, service.linkStudent(studentId, "   "));
        Mockito.verify(studentRepo, Mockito.never())
                .linkStudentIdentity(Mockito.any(), Mockito.any());
    }

    @Test
    void linkStudent_trimsEmail() {
        Mockito.when(studentRepo.linkStudentIdentity(studentId, "Student@PTIT.edu.vn"))
                .thenReturn(2);

        assertEquals(2, service.linkStudent(studentId, "  Student@PTIT.edu.vn  "));
    }
}
