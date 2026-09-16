package vn.edu.ptit.web_grading_system.executor_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingStepResult;

import java.util.List;
import java.util.UUID;

@Repository
public interface GradingStepResultRepository extends JpaRepository<GradingStepResult, UUID> {

    List<GradingStepResult> findByJobId(UUID jobId);

    @Modifying
    @Query("DELETE FROM GradingStepResult r WHERE r.jobId = :jobId")
    void deleteByJobId(UUID jobId);
}
