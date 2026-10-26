package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
        if (classId == null) {
            return Map.of();
        }
        return classStudentRepository.findAllByClassId(classId).stream()
                // A repeated student_user_id would make Collectors.toMap throw; first wins,
                // matching ClassStudentRepository.findByClassIdAndStudentCode's Optional.
                .collect(Collectors.toMap(ClassStudent::getStudentUserId,
                        student -> student, (first, second) -> first));
    }

    /**
     * Looks up the identifying fields (code/name) for the given student_user_id values, used when
     * the assignment belongs to no class (practice) so there is no roster to enrich from.
     */
    private Map<UUID, ClassStudent> identityByUserId(Collection<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        return classStudentRepository.findAllByStudentUserIdIn(userIds).stream()
                .filter(s -> s.getStudentUserId() != null)
                .collect(Collectors.toMap(ClassStudent::getStudentUserId,
                        student -> student, (first, second) -> first));
    }

    /**
     * Resolves a roster code to the owning user id. Shared by the results and
     * submissions handlers so the code→uuid translation never diverges.
     *
     * @return empty when the code is blank, unknown for this class, or mapped to
     *         a student with no user id yet (e.g. imported-but-unlinked) — both
     *         are "no matching student", indistinguishable from "no rows".
     */
    private Optional<UUID> studentIdFor(Map<UUID, ClassStudent> roster, String studentCode) {
        if (studentCode == null || studentCode.isBlank()) {
            return Optional.empty();
        }
        return roster.entrySet().stream()
                .filter(e -> studentCode.trim().equals(e.getValue().getStudentCode()))
                .map(Map.Entry::getValue)
                .map(ClassStudent::getStudentUserId)
                .filter(Objects::nonNull)
                .findFirst();
    }

    /**
     * Auto-grading results for one assignment, whole class or a single student.
     *
     * <p>When {@code studentCode} is null/blank the whole class is returned — including
     * students who have not yet run anything: each such student appears with
     * {@code exerciseScore = 0} and an empty {@code results} list (temporary 0, not
     * a graded zero), so the class view never looks complete while submissions are
     * still outstanding (review: 2026-10-18, lecturer "all students" view).
     *
     * @param studentCode roster code (e.g. {@code SV0001}); null/blank returns the whole class.
     *                    An unknown code for this class returns an empty list rather than an
     *                    error, so it stays indistinguishable from "no results yet".
     * @param includeSteps false keeps the read to one small payload; the FE passes true only
     *                     when drilling into one student's step detail.
     */
    public Page<StudentResultResponse> results(
            UUID assignmentId, UUID ownerId, String studentCode, boolean includeSteps,
            Integer page, Integer size) {
        Assignment assignment = requireOwnedAssignment(assignmentId, ownerId);
        UUID classId = assignment.getClassId();
        Map<UUID, ClassStudent> roster = rosterByUserId(classId);

        // Single-student path: translate the roster code to a user id up front.
        Optional<UUID> wantedId = studentIdFor(roster, studentCode);
        if (studentCode != null && !studentCode.isBlank() && wantedId.isEmpty()) {
            return Page.empty();
        }
        UUID studentUserId = wantedId.orElse(null);

        // Deliberately NOT swallowed by try/catch (the "degrade gracefully" rule in skill §10
        // does not fit here): an empty list would read as "nobody has submitted yet" and could
        // send a lecturer to the wrong grading decision. A failed dependency must be a 500.
        List<StudentResultResponse> groups =
                resultServiceClient.assignmentResults(assignmentId, studentUserId, includeSteps);

        // Practice assignments have classId == NULL, so no class roster exists: enrich student
        // code/name from class_students by student_user_id (students who have taken at least one
        // class).
        Map<UUID, ClassStudent> identity = classId == null
                ? identityByUserId(groups.stream()
                        .map(StudentResultResponse::getStudentUserId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                : roster;

        // Only the whole-class view synthesises the still-missing students — a single-student
        // view must never invent rows for people who have no results at all.
        // Practice assignments have no roster, so nothing to synthesise.
        boolean synthesizeMissing = studentUserId == null && classId != null;
        Map<UUID, StudentResultResponse> byUser = new LinkedHashMap<>();
        for (StudentResultResponse group : groups) {
            ClassStudent student = identity.get(group.getStudentUserId());
            if (student != null) {
                group.setStudentCode(student.getStudentCode());
                group.setStudentName(student.getStudentName());
            }
            // A result whose student left the roster keeps its raw studentUserId with null
            // code/name — hiding the row would make the class view look complete when it isn't.
            byUser.put(group.getStudentUserId(), group);
        }
        if (synthesizeMissing) {
            BigDecimal ZERO = BigDecimal.ZERO;
            for (Map.Entry<UUID, ClassStudent> entry : roster.entrySet()) {
                UUID userId = entry.getKey();
                if (userId == null) {
                    // Imported-but-unlinked roster row: no gradable identity yet
                    // (no user id to key or drill into), so skip it rather than emit a
                    // /results row with studentUserId=null — the lecture view keys rows
                    // on studentUserId and nulls would be a click-through trap.
                    // Review: 2026-10-18, Pullfrog (null-keyed synthesis row).
                    continue;
                }
                if (byUser.containsKey(userId)) {
                    continue;
                }
                ClassStudent student = entry.getValue();
                byUser.put(userId, StudentResultResponse.builder()
                        .studentUserId(userId)
                        .studentCode(student.getStudentCode())
                        .studentName(student.getStudentName())
                        .exerciseScore(ZERO)
                        .results(List.of())
                        .build());
            }
        }
        // Preserve roster order (by studentCode) for a stable column on re-renders; the
        // freshly-synthesised rows land in the same natural order as the real ones.
        List<StudentResultResponse> ordered = byUser.values().stream()
                .sorted(Comparator.comparing(
                        StudentResultResponse::getStudentCode,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        // Server-side page the synthesized list. page/size null => whole list (backward
        // compatible: FE sends neither and gets the full class as one PageImpl so its
        // ListPage still pagates client-side on meta.total).
        // Review: 2026-10-19, pagination-via-course-service.
        if (page == null || size == null) {
            return new PageImpl<>(ordered);
        }
        int from = Math.max(0, Math.min(page * size, ordered.size()));
        int to = Math.min(from + size, ordered.size());
        return new PageImpl<>(ordered.subList(from, to), PageRequest.of(page, size), ordered.size());
    }

    /**
     * Every submission of one assignment, optionally narrowed to one student via
     * {@code studentCode}. Null/blank returns everyone (the whole-class list).
     * An unknown code for this class returns an empty list — there are no
     * submissions to show, indistinguishable from "none submitted yet".
     */
    public Page<SubmissionResponse> submissions(UUID assignmentId, UUID ownerId, String studentCode,
            Integer page, Integer size) {
        Assignment assignment = requireOwnedAssignment(assignmentId, ownerId);
        UUID classId = assignment.getClassId();
        Map<UUID, ClassStudent> roster = rosterByUserId(classId);

        Optional<UUID> wantedId = studentIdFor(roster, studentCode);
        if (studentCode != null && !studentCode.isBlank() && wantedId.isEmpty()) {
            return Page.empty();
        }
        UUID studentUserId = wantedId.orElse(null);
        // Roster read first, Feign second: same no-connection-across-HTTP rule as above.
        // Forward page/size to submission-service for true server-side paging.
        Page<SubmissionResponse> fetched = submissionInternalClient.listByAssignment(
                assignmentId, page, size);
        List<SubmissionResponse> rows = fetched.getContent();
        // Practice assignments have classId == NULL, so no class roster exists: enrich code/name from
        // class_students by student_user_id of the submissions.
        Map<UUID, ClassStudent> identity = classId == null
                ? identityByUserId(rows.stream()
                        .map(SubmissionResponse::getStudentId)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet()))
                : roster;
        for (SubmissionResponse row : rows) {
            ClassStudent student = row.getStudentId() == null
                    ? null : identity.get(row.getStudentId());
            if (student != null) {
                row.setStudentCode(student.getStudentCode());
                row.setStudentName(student.getStudentName());
            }
        }
        // Narrow to the requested student (null => whole class, keep all rows).
        List<SubmissionResponse> narrowed;
        if (studentUserId != null) {
            narrowed = rows.stream()
                    .filter(row -> studentUserId.equals(row.getStudentId()))
                    .toList();
        } else {
            narrowed = rows;
        }
        // Page the narrowed set. When no page/size were supplied (null), the
        // upstream PageImpl already carries the full list — pass it through.
        // When filtering reduced the content (studentCode narrowing happens client-side
        // here, post-fetch), re-slice to reflect the actual visible total.
        if (page == null || size == null) {
            return new PageImpl<>(narrowed);
        }
        int from = Math.max(0, Math.min(page * size, narrowed.size()));
        int to = Math.min(from + size, narrowed.size());
        return new PageImpl<>(narrowed.subList(from, to), PageRequest.of(page, size), narrowed.size());
    }
}
