package vn.edu.ptit.web_grading_system.submission_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.submission_service.entity.Submission;
import vn.edu.ptit.web_grading_system.submission_service.entity.SubmissionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Page<Submission> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    List<Submission> findByAssignmentIdAndLatestTrueOrderByCreatedAtDesc(UUID assignmentId);

    List<Submission> findByAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    Optional<Submission> findByRustfsPath(String rustfsPath);

    // Returns a List: there is no unique constraint on submissions.latest — the real
    // migration is a plain, non-partial, non-unique index on (assignment_id, student_id)
    // (submission-service/V1__2026-08-16__init_schema.sql:18), so a concurrent
    // double-submit can leave 2+ rows with latest=true. A single-entity return type would
    // throw NonUniqueResultException (500) for that pair; a list lets requestUpload demote
    // every stale row and self-heal the data instead. Review: 2026-09-28, Pullfrog PR #24.
    @Query("SELECT s FROM Submission s WHERE s.assignmentId = :assignmentId AND s.studentId = :studentId AND s.latest = true")
    List<Submission> findAllLatestByAssignmentAndStudent(
            @Param("assignmentId") UUID assignmentId,
            @Param("studentId") UUID studentId);

    long countByAssignmentIdAndStatus(UUID assignmentId, SubmissionStatus status);
}
