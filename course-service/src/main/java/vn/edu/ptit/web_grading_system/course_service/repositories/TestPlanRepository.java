package vn.edu.ptit.web_grading_system.course_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entities.TestPlan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestPlanRepository extends JpaRepository<TestPlan, UUID> {

    Optional<TestPlan> findByIdAndAssignmentId(UUID id, UUID assignmentId);

    List<TestPlan> findAllByAssignmentIdOrderBySequenceOrderAsc(UUID assignmentId);

    boolean existsByAssignmentIdAndSequenceOrder(UUID assignmentId, Integer sequenceOrder);
}