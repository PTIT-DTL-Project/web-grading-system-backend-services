package vn.edu.ptit.web_grading_system.executor_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingSaga;
import vn.edu.ptit.web_grading_system.executor_service.entities.SagaStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradingSagaRepository extends JpaRepository<GradingSaga, UUID> {

    List<GradingSaga> findByJobId(UUID jobId);

    List<GradingSaga> findByStatus(SagaStatus status);

    Optional<GradingSaga> findFirstByJobId(UUID jobId);

    @Modifying
    @Query("UPDATE GradingSaga s SET s.status = :status, s.completedAt = null, s.currentStep = null WHERE s.jobId = :jobId")
    void resetByJobId(UUID jobId, SagaStatus status);
}
