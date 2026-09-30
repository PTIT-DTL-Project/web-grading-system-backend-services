package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import vn.edu.ptit.web_grading_system.course_service.client.ResultServiceClient;
import vn.edu.ptit.web_grading_system.course_service.client.SubmissionInternalClient;
import vn.edu.ptit.web_grading_system.course_service.dto.response.StudentResultResponse;
import vn.edu.ptit.web_grading_system.course_service.dto.response.SubmissionResponse;
import vn.edu.ptit.web_grading_system.course_service.entity.Assignment;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStudent;
import vn.edu.ptit.web_grading_system.course_service.exception.ResourceNotFoundException;
import vn.edu.ptit.web_grading_system.course_service.repository.AssignmentRepository;
import vn.edu.ptit.web_grading_system.course_service.repository.ClassStudentRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The lecturer's grading view over two data services.
 *
 * <p>course-service is the single authority on "may this lecturer see this assignment" —
 * result-service and submission-service carry no role or ownership logic at all (plan
 * role-split-result-apis-v1.0, D1/D3). Every method here therefore does its owner check
 * <em>before</em> any Feign call, so an unowned id never reaches a downstream service.
 *
 * <p>No {@code @Transactional}: each repository call is its own short read, and wrapping the
 * Feign calls would hold a DB connection open across two HTTP round trips.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssignmentGradingService {

    private final AssignmentRepository assignmentRepository;
    private final ClassStudentRepository classStudentRepository;
    private final ResultServiceClient resultServiceClient;
    private final SubmissionInternalClient submissionInternalClient;

    /**
     * An assignment outside the caller's own classes is a 404, never a 403 — an existing id
     * must stay indistinguishable from a missing one (project ownership convention).
     */
    private Assignment requireOwnedAssignment(UUID assignmentId, UUID ownerId) {
        return assignmentRepository.findByIdAndOwnerId(assignmentId, ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));
    }

    private Map<UUID, ClassStudent> rosterByUserId(UUID classId) {
        return classStudentRepository.findAllByClassId(classId).stream()
                // A repeated student_user_id would make Collectors.toMap throw; first wins,
                // matching ClassStudentRepository.findByClassIdAndStudentCode's Optional.
                .collect(Collectors.toMap(ClassStudent::getStudentUserId,
                        student -> student, (first, second) -> first));
    }

    /**
     * Auto-grading results for one assignment, whole class or a single student.
     *
     * @param studentCode roster code (e.g. {@code SV0001}); null/blank returns the whole class.
     *                    An unknown code for this class returns an empty list rather than an
     *                    error, so it stays indistinguishable from "no results yet".
     * @param includeSteps false keeps the read to one small payload; the FE passes true only
     *                     when drilling into one student's step detail.
     */
    public List<StudentResultResponse> results(
            UUID assignmentId, UUID ownerId, String studentCode, boolean includeSteps) {
        Assignment assignment = requireOwnedAssignment(assignmentId, ownerId);
        Map<UUID, ClassStudent> roster = rosterByUserId(assignment.getClassId());

        // This is the only layer that can translate a roster code into a user id, so the
        // translation happens here and the downstream call already carries the narrowed set.
        UUID studentUserId = null;
        if (studentCode != null && !studentCode.isBlank()) {
            String wanted = studentCode.trim();
            ClassStudent match = roster.entrySet().stream()
                    .filter(entry -> wanted.equals(entry.getValue().getStudentCode()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            if (match == null) {
                return List.of();
            }
            studentUserId = match.getStudentUserId();
        }

        // Deliberately NOT swallowed by try/catch (the "degrade gracefully" rule in skill §10
        // does not fit here): an empty list would read as "nobody has submitted yet" and could
        // send a lecturer to the wrong grading decision. A failed dependency must be a 500.
        List<StudentResultResponse> groups =
                resultServiceClient.assignmentResults(assignmentId, studentUserId, includeSteps);

        for (StudentResultResponse group : groups) {
            ClassStudent student = roster.get(group.getStudentUserId());
            if (student != null) {
                group.setStudentCode(student.getStudentCode());
                group.setStudentName(student.getStudentName());
            }
            // A result whose student left the roster keeps its raw studentUserId with null
            // code/name — hiding the row would make the class view look complete when it isn't.
        }
        return groups;
    }

    /** Submissions of one assignment — the list that used to sit behind a bare role check. */
    public List<SubmissionResponse> submissions(UUID assignmentId, UUID ownerId) {
        requireOwnedAssignment(assignmentId, ownerId);
        return submissionInternalClient.listByAssignment(assignmentId);
    }
}
