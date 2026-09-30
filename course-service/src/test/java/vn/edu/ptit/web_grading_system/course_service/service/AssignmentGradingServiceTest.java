package vn.edu.ptit.web_grading_system.course_service.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.edu.ptit.web_grading_system.course_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.course_service.client.SubmissionInternalClient;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentResultResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.repository.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.ClassStudentRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AssignmentGradingService} ownership, roster mapping and downstream-call behaviour
 * (plan role-split-result-apis-v1.0).
 *
 * <p>The first test of each pair is the important one: the owner check must fail <em>before</em>
 * any Feign interaction, so an assignment this lecturer does not own is never forwarded to
 * result-service or submission-service. Without that ordering the endpoint would leak
 * existence of foreign ids even though it answers 404.
 */
class AssignmentGradingServiceTest {

    private static final UUID OWNER = UUID.fromString("2d93941a-4221-458b-a03d-43bd6315d02e");
    private static final UUID CLASS_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000001");
    private static final UUID ASSIGNMENT_ID = UUID.fromString("6f3d6d6e-0000-4000-8000-000000000007");
    private static final UUID STUDENT = UUID.fromString("3e0b0d0b-0000-4000-8000-000000000006");

    private final AssignmentRepository assignmentRepository = Mockito.mock(AssignmentRepository.class);
    private final ClassStudentRepository classStudentRepository = Mockito.mock(ClassStudentRepository.class);
    private final ResultServiceClient resultServiceClient = Mockito.mock(ResultServiceClient.class);
    private final SubmissionInternalClient submissionInternalClient =
            Mockito.mock(SubmissionInternalClient.class);

    private AssignmentGradingService service() {
        return new AssignmentGradingService(
                assignmentRepository, classStudentRepository,
                resultServiceClient, submissionInternalClient);
    }

    private static Assignment ownedAssignment() {
        Assignment assignment = new Assignment();
        assignment.setOwnerId(OWNER);
        assignment.setClassId(CLASS_ID);
        return assignment;
    }

    private static ClassStudent rostered(String studentCode, String studentName) {
        ClassStudent student = new ClassStudent();
        student.setClassId(CLASS_ID);
        student.setStudentCode(studentCode);
        student.setStudentName(studentName);
        student.setStudentUserId(STUDENT);
        return student;
    }

    private static StudentResultResponse group() {
        return StudentResultResponse.builder()
                .studentUserId(STUDENT)
                .exerciseScore(new BigDecimal("8.50"))
                .results(List.of())
                .build();
    }

    private void ownerExistsOnMyClass() {
        when(assignmentRepository.findByIdAndOwnerId(ASSIGNMENT_ID, OWNER))
                .thenReturn(Optional.of(ownedAssignment()));
    }

    // --- results ---------------------------------------------------------------

    @Test
    void results_refusesAnUnownedAssignmentBeforeAnyFeignCall() {
        when(assignmentRepository.findByIdAndOwnerId(ASSIGNMENT_ID, OWNER))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service().results(ASSIGNMENT_ID, OWNER, null, false));

        // Forwarding would turn this endpoint into an oracle for foreign assignment ids.
        verifyNoInteractions(resultServiceClient, submissionInternalClient);
    }

    @Test
    void results_addsTheRosterCodeAndNameTheDataServiceCannotKnow() {
        ownerExistsOnMyClass();
        when(classStudentRepository.findAllByClassId(CLASS_ID))
                .thenReturn(List.of(rostered("SV0001", "Nguyen Van A")));
        when(resultServiceClient.assignmentResults(ASSIGNMENT_ID, null, false))
                .thenReturn(List.of(group()));

        List<StudentResultResponse> out = service().results(ASSIGNMENT_ID, OWNER, null, false);

        assertEquals(1, out.size());
        assertEquals("SV0001", out.get(0).getStudentCode());
        assertEquals("Nguyen Van A", out.get(0).getStudentName());
        assertEquals(STUDENT, out.get(0).getStudentUserId());
    }

    @Test
    void results_studentCodeUnknownToThisClassReturnsEmptyWithoutCallingResultService() {
        ownerExistsOnMyClass();
        when(classStudentRepository.findAllByClassId(CLASS_ID))
                .thenReturn(List.of(rostered("SV0001", "Nguyen Van A")));

        assertTrue(service().results(ASSIGNMENT_ID, OWNER, "NOPE", false).isEmpty());

        verifyNoInteractions(resultServiceClient);
    }

    @Test
    void results_aKnownStudentCodeNarrowsTheDownstreamQueryAndIsTrimmed() {
        ownerExistsOnMyClass();
        when(classStudentRepository.findAllByClassId(CLASS_ID))
                .thenReturn(List.of(rostered("SV0001", "Nguyen Van A")));
        when(resultServiceClient.assignmentResults(ASSIGNMENT_ID, STUDENT, false))
                .thenReturn(List.of(group()));

        List<StudentResultResponse> out = service().results(ASSIGNMENT_ID, OWNER, " SV0001 ", false);

        assertEquals(1, out.size());
        // The roster code is translated here — only course-service can do it — so the data
        // service never has to know what a student code is.
        verify(resultServiceClient).assignmentResults(ASSIGNMENT_ID, STUDENT, false);
    }

    @Test
    void results_forwardsIncludeStepsSoTheClassWideReadStaysCheap() {
        ownerExistsOnMyClass();
        when(classStudentRepository.findAllByClassId(CLASS_ID)).thenReturn(List.of());
        when(resultServiceClient.assignmentResults(ASSIGNMENT_ID, null, true))
                .thenReturn(List.of());

        service().results(ASSIGNMENT_ID, OWNER, null, true);

        verify(resultServiceClient).assignmentResults(ASSIGNMENT_ID, null, true);
    }

    @Test
    void results_aStudentGoneFromTheRosterKeepsTheirRowWithNullCode() {
        ownerExistsOnMyClass();
        when(classStudentRepository.findAllByClassId(CLASS_ID)).thenReturn(List.of());
        when(resultServiceClient.assignmentResults(ASSIGNMENT_ID, null, false))
                .thenReturn(List.of(group()));

        List<StudentResultResponse> out = service().results(ASSIGNMENT_ID, OWNER, null, false);

        // Dropping the row would make the class view look complete when it is not.
        assertEquals(1, out.size());
        assertEquals(STUDENT, out.get(0).getStudentUserId());
        assertNull(out.get(0).getStudentCode());
        assertNull(out.get(0).getStudentName());
    }

    // --- submissions -----------------------------------------------------------

    @Test
    void submissions_refusesAnUnownedAssignmentBeforeAnyFeignCall() {
        when(assignmentRepository.findByIdAndOwnerId(ASSIGNMENT_ID, OWNER))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service().submissions(ASSIGNMENT_ID, OWNER));

        verifyNoInteractions(resultServiceClient, submissionInternalClient);
    }

    @Test
    void submissions_forwardsTheRowsUnchangedOnceTheOwnerCheckPasses() {
        ownerExistsOnMyClass();
        SubmissionResponse row = SubmissionResponse.builder()
                .id(UUID.randomUUID())
                .assignmentId(ASSIGNMENT_ID)
                .studentId(STUDENT)
                .status("UPLOADED")
                .build();
        when(submissionInternalClient.listByAssignment(ASSIGNMENT_ID)).thenReturn(List.of(row));

        List<SubmissionResponse> out = service().submissions(ASSIGNMENT_ID, OWNER);

        assertEquals(1, out.size());
        assertEquals(row.getId(), out.get(0).getId());
    }
}
