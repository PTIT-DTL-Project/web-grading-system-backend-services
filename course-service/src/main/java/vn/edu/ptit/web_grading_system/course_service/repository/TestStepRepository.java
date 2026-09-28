package vn.edu.ptit.web_grading_system.course_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.course_service.entity.TestStep;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TestStepRepository extends JpaRepository<TestStep, UUID> {

    Optional<TestStep> findByIdAndPlanId(UUID id, UUID planId);

    List<TestStep> findAllByPlanIdOrderByStepOrderAsc(UUID planId);

    List<TestStep> findAllByPlanIdInOrderByStepOrderAsc(Collection<UUID> planIds);

    boolean existsByPlanIdAndStepOrder(UUID planId, Integer stepOrder);
}