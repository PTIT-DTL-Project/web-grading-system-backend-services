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
 * <p>Lecturer CSV imports store {@code student_user_id = NULL} unless the UUID
 * column is hand-filled, which used to leave students invisible forever. On
 * each student read, this fills the caller's own UUID into still-unlinked rows
 * whose email matches — so previously imported rosters heal themselves on the
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
