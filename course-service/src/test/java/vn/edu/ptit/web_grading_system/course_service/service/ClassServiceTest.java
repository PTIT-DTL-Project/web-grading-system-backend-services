package vn.edu.ptit.web_grading_system.course_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import vn.edu.ptit.web_grading_system.course_service.dto.request.CreateClassRequest;
import vn.edu.ptit.web_grading_system.course_service.exception.BadRequestException;
import vn.edu.ptit.web_grading_system.course_service.mapper.ClassMapper;
import vn.edu.ptit.web_grading_system.course_service.mapper.ClassStudentMapper;
import vn.edu.ptit.web_grading_system.course_service.repositories.ClassStudentRepository;
import vn.edu.ptit.web_grading_system.course_service.repositories.CourseClassRepository;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClassServiceTest {

    private final CourseClassRepository classRepo = Mockito.mock(CourseClassRepository.class);
    private final ClassStudentRepository studentRepo = Mockito.mock(ClassStudentRepository.class);
    private final ClassService service = new ClassService(
            classRepo, studentRepo, Mockito.mock(ClassMapper.class), Mockito.mock(ClassStudentMapper.class));

    @Test
    void create_throwsDescriptiveError_whenDuplicateNameInSemester() {
        UUID ownerId = UUID.randomUUID();
        Mockito.when(classRepo.existsByOwnerIdAndNameAndSemester(ownerId, "PTIT CNTT-K68", "20261"))
                .thenReturn(true);

        BadRequestException e = assertThrows(BadRequestException.class,
                () -> service.create(ownerId, new CreateClassRequest(" PTIT CNTT-K68 ", " 20261 ")));
        assertEquals("Class 'PTIT CNTT-K68' already exists in semester 20261", e.getMessage());
        Mockito.verify(classRepo, Mockito.never()).save(Mockito.any());
    }

    @Test
    void parseCsv_skipsHeaderAndBlankLines() {
        MockMultipartFile file = new MockMultipartFile("file", "students.csv",
                "text/csv", "studentCode,studentName,email\nB22DCCN001,Nguyen Van A,a@ptit.edu.vn\n\nB22DCCN002,Tran Thi B,\n"
                        .getBytes(StandardCharsets.UTF_8));
        List<String[]> rows = ClassService.parseCsv(file);
        assertEquals(2, rows.size());
        assertEquals("B22DCCN001", rows.get(0)[0]);
        assertEquals("Nguyen Van A", rows.get(0)[1]);
        assertEquals("a@ptit.edu.vn", rows.get(0)[2]);
        assertEquals("B22DCCN002", rows.get(1)[0]);
        assertEquals(3, rows.get(1).length);
    }

    @Test
    void parseCsv_emptyFile_returnsNoRows() {
        MockMultipartFile file = new MockMultipartFile("file", "students.csv",
                "text/csv", new byte[0]);
        assertEquals(0, ClassService.parseCsv(file).size());
    }

    @Test
    void parseCsv_junkSingleColumnRows_doNotCrash() {
        MockMultipartFile file = new MockMultipartFile("file", "students.csv",
                "text/csv", "\u0000\u0001binary-ish,junk\nonly-one-column\n".getBytes(StandardCharsets.UTF_8));
        List<String[]> rows = ClassService.parseCsv(file);
        assertEquals(2, rows.size());
    }
}