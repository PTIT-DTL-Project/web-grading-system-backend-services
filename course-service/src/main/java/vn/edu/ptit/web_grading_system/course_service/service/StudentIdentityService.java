package vn.edu.ptit.web_grading_system.course_service.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.course_service.repository.ClassStudentRepository;

import java.util.UUID;

/**
 * Lazily binds a Keycloak identity to roster rows by email.
 *
 * <p>Lecturer CSV imports now deliberately store {@code student_user_id = NULL}
 * (the CSV {@code studentUserId} column is ignored, see
 * {@link vn.edu.ptit.web_grading_system.course_service.service.ClassService#importStudents}),
 * so no stale UUID can permanently mismatch a roster row against the student's
 * JWT subject. On each student read, this fills the caller's own UUID into rows
 * whose email matches — including rows that previously held a wrong UUID, which
 * are overwritten — so previously imported rosters heal themselves on the
 * student's next API call. The email comes from the gateway-stamped
 * {@code X-User-Email} header (validated JWT), never from the client.
 */
@Service
@RequiredArgsConstructor
public class StudentIdentityService {

    private final ClassStudentRepository classStudentRepository;

    // Review: 2026-10-08 — student identity linking. Student reads run
    // @Transactional(readOnly = true), so the link needs its own read-write
    // transaction; the statement is idempotent, safe to repeat on every read.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int linkStudent(UUID studentId, String email) {
        if (email == null || email.isBlank()) {
            return 0;
        }
        return classStudentRepository.linkStudentIdentity(studentId, email.trim());
    }
}
