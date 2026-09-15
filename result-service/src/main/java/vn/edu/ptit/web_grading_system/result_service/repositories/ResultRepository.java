package vn.edu.ptit.web_grading_system.result_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.result_service.entities.Result;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ResultRepository extends JpaRepository<Result, UUID> {

    List<Result> findByAssignmentIdInAndStudentIdAndLatestTrue(Collection<UUID> assignmentIds, UUID studentId);

    List<Result> findBySubmissionId(UUID submissionId);
    List<Result> findByStudentIdAndAssignmentIdAndPlanIdAndLatestTrue(
            UUID studentId, UUID assignmentId, UUID planId);

    List<Result> findByStudentIdAndAssignmentIdAndPlanIdIsNullAndLatestTrue(
            UUID studentId, UUID assignmentId);
}