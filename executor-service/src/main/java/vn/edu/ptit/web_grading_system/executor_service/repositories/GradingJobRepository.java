package vn.edu.ptit.web_grading_system.executor_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradingJobRepository extends JpaRepository<GradingJob, UUID> {

    Optional<GradingJob> findBySubmissionId(UUID submissionId);
}
