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
    List<Result> findByStudentIdAndAssignmentIdAndPlanIdAndLatestTrue(
            UUID studentId, UUID assignmentId, UUID planId);

    List<Result> findByStudentIdAndAssignmentIdAndPlanIdIsNullAndLatestTrue(
            UUID studentId, UUID assignmentId);
}