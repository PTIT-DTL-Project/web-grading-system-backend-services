package vn.edu.ptit.web_grading_system.submission_service.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.submission_service.entities.Submission;
import vn.edu.ptit.web_grading_system.submission_service.entities.SubmissionStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

    Page<Submission> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable pageable);

    List<Submission> findByAssignmentIdAndLatestTrueOrderByCreatedAtDesc(UUID assignmentId);

    List<Submission> findByAssignmentIdOrderByCreatedAtDesc(UUID assignmentId);

    Optional<Submission> findByRustfsPath(String rustfsPath);

    @Query("SELECT s FROM Submission s WHERE s.assignmentId = :assignmentId AND s.studentId = :studentId AND s.latest = true")
    Submission findLatestByAssignmentAndStudent(
            @Param("assignmentId") UUID assignmentId,
            @Param("studentId") UUID studentId);

    long countByAssignmentIdAndStatus(UUID assignmentId, SubmissionStatus status);
}
