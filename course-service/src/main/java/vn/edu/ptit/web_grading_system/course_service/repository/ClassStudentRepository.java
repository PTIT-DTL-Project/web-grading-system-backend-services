package vn.edu.ptit.web_grading_system.course_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entity.ClassStudent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClassStudentRepository extends JpaRepository<ClassStudent, UUID> {

    Page<ClassStudent> findAllByClassId(UUID classId, Pageable pageable);

    List<ClassStudent> findAllByClassId(UUID classId);

    Optional<ClassStudent> findByClassIdAndStudentCode(UUID classId, String studentCode);

    boolean existsByClassIdAndStudentCode(UUID classId, String studentCode);

    List<ClassStudent> findAllByStudentUserId(UUID studentUserId);

    /**
     * Binds a Keycloak identity to roster rows by email. Only rows that have
     * never been linked are touched — an already-owned row keeps its owner.
     *
     * <p>Trust assumption (review before changing realm policy): the caller
     * must be the unique holder of the presented email. This holds because the
     * {@code ptit-wgs} realm has self-registration off
     * ({@code registrationAllowed=false}) and forbids duplicate emails
     * ({@code duplicateEmailsAllowed=false}) — whoever presents an email is
     * its sole owner, so first-come-first-served can only bind one's own rows.
     *
     * @return number of rows linked
     */
    // Review: 2026-10-08 — student identity linking (auto-bind from CSV email).
    // Review: 2026-10-09, Pullfrog — scope note: the no-steal guarantee covers
    // already-linked rows; unlinked rows are claimable by their email holder.
    @Modifying
    @Query("""
            update ClassStudent s
               set s.studentUserId = :studentId
             where s.studentUserId is null
               and lower(s.email) = lower(:email)
               and s.deletedAt is null""")
    int linkStudentIdentity(@Param("studentId") UUID studentId,
                            @Param("email") String email);
}