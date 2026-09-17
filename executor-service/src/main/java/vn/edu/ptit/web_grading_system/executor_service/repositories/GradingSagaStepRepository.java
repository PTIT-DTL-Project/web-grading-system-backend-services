package vn.edu.ptit.web_grading_system.executor_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingSagaStep;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStepStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface GradingSagaStepRepository extends JpaRepository<GradingSagaStep, UUID> {

    List<GradingSagaStep> findBySagaId(UUID sagaId);

    List<GradingSagaStep> findByStatus(SagaStepStatus status);

    @Modifying
    @Query("DELETE FROM GradingSagaStep s WHERE s.sagaId = :sagaId")
    void deleteBySagaId(UUID sagaId);
}
