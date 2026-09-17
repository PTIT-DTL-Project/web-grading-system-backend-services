package vn.edu.ptit.web_grading_system.executor_service.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJob;
import vn.edu.ptit.web_grading_system.executor_service.entities.GradingJobStatus;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface GradingJobRepository extends JpaRepository<GradingJob, UUID> {

    Optional<GradingJob> findBySubmissionId(UUID submissionId);

    List<GradingJob> findByStatusIn(Collection<GradingJobStatus> statuses);
}
