package vn.edu.ptit.web_grading_system.result_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.result_service.entity.Result;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ResultRepository extends JpaRepository<Result, UUID> {

    List<Result> findByAssignmentIdInAndStudentIdAndLatestTrue(Collection<UUID> assignmentIds, UUID studentId);

    /** Every latest row of one assignment — the lecturer's class-wide grading view. */
    List<Result> findByAssignmentIdAndLatestTrue(UUID assignmentId);

    List<Result> findBySubmissionId(UUID submissionId);

    /** Every attempt row of these assignments for one student (max-policy reads). */
    List<Result> findByAssignmentIdInAndStudentId(Collection<UUID> assignmentIds, UUID studentId);

    /** Every attempt row of one assignment (max-policy reads). */
    List<Result> findByAssignmentId(UUID assignmentId);
    List<Result> findByStudentIdAndAssignmentIdAndPlanIdAndLatestTrue(
            UUID studentId, UUID assignmentId, UUID planId);

    /** Every latest row of one student's assignment, all plan scopes. */
    List<Result> findByStudentIdAndAssignmentIdAndLatestTrue(
            UUID studentId, UUID assignmentId);
}