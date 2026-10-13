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
    void results_studentWithNullUserIdReturnsEmptyWithoutCallingResultService() {
        ownerExistsOnMyClass();
        ClassStudent student = rostered("SV0001", "Nguyen Van A");
        student.setStudentUserId(null);
        when(classStudentRepository.findAllByClassId(CLASS_ID))
                .thenReturn(List.of(student));

        assertTrue(service().results(ASSIGNMENT_ID, OWNER, "SV0001", false).isEmpty());

        verifyNoInteractions(resultServiceClient);
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

    @Test
    void results_scopeAndEvidenceSurviveDeserialization() throws Exception {
        // Locks the result-service → course-service wire contract: scope plus
        // the A2 evidence fields must land on the mirror DTOs (unknown fields
        // would be silently dropped, starving the lecturer evidence view).
        // Review: 2026-10-12, lecturer detail parity.
        String json = """
                {"studentUserId":"%s","studentCode":"SV0001","studentName":"N",
                 "exerciseScore":8.50,
                 "results":[{"id":"%s","submissionId":"%s","assignmentId":"%s",
                   "studentId":"%s","planId":null,"planWeight":1,"scope":"FULL",
                   "score":8.50,"maxScore":10.00,"status":"DONE","summaryLog":"s",
                   "latest":true,"startedAt":null,"completedAt":null,
                   "steps":[{"id":"%s","planId":null,"stepId":"%s","stepOrder":1,
                     "stepName":"s","stepType":"HTTP_REQUEST","passed":false,
                     "skipped":false,"weight":1,"score":0.00,
                     "actualValue":"{}","expectedValue":null,
                     "errorMessage":"status: expected 200, got 404",
                     "durationMs":45,"assertionDetails":"[{\\"kind\\":\\"status\\"}]",
                     "extractedVars":null,"requestUrl":"http://x/y",
                     "requestBody":null,"responseStatus":404}]}]}"""
                .formatted(STUDENT, UUID.randomUUID(), UUID.randomUUID(), ASSIGNMENT_ID,
                        STUDENT, UUID.randomUUID(), UUID.randomUUID());
        tools.jackson.databind.ObjectMapper mapper = new tools.jackson.databind.ObjectMapper();
        StudentResultResponse group = mapper.readValue(json, StudentResultResponse.class);

        assertEquals("FULL", group.getResults().get(0).getScope());
        assertEquals("status: expected 200, got 404",
                group.getResults().get(0).getSteps().get(0).getErrorMessage());
        assertEquals("[{\"kind\":\"status\"}]",
                group.getResults().get(0).getSteps().get(0).getAssertionDetails());
        assertEquals("http://x/y",
                group.getResults().get(0).getSteps().get(0).getRequestUrl());
        assertEquals(404,
                group.getResults().get(0).getSteps().get(0).getResponseStatus());
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
    void submissions_enrichesRosterCodeAndNameAndKeepsDepartedRows() {
        ownerExistsOnMyClass();
        when(classStudentRepository.findAllByClassId(CLASS_ID))
                .thenReturn(List.of(rostered("SV0001", "Nguyen Van A")));
        UUID planId = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        SubmissionResponse row = SubmissionResponse.builder()
                .id(UUID.randomUUID())
                .assignmentId(ASSIGNMENT_ID)
                .studentId(STUDENT)
                .status("UPLOADED")
                .planId(planId)
                .build();
        SubmissionResponse departed = SubmissionResponse.builder()
                .id(UUID.randomUUID())
                .assignmentId(ASSIGNMENT_ID)
                .studentId(stranger)
                .status("UPLOADED")
                .build();
        when(submissionInternalClient.listByAssignment(ASSIGNMENT_ID))
                .thenReturn(List.of(row, departed));

        List<SubmissionResponse> out = service().submissions(ASSIGNMENT_ID, OWNER);

        assertEquals(2, out.size());
        assertEquals(row.getId(), out.get(0).getId());
        // Mirror contract: planId survives the pass-through (null = whole run).
        assertEquals(planId, out.get(0).getPlanId());
        // Roster enrichment: code/name for members, nulls (row kept) for departed.
        assertEquals("SV0001", out.get(0).getStudentCode());
        assertEquals("Nguyen Van A", out.get(0).getStudentName());
        assertNull(out.get(1).getStudentCode());
        assertNull(out.get(1).getStudentName());
    }
}
